// @ts-check
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { classTopic, teacherTopic } from '../src/topics.js';
import { fixtureJson } from './helpers.js';

test('topic names match the shared vectors (the app checks the same file)', () => {
  for (const { kind, name, topic } of fixtureJson('topics.json')) {
    assert.equal(kind === 'class' ? classTopic(name) : teacherTopic(name), topic, name);
    assert.match(topic, /^[a-zA-Z0-9-_.~%]{1,900}$/);
  }
});
