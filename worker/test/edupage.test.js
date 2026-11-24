// @ts-check
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseSubstitutions } from '../src/edupage.js';
import { substitutionsHtml } from './helpers.js';

test('parses every row with its class, like the app', () => {
  const rows = parseSubstitutions(substitutionsHtml('2026-11-24'));
  assert.equal(rows.length, 55);
  assert.deepEqual(
    rows.filter((r) => r.className === '1at').map((r) => r.period),
    ['4', '5', '(9)'],
  );
});

test('collects planned, substitute and cancelled teachers', () => {
  const rows = parseSubstitutions(substitutionsHtml('2026-11-24')).filter((r) => r.className === '1at');
  assert.deepEqual(rows[0].teachers, ['Wojciech Książkiewicz', 'Anna Grzebień']);
  assert.deepEqual(rows[2].teachers, ['Mariusz Czekański']);
  assert.match(rows[0].what, /\u0001PEiE\u0002 ➔ inf/);
});

test('a struck-out name alone is the teacher without a substitute', () => {
  const rows = parseSubstitutions(substitutionsHtml('2026-11-25'));
  const missing = rows.filter((r) => /^\u0001[^\u0002]+\u0002$/.test(r.info));
  assert.ok(missing.length > 0);
  for (const row of missing) assert.equal(row.teachers.length, 1);
});

test('"(Bez klasy …)" and "Anulowano" are not teachers', () => {
  const rows = parseSubstitutions(substitutionsHtml('2026-11-20'));
  const without = rows.filter((r) => r.info.includes('Bez klasy'));
  assert.ok(without.length > 0);
  for (const row of without) assert.ok(row.teachers.every((t) => !t.includes('Bez klasy')));
  for (const row of parseSubstitutions(substitutionsHtml('2026-11-24'))) assert.ok(!row.teachers.includes('Anulowano'));
});

test('a day without substitutions has no rows', () => {
  assert.deepEqual(parseSubstitutions(substitutionsHtml('2026-11-21')), []);
});

test('class absences and whole-day rows keep their class', () => {
  const rows = parseSubstitutions(substitutionsHtml('2026-10-15'));
  assert.ok(rows.some((r) => r.period === 'Cały dzień' && r.className !== ''));
  assert.ok(rows.every((r) => r.className !== ''));
});
