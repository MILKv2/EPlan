import assert from 'node:assert/strict';
import { test } from 'node:test';
import { classTopic, teacherTopic } from '../src/topics.ts';
import { fixtureJson } from './helpers.ts';

test('topic names match the shared vectors (the app checks the same file)', () => {
  for (const { kind, name, topic } of fixtureJson<{ kind: string; name: string; topic: string }[]>('topics.json')) {
    assert.equal(kind === 'class' ? classTopic(name) : teacherTopic(name), topic, name);
    assert.match(topic, /^[a-zA-Z0-9-_.~%]{1,900}$/);
  }
});
