import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from bot import Bot, load_config

class FreeBotTests(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = str(Path(self.temp.name) / 'bot.db')
        self.config = {'TELEGRAM_BOT_TOKEN': 'test', 'OWNER_ID': '123'}
        def request(url, data):
            self.assertTrue(url.startswith('https://api.telegram.org/bot'))
            self.calls.append(data)
            return {'ok': True, 'result': {}}
        self.request = request
        self.bot = Bot(self.config, self.path, request)
        self.addCleanup(lambda: self.bot.db.close())

    def message(self, text, uid=123, kind='private'):
        self.bot.handle({'message': {'from': {'id': uid}, 'chat': {'id': uid, 'type': kind}, 'text': text}})

    def test_private_owner_only(self):
        self.message('/note secret', 456)
        self.message('/note secret', kind='group')
        self.assertEqual(self.calls, [])
        self.assertEqual(self.bot.db.execute('SELECT COUNT(*) FROM items').fetchone()[0], 0)

    def test_notes_tasks_persist_and_delete(self):
        self.message('➕ Заметка')
        self.message('Купить плёнку')
        self.message('/todo Проверить принтер')
        self.bot.db.close()
        self.bot = Bot(self.config, self.path, self.request)
        self.message('/notes')
        self.assertIn('Купить плёнку', self.calls[-1]['text'])
        self.message('/done 2')
        self.message('/todos')
        self.assertIn('☑', self.calls[-1]['text'])
        self.message('/delete 1')
        self.message('/notes')
        self.assertIn('пуст', self.calls[-1]['text'])

    def test_no_paid_service_even_with_old_key(self):
        self.bot.config['OPENAI_API_KEY'] = 'unused'
        for text in ('/gpt', 'Расскажи историю', '/start', '/help'):
            self.message(text)
        self.assertEqual(len(self.calls), 4)

    def test_cancel_and_empty_note(self):
        self.message('➕ Заметка')
        self.message('/cancel')
        self.message('Не сохранять')
        self.message('/note')
        self.assertEqual(self.bot.db.execute('SELECT COUNT(*) FROM items').fetchone()[0], 0)

    def test_config_needs_no_openai_key(self):
        with patch('bot.CONFIG', Path(self.temp.name) / 'missing'), patch.dict('os.environ', self.config, clear=True):
            self.assertEqual(load_config(), self.config)

if __name__ == '__main__':
    unittest.main()
