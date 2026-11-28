import assert from 'node:assert/strict';
import { test } from 'node:test';
import { checkTimetable, type KVNamespace } from '../src/index.ts';
import { isNewer, isQuietHour, newestVersion, schoolYear, type Version } from '../src/timetable.ts';
import { fixtureJson } from './helpers.ts';

const quiet = { log() {}, error() {} };
const versions = fixtureJson<unknown>('ttviewer_2026-11-28.json');
const MORNING = new Date('2026-11-28T08:00:00Z'); // 09:00 in Warsaw
const MIDNIGHT = new Date('2026-11-27T23:05:00Z'); // 00:05 in Warsaw

class MemoryKV implements KVNamespace {
  values = new Map<string, string>();
  async get(key: string): Promise<unknown> {
    const value = this.values.get(key);
    return value === undefined ? null : JSON.parse(value);
  }
  async put(key: string, value: string): Promise<void> {
    this.values.set(key, value);
  }
}

const serve = (json: unknown): typeof fetch => async () => Response.json(json);

function check(kv: MemoryKV, now: Date, json: unknown = versions, fail = false) {
  const announced: Version[] = [];
  const run = checkTimetable({ STATE: kv }, now, async (v) => {
    if (fail) throw new Error('FCM down');
    announced.push(v);
  }, serve(json), quiet);
  return { run, announced };
}

const stored = (ttNum: string, validFrom: string): Version => ({ ttNum, name: '', validFrom });

test('newest visible version of the school year', () => {
  assert.equal(schoolYear(MORNING), 2026);
  assert.equal(schoolYear(new Date('2027-07-31T12:00:00Z')), 2026);
  assert.equal(schoolYear(new Date('2027-08-01T12:00:00Z')), 2027);
  assert.deepEqual(newestVersion(versions, 2026), { ttNum: '190', name: 'grudzien2026_RC1 (28.11. - 31.12.2026)', validFrom: '2026-11-28' });
  assert.equal(newestVersion(versions, 2030), null);
  assert.throws(() => newestVersion({ e: 'Error' }, 2026), /unexpected/);
});

test('newer means a later start, or the same start with a higher number', () => {
  assert.ok(isNewer(stored('190', '2026-11-28'), stored('189', '2026-11-21')));
  assert.ok(isNewer(stored('191', '2026-11-28'), stored('190', '2026-11-28')));
  assert.ok(!isNewer(stored('189', '2026-11-21'), stored('190', '2026-11-28')));
});

test('quiet hours are 21:00-7:00 in Warsaw', () => {
  assert.ok(isQuietHour(MIDNIGHT));
  assert.ok(!isQuietHour(MORNING));
  assert.ok(isQuietHour(new Date('2026-11-28T20:00:00Z')));
});

test('the first sighting is stored without an announcement', async () => {
  const kv = new MemoryKV();
  const { run, announced } = check(kv, MORNING);
  await run;
  assert.deepEqual(announced, []);
  assert.equal((JSON.parse(kv.values.get('timetable')!) as Version).ttNum, '190');
});

test('a newer version is announced once, in the morning', async () => {
  const kv = new MemoryKV();
  kv.values.set('timetable', JSON.stringify(stored('189', '2026-11-21')));

  const night = check(kv, MIDNIGHT);
  await night.run;
  assert.deepEqual(night.announced, []);
  assert.equal((JSON.parse(kv.values.get('timetable')!) as Version).ttNum, '189', 'kept for the morning run');

  const morning = check(kv, MORNING);
  await morning.run;
  assert.deepEqual(morning.announced.map((v) => v.ttNum), ['190']);

  const again = check(kv, MORNING);
  await again.run;
  assert.deepEqual(again.announced, []);
});

test('a failed announcement is retried', async () => {
  const kv = new MemoryKV();
  kv.values.set('timetable', JSON.stringify(stored('189', '2026-11-21')));
  const failed = check(kv, MORNING, versions, true);
  await assert.rejects(failed.run, /FCM down/);
  assert.equal((JSON.parse(kv.values.get('timetable')!) as Version).ttNum, '189');
  const retry = check(kv, MORNING);
  await retry.run;
  assert.deepEqual(retry.announced.map((v) => v.ttNum), ['190']);
});

test('an older version coming back is stored silently', async () => {
  const kv = new MemoryKV();
  kv.values.set('timetable', JSON.stringify(stored('191', '2027-01-04')));
  const { run, announced } = check(kv, MORNING);
  await run;
  assert.deepEqual(announced, []);
  assert.equal((JSON.parse(kv.values.get('timetable')!) as Version).ttNum, '190');
});
