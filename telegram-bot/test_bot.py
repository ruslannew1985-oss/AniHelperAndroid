import unittest
from bot import Bot, ApiError, extract_text


class BotTests(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.fail = False
        def request(url, data, **kwargs):
            self.calls.append((url, data))
            if url.endswith('/responses'):
                if self.fail:
                    raise ApiError('HTTP 429')
                return {'output': [{'type': 'reasoning'}, {'type': 'message', 'content': [{'type': 'output_text', 'text': 'Ответ'}]}]}
            return {'ok': True, 'result': {}}
        self.bot = Bot({'TELEGRAM_BOT_TOKEN': 'test', 'OPENAI_API_KEY': 'test', 'OPENAI_MODEL': 'test', 'OWNER_ID': '123'}, ':memory:', request)
        self.addCleanup(self.bot.db.close)

    def message(self, text, uid=123, chat_type='private'):
        self.bot.handle({'message': {'from': {'id': uid}, 'chat': {'id': uid, 'type': chat_type}, 'text': text}})

    def test_access_denied_without_api_call(self):
        self.message('привет', 456)
        self.message('привет', chat_type='group')
        self.assertEqual(self.calls, [])

    def test_modes_have_separate_history_and_clear(self):
        self.message('первый вопрос')
        self.message('/gpt')
        self.message('другой вопрос')
        requests = [data for url, data in self.calls if url.endswith('/responses')]
        self.assertEqual(len(requests[1]['input']), 1)
        self.message('/new')
        self.assertEqual(self.bot.get('history:gpt'), '[]')
        self.assertIn('первый вопрос', self.bot.get('history:ronin'))
        self.message('/forget')
        self.assertEqual(self.bot.get('history:ronin'), '[]')

    def test_error_does_not_save_or_retry(self):
        self.fail = True
        self.message('вопрос')
        self.assertEqual(self.bot.get('history:ronin'), '')
        self.assertEqual(sum(url.endswith('/responses') for url, _ in self.calls), 1)
        self.assertIn('HTTP 429', self.calls[-1][1]['text'])

    def test_long_emoji_answer_chunks(self):
        text = '🥷' * 5000
        self.bot.send(text)
        chunks = [data['text'] for _, data in self.calls]
        self.assertEqual(''.join(chunks), text)
        self.assertTrue(all(len(c.encode('utf-16-le')) // 2 <= 4096 for c in chunks))

    def test_bounded_history(self):
        for _ in range(15):
            self.message('вопрос')
        import json
        self.assertEqual(len(json.loads(self.bot.get('history:ronin'))), 20)

    def test_refusal_and_empty_response(self):
        self.assertEqual(extract_text({'output': [{'type': 'message', 'content': [{'type': 'refusal', 'refusal': 'Отказ'}]}]}), 'Отказ')
        with self.assertRaises(ApiError):
            extract_text({'output': []})


if __name__ == '__main__':
    unittest.main()
