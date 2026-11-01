import { afterEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, api, setResponseListener } from './client.ts';

const realFetch = globalThis.fetch;

const respond = (status: number, body: unknown = null) => {
  globalThis.fetch = async () => new Response(body === null ? null : JSON.stringify(body), { status });
};

describe('응답 알림(쓰기 뒤 배지 다시 받기용)', () => {
  afterEach(() => {
    globalThis.fetch = realFetch;
    setResponseListener(null);
  });

  test('응답을 받으면 성공 · 실패 모두 메서드와 경로를 알린다', async () => {
    const seen: string[] = [];
    setResponseListener((method, path) => seen.push(`${method} ${path}`));

    respond(202, { eventId: 1, applicationStatus: 'APPLYING' });
    await api('POST', '/events');
    respond(204);
    await api('PATCH', '/notifications/3/read');
    respond(400, { code: 'INVALID_INPUT', message: '입력 값을 확인해 주세요.' });
    await assert.rejects(api('PATCH', '/settings/email', { json: { enabled: true } }), ApiError);
    respond(200, { count: 2 });
    await api('GET', '/notifications/unread-count');

    assert.deepEqual(seen, ['POST /events', 'PATCH /notifications/3/read', 'PATCH /settings/email', 'GET /notifications/unread-count']);
  });

  test('네트워크 오류(응답 없음)면 알리지 않는다', async () => {
    const seen: string[] = [];
    setResponseListener((method, path) => seen.push(`${method} ${path}`));
    globalThis.fetch = async () => {
      throw new TypeError('Failed to fetch');
    };

    await assert.rejects(api('POST', '/events'), (e: unknown) => e instanceof ApiError && e.code === 'NETWORK_ERROR');
    assert.deepEqual(seen, []);
  });
});
