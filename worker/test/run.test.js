// @ts-check
import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import { clearTokenCache } from '../src/fcm.js';
import { run, stateExpiration } from '../src/index.js';
import { substitutionsHtml, testAccount } from './helpers.js';

const NOW = new Date('2026-11-24T08:00:00Z'); // watches 11-24, 11-25, 11-26
const quiet = { log() {}, error() {} };

class MemoryKV {
  /** @type {Map<string, string>} */ values = new Map();
  /** @type {Map<string, number | undefined>} */ expirations = new Map();
  puts = 0;
  /** @param {string} key */
  async get(key) {
    const value = this.values.get(key);
    return value === undefined ? null : JSON.parse(value);
  }
  /** @param {string} key @param {string} value @param {{ expiration?: number }} [options] */
  async put(key, value, options) {
    this.puts++;
    this.values.set(key, value);
    this.expirations.set(key, options?.expiration);
  }
}

/**
 * Serves fixture days (an edit hook rewrites the HTML) and records FCM sends.
 * @param {{ edit?: (date: string, html: string) => string, failSends?: boolean }} [options]
 */
function fakeNetwork({ edit = (_, html) => html, failSends = false } = {}) {
  /** @type {{ condition: string, data: Record<string, string> }[]} */
  const sends = [];
  /** @type {typeof fetch} */
  const fetchImpl = async (url, init) => {
    const target = String(url);
    if (target.includes('edupage.org')) {
      const date = JSON.parse(String(init?.body)).__args[1].date;
      const file = ['2026-11-24', '2026-11-25'].includes(date) ? date : '2026-11-21';
      return Response.json({ r: edit(date, substitutionsHtml(file)) });
    }
    if (target.includes('oauth2')) return Response.json({ access_token: 'token', expires_in: 3600 });
    if (failSends) return new Response('unavailable', { status: 503 });
    sends.push(JSON.parse(String(init?.body)).message);
    return new Response('{}');
  };
  return { fetchImpl, sends };
}

beforeEach(() => clearTokenCache());

test('the first sighting of a day is stored without notifying', async () => {
  const env = { STATE: new MemoryKV(), FCM_SERVICE_ACCOUNT: JSON.stringify(testAccount) };
  const net = fakeNetwork();
  await run(env, { now: NOW, fetchImpl: net.fetchImpl, log: quiet });
  assert.equal(net.sends.length, 0);
  assert.deepEqual([...env.STATE.values.keys()].sort(), ['subst:2026-11-24', 'subst:2026-11-25', 'subst:2026-11-26']);
  assert.equal(env.STATE.expirations.get('subst:2026-11-24'), Date.parse('2026-11-26T00:00:00Z') / 1000);
});

test('a day is kept while watched and dropped a day after it ends', () => {
  // Friday 2026-11-27 watches Fri, Mon and Tue; Tuesday's key must outlive Tuesday in Warsaw.
  const tuesdayEndsInWarsaw = Date.parse('2026-12-01T23:00:00Z') / 1000;
  assert.ok(stateExpiration('2026-12-01') > tuesdayEndsInWarsaw);
  assert.ok(stateExpiration('2026-12-01') - tuesdayEndsInWarsaw <= 24 * 60 * 60 + 3600);
});

test('nothing changed: no pushes, no writes', async () => {
  const env = { STATE: new MemoryKV(), FCM_SERVICE_ACCOUNT: JSON.stringify(testAccount) };
  await run(env, { now: NOW, fetchImpl: fakeNetwork().fetchImpl, log: quiet });
  const puts = env.STATE.puts;
  const net = fakeNetwork();
  await run(env, { now: NOW, fetchImpl: net.fetchImpl, log: quiet });
  assert.equal(net.sends.length, 0);
  assert.equal(env.STATE.puts, puts);
});

test('a change wakes only its class and teachers, with the date', async () => {
  const env = { STATE: new MemoryKV(), FCM_SERVICE_ACCOUNT: JSON.stringify(testAccount) };
  await run(env, { now: NOW, fetchImpl: fakeNetwork().fetchImpl, log: quiet });
  const net = fakeNetwork({ edit: (date, html) => (date === '2026-11-24' ? html.replace('A138', 'A139') : html) });
  await run(env, { now: NOW, fetchImpl: net.fetchImpl, log: quiet });
  assert.deepEqual(net.sends, [
    {
      condition: "'klasa-1bl' in topics || 'naucz-anna-jurczak' in topics || 'naucz-tomasz-przybyla' in topics",
      data: { date: '2026-11-24' },
      android: { priority: 'HIGH', ttl: '21600s', collapse_key: 'subst-2026-11-24' },
    },
  ]);
  // Recorded: the same change doesn't go out twice.
  const again = fakeNetwork({ edit: (date, html) => (date === '2026-11-24' ? html.replace('A138', 'A139') : html) });
  await run(env, { now: NOW, fetchImpl: again.fetchImpl, log: quiet });
  assert.equal(again.sends.length, 0);
});

test('a failed push is retried on the next run', async () => {
  const env = { STATE: new MemoryKV(), FCM_SERVICE_ACCOUNT: JSON.stringify(testAccount) };
  await run(env, { now: NOW, fetchImpl: fakeNetwork().fetchImpl, log: quiet });
  const edit = (/** @type {string} */ date, /** @type {string} */ html) => (date === '2026-11-25' ? html.replace(/<tbody\b[\s\S]*?<\/tbody>/, '') : html);
  await run(env, { now: NOW, fetchImpl: fakeNetwork({ edit, failSends: true }).fetchImpl, log: quiet });
  const net = fakeNetwork({ edit });
  await run(env, { now: NOW, fetchImpl: net.fetchImpl, log: quiet });
  assert.ok(net.sends.length > 0);
  assert.ok(net.sends.every((m) => m.data.date === '2026-11-25'));
});

test('without the FCM key nothing is recorded', async () => {
  const env = { STATE: new MemoryKV() };
  await run(env, { now: NOW, fetchImpl: fakeNetwork().fetchImpl, log: quiet });
  assert.equal(env.STATE.puts, 0);
});

test('a broken FCM key is reported and nothing is recorded', async () => {
  const env = { STATE: new MemoryKV(), FCM_SERVICE_ACCOUNT: '{ not json' };
  /** @type {string[]} */
  const errors = [];
  await run(env, { now: NOW, fetchImpl: fakeNetwork().fetchImpl, log: { log() {}, error: (/** @type {string} */ m) => errors.push(m) } });
  assert.equal(env.STATE.puts, 0);
  assert.match(errors.join('\n'), /FCM_SERVICE_ACCOUNT is not a service account key file/);
});
