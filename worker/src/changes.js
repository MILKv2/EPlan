// @ts-check
import { classTopic, teacherTopic } from './topics.js';

/** @typedef {import('./edupage.js').Row} Row */

/** Hash of a day's rows per FCM topic: one per class, one per teacher named in a row. @typedef {Record<string, string>} Fingerprints */

/**
 * @param {Row[]} rows
 * @returns {Promise<Fingerprints>}
 */
export async function fingerprints(rows) {
  /** @type {Map<string, string[]>} */
  const lines = new Map();
  const add = (/** @type {string} */ topic, /** @type {string} */ line) => {
    const list = lines.get(topic);
    if (list) list.push(line);
    else lines.set(topic, [line]);
  };
  for (const row of rows) {
    const line = [row.className, row.period, row.what, row.info].join('|');
    add(classTopic(row.className), line);
    for (const teacher of row.teachers) add(teacherTopic(teacher), line);
  }
  /** @type {Fingerprints} */
  const result = {};
  for (const [topic, list] of lines) {
    // Sorted, so EduPage reordering rows isn't a change.
    result[topic] = await sha256(list.sort().join('\n'));
  }
  return result;
}

/**
 * Topics whose rows were added, changed or removed (a removed row is news too: the lesson is back).
 * @param {Fingerprints} before
 * @param {Fingerprints} after
 */
export function changedTopics(before, after) {
  const topics = new Set([...Object.keys(before), ...Object.keys(after)]);
  return [...topics].filter((topic) => before[topic] !== after[topic]).sort();
}

/** @param {string} text */
async function sha256(text) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return [...new Uint8Array(digest).slice(0, 8)].map((b) => b.toString(16).padStart(2, '0')).join('');
}
