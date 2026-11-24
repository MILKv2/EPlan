import assert from 'node:assert/strict';
import { test } from 'node:test';
import { warsawDate, watchedDates } from '../src/calendar.ts';

test('dates are Polish, not UTC', () => {
  // Monday 00:30 in Warsaw is still Sunday in UTC.
  assert.equal(warsawDate(new Date('2026-11-29T23:30:00Z')), '2026-11-30');
});

test('watches today and the next two school days', () => {
  assert.deepEqual(watchedDates(new Date('2026-11-24T08:00:00Z')), ['2026-11-24', '2026-11-25', '2026-11-26']);
  // Friday: over the weekend.
  assert.deepEqual(watchedDates(new Date('2026-11-27T15:00:00Z')), ['2026-11-27', '2026-11-30', '2026-12-01']);
  // Saturday: no today.
  assert.deepEqual(watchedDates(new Date('2026-11-28T10:00:00Z')), ['2026-11-30', '2026-12-01', '2026-12-02']);
});
