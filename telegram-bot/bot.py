"""Private Ronin/GPT Telegram chat. Python 3.10+, standard library only."""
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
KEYBOARD = {'keyboard': [['🥷 Ронин', '💬 GPT'], ['🧹 Новый диалог']], 'resize_keyboard': True}
PROMPTS = {
    'ronin': 'Ты Ронин, личный ИИ-помощник в образе спокойного самурая в маске. '
             'Общайся по-русски, на ты, ясно и по делу, без театральности. '
             'Ты работаешь через GPT API в Telegram. У тебя нет доступа к телефону, '
             'Android-приложению, другим чатам, файлам или интернет-поиску. '
             'Не утверждай, что выполнил действие, которое не можешь выполнить.',
    'gpt': 'Ты полезный ИИ-помощник GPT. Отвечай по-русски, если не просят иначе. '
           'Объясняй понятно и честно. У тебя нет инструментов управления телефоном '
           'или доступа к истории аккаунта ChatGPT и интернет-поиску.',
}


class ApiError(Exception):
    pass


def post(url, data, key=None, timeout=120):
    headers = {'Content-Type': 'application/json'}
    if key:
        headers['Authorization'] = 'Bearer ' + key
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
    for key in ('TELEGRAM_BOT_TOKEN', 'OPENAI_API_KEY', 'OPENAI_MODEL', 'OWNER_ID'):
        values[key] = os.environ.get(key, values.get(key, ''))
    if not all(values.values()) or not values['OWNER_ID'].isdigit() or int(values['OWNER_ID']) <= 0:
        raise ValueError('Сначала запустите: python3 bot.py --setup')
    return values


def setup():
    if CONFIG.exists():
        raise ValueError('.env уже существует. Отредактируйте его локально для смены настроек.')
    token = getpass.getpass('Токен Telegram от BotFather (ввод скрыт): ').strip()
    key = getpass.getpass('OpenAI API key (ввод скрыт): ').strip()
    model = input('Модель из вашего OpenAI API проекта: ').strip()
    owner = input('Ваш числовой Telegram user ID: ').strip()
    if not token or not key or not model or not owner.isdigit() or int(owner) <= 0:
        raise ValueError('Все поля обязательны; ID должен быть положительным числом.')
    with os.fdopen(os.open(CONFIG, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w') as file:
        file.write(f'TELEGRAM_BOT_TOKEN={token}\nOPENAI_API_KEY={key}\nOPENAI_MODEL={model}\nOWNER_ID={owner}\n')
    print('Настройки сохранены. Запуск: python3 bot.py')


def extract_text(response):
    texts = []
    for item in response.get('output', []):
        if item.get('type') == 'message':
            for part in item.get('content', []):
                if part.get('type') == 'output_text':
                    texts.append(part['text'])
                elif part.get('type') == 'refusal':
                    texts.append(part['refusal'])
    text = '\n'.join(texts).strip()
    if not text:
        raise ApiError('Модель не вернула текст')
    if response.get('status') == 'incomplete':
        text += '\n[Ответ прерван лимитом генерации.]'
    return text


class Bot:
    def __init__(self, config, db_path, request=post):
        self.config, self.request = config, request
        self.db = sqlite3.connect(db_path)
        self.db.execute('PRAGMA secure_delete=ON')
        self.db.execute('CREATE TABLE IF NOT EXISTS state (key TEXT PRIMARY KEY, value TEXT)')
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
        mode = self.get('mode', 'ronin')
        if text in ('/start', '/help'):
            self.send('Выбери Ронина или GPT и напиши сообщение. Оба режима используют GPT API, '
                      'истории раздельные. Ронин здесь — образ помощника; управление телефоном '
                      'не подключено. /new — очистить текущий диалог; /forget — оба. '
                      'Текст и последние 10 пар сообщений отправляются в OpenAI. '
                      'Фотографии и голос пока не поддерживаются.')
        elif text in ('🥷 Ронин', '/ronin', '💬 GPT', '/gpt'):
            mode = 'ronin' if text in ('🥷 Ронин', '/ronin') else 'gpt'
            self.set('mode', mode)
            self.send('Режим: ' + ('🥷 Ронин' if mode == 'ronin' else '💬 GPT'))
        elif text in ('🧹 Новый диалог', '/new', '/forget'):
            for target in (PROMPTS if text == '/forget' else (mode,)):
                self.set('history:' + target, '[]')
            self.send('История очищена.' if text == '/forget' else 'Начинаем новый диалог.')
        elif not text:
            self.send('Пока принимаю только текст. Напиши свой вопрос.')
        elif text.startswith('/'):
            self.send('Команды: /ronin, /gpt, /new, /forget, /help')
        elif len(text) > 12000:
            self.send('Сократи сообщение до 12000 символов.')
        else:
            history = json.loads(self.get('history:' + mode, '[]'))
            messages = history + [{'role': 'user', 'content': text}]
            try:
                response = self.request('https://api.openai.com/v1/responses', {
                    'model': self.config['OPENAI_MODEL'], 'instructions': PROMPTS[mode],
                    'input': messages, 'store': False, 'max_output_tokens': 2048,
                }, key=self.config['OPENAI_API_KEY'])
                answer = extract_text(response)
            except ApiError as error:
                self.send('Не удалось получить ответ: ' + str(error) +
                          '. Проверь ключ, баланс API, доступ к модели и соединение. Повтори вопрос.')
                return
            self.set('history:' + mode, json.dumps((messages + [{'role': 'assistant', 'content': answer}])[-20:], ensure_ascii=False))
            self.send(answer)

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
                    # At-most-once processing avoids duplicate paid requests after restart.
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
