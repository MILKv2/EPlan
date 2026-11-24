import assert from 'node:assert/strict';
import { test } from 'node:test';
import { changedTopics, fingerprints } from '../src/changes.ts';
import { parseSubstitutions } from '../src/edupage.ts';
import { substitutionsHtml } from './helpers.ts';

const rows = parseSubstitutions(substitutionsHtml('2026-11-24'));

test('one fingerprint per class and per named teacher', async () => {
  const prints = await fingerprints(rows);
  assert.ok('klasa-1at' in prints);
  assert.ok('naucz-anna-grzebien' in prints);
  assert.equal(Object.keys(prints).filter((t) => t.startsWith('klasa-')).length, new Set(rows.map((r) => r.className)).size);
});

test('row order does not matter', async () => {
  assert.deepEqual(await fingerprints([...rows].reverse()), await fingerprints(rows));
});

test('a changed row flags its class and both teachers, nothing else', async () => {
  const before = await fingerprints(rows);
  const edited = rows.map((r) =>
    r.className === '1bl' && r.period === '5' ? { ...r, info: r.info.replace('A138', 'A139') } : r,
  );
  assert.deepEqual(changedTopics(before, await fingerprints(edited)), ['klasa-1bl', 'naucz-anna-jurczak', 'naucz-tomasz-przybyla']);
});

test('removed and added rows are changes', async () => {
  const before = await fingerprints(rows);
  const without1al = await fingerprints(rows.filter((r) => r.className !== '1al'));
  assert.ok(changedTopics(before, without1al).includes('klasa-1al'));
  assert.ok(changedTopics(without1al, before).includes('klasa-1al'));
});
