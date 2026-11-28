import assert from 'node:assert/strict';
import { test } from 'node:test';
import { classTopic, teacherTopic, TIMETABLE_TOPIC } from '../src/topics.ts';
import { fixtureJson } from './helpers.ts';

test('topic names match the shared vectors (the app checks the same file)', () => {
  for (const { kind, name, topic } of fixtureJson<{ kind: string; name: string; topic: string }[]>('topics.json')) {
    const actual = kind === 'class' ? classTopic(name) : kind === 'teacher' ? teacherTopic(name) : TIMETABLE_TOPIC;
    assert.equal(actual, topic, name);
    assert.match(topic, /^[a-zA-Z0-9-_.~%]{1,900}$/);
  }
});
