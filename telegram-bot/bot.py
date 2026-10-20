"""Private Ronin Telegram utility bot. No AI services. Python 3.10+, standard library only."""
import getpass
import json
import os
from pathlib import Path
import sqlite3
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent
CONFIG = ROOT / '.env'
KEYBOARD = {'keyboard': [['📝 Заметки', '✅ Дела'], ['➕ Заметка', '➕ Дело'], ['❓ Помощь']], 'resize_keyboard': True}
HELP = ('🥷 Я Ронин. Работаю без GPT и платных ИИ-сервисов.\n'
        '📝 Заметки — посмотреть сохранённое.\n'
        '✅ Дела — список задач.\n'
        '/note текст — сохранить заметку\n'
        '/todo текст — добавить дело\n'
        '/done номер — отметить дело выполненным\n'
        '/delete номер — удалить заметку\n'
        '/cancel — отменить ввод\n'
        'Свободный ИИ-диалог и управление телефоном пока не подключены.')


class ApiError(Exception):
    pass


def post(url, data, timeout=120):
    headers = {'Content-Type': 'application/json'}
    req = urllib.request.Request(url, json.dumps(data).encode(), headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        # Never log URLs: Telegram embeds the secret in its URL.
        raise ApiError('HTTP ' + str(error.code)) from None
    except (urllib.error.URLError, TimeoutError, OSError, ValueError):
        raise ApiError('Сеть или неверный ответ сервиса') from None


def load_config():
    values = {}
    if CONFIG.exists():
        for line in CONFIG.read_text().splitlines():
            if line.strip() and not line.lstrip().startswith('#'):
                k, v = line.split('=', 1)
                values[k.strip()] = v.strip()
    for key in ('TELEGRAM_BOT_TOKEN', 'OWNER_ID'):
        values[key] = os.environ.get(key, values.get(key, ''))
    values = {k: values[k] for k in ('TELEGRAM_BOT_TOKEN', 'OWNER_ID')}
    if not all(values.values()) or not values['OWNER_ID'].isdigit() or int(values['OWNER_ID']) <= 0:
        raise ValueError('Сначала запустите: python3 bot.py --setup')
    return values


def setup():
    if CONFIG.exists():
        raise ValueError('.env уже существует. Отредактируйте его локально для смены настроек.')
    token = getpass.getpass('Токен Telegram от BotFather (ввод скрыт): ').strip()
    owner = input('Ваш числовой Telegram user ID: ').strip()
    if not token or not owner.isdigit() or int(owner) <= 0:
        raise ValueError('Все поля обязательны; ID должен быть положительным числом.')
    with os.fdopen(os.open(CONFIG, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w') as file:
        file.write(f'TELEGRAM_BOT_TOKEN={token}\nOWNER_ID={owner}\n')
    print('Настройки сохранены. Запуск: python3 bot.py')


class Bot:
    def __init__(self, config, db_path, request=post):
        self.config, self.request = config, request
        self.db = sqlite3.connect(db_path)
        self.db.execute('PRAGMA secure_delete=ON')
        self.db.execute('CREATE TABLE IF NOT EXISTS state (key TEXT PRIMARY KEY, value TEXT)')
        self.db.execute('CREATE TABLE IF NOT EXISTS items (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, text TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0)')
        self.db.commit()

    def get(self, key, default=''):
        row = self.db.execute('SELECT value FROM state WHERE key=?', (key,)).fetchone()
        return row[0] if row else default

    def set(self, key, value):
        with self.db:
            self.db.execute('INSERT OR REPLACE INTO state VALUES (?, ?)', (key, value))

    def tg(self, method, **data):
        result = self.request('https://api.telegram.org/bot' + self.config['TELEGRAM_BOT_TOKEN'] + '/' + method, data)
        if not result.get('ok'):
            raise ApiError('Telegram отклонил запрос')
        return result['result']

    def send(self, text):
        # 2000 Unicode code points also fit Telegram's 4096 UTF-16-unit limit.
        for start in range(0, len(text), 2000):
            self.tg('sendMessage', chat_id=int(self.config['OWNER_ID']),
                    text=text[start:start + 2000], reply_markup=KEYBOARD)

    def handle(self, update):
        msg = update.get('message', {})
        owner = int(self.config['OWNER_ID'])
        if msg.get('from', {}).get('id') != owner or msg.get('chat', {}).get('id') != owner or msg.get('chat', {}).get('type') != 'private':
            return
        text = msg.get('text', '').strip()
        if text in ('/start', '/help', '❓ Помощь'):
            self.set('pending', '')
            self.send(HELP)
        elif text in ('📝 Заметки', '/notes', '✅ Дела', '/todos'):
            self.set('pending', '')
            kind = 'note' if text in ('📝 Заметки', '/notes') else 'todo'
            rows = self.db.execute('SELECT id, text, done FROM items WHERE kind=? ORDER BY id', (kind,)).fetchall()
            self.send('\n'.join(f'{i}. ' + ('☑ ' if done else '') + value for i, value, done in rows) if rows else 'Пока список пуст.')
        elif text in ('➕ Заметка', '➕ Дело'):
            self.set('pending', 'note' if text == '➕ Заметка' else 'todo')
            self.send('Пришли текст одним сообщением. Отмена: /cancel')
        elif text == '/cancel':
            self.set('pending', '')
            self.send('Ввод отменён.')
        elif text.split(' ', 1)[0] in ('/done', '/delete'):
            command, _, arg = text.partition(' ')
            if not arg.strip().isdigit():
                self.send('Укажи номер из списка: /done 3 или /delete 2')
                return
            number = int(arg.strip()) if len(arg.strip()) < 19 else 0
            with self.db:
                if command == '/done':
                    cursor = self.db.execute("UPDATE items SET done=1 WHERE id=? AND kind='todo' AND done=0", (number,))
                else:
                    cursor = self.db.execute("DELETE FROM items WHERE id=? AND kind='note'", (number,))
            self.send('Готово.' if cursor.rowcount else 'Номер не найден или дело уже выполнено.')
        elif text in ('/gpt', '💬 GPT'):
            self.send('GPT отключён. Платных ИИ-запросов нет. Нажми «Помощь».')
        elif not text:
            self.send('Пока принимаю только текст.')
        else:
            command, _, body = text.partition(' ')
            kind = command[1:] if command in ('/note', '/todo') else self.get('pending')
            if text.startswith('/') and command not in ('/note', '/todo'):
                self.send('Неизвестная команда. Нажми «Помощь».')
                return
            if kind:
                value = body.strip() if command in ('/note', '/todo') else text
                if not value or len(value) > 4000:
                    self.send('Нужен текст от 1 до 4000 символов.')
                    return
                count = self.db.execute('SELECT COUNT(*) FROM items WHERE kind=?', (kind,)).fetchone()[0]
                if count >= 100:
                    self.send('Лимит — 100 записей в списке. Для заметок используй /delete номер.')
                    return
                with self.db:
                    cursor = self.db.execute('INSERT INTO items(kind,text) VALUES (?,?)', (kind, value))
                self.set('pending', '')
                self.send('Сохранено, номер ' + str(cursor.lastrowid) + '.')
            elif text.lower().rstrip('!.,') in ('привет', 'привет ронин', 'здравствуй'):
                self.send('Привет! 🥷 Я на связи. Могу сохранить заметку или дело — выбери кнопку.')
            else:
                self.send('Я пока работаю по командам, без ИИ. Чтобы сохранить этот текст, нажми «➕ Заметка» и отправь его ещё раз.')

    def run(self):
        self.tg('getMe')
        if self.tg('getWebhookInfo').get('url'):
            raise ValueError('У бота уже установлен webhook. Используйте отдельного бота или отключите webhook вручную.')
        print('Бот запущен. Остановка: Ctrl+C. Сообщения и ключи в журнал не выводятся.', flush=True)
        while True:
            try:
                updates = self.tg('getUpdates', offset=int(self.get('offset', '0')),
                                  timeout=30, allowed_updates=['message'])
                for update in updates:
                    # At-most-once processing avoids duplicate notes after restart.
                    # A crash during processing can lose this one incoming message.
                    self.set('offset', str(update['update_id'] + 1))
                    self.handle(update)
            except ApiError as error:
                print('Ошибка сервиса:', str(error), flush=True)
                time.sleep(5)


if __name__ == '__main__':
    import sys
    os.umask(0o077)
    try:
        if '--setup' in sys.argv:
            setup()
        else:
            Bot(load_config(), ROOT / 'history.sqlite3').run()
    except KeyboardInterrupt:
        print('\nБот остановлен.')
    except (ValueError, ApiError) as error:
        print(str(error))
        sys.exit(1)
