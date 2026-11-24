import type { Row } from './edupage.ts';
import { classTopic, teacherTopic } from './topics.ts';

/** Hash of a day's rows per FCM topic: one per class, one per teacher named in a row. */
export type Fingerprints = Record<string, string>;

export async function fingerprints(rows: Row[]): Promise<Fingerprints> {
  const lines = new Map<string, string[]>();
  const add = (topic: string, line: string) => {
    const list = lines.get(topic);
    if (list) list.push(line);
    else lines.set(topic, [line]);
  };
  for (const row of rows) {
    const line = [row.className, row.period, row.what, row.info].join('|');
    add(classTopic(row.className), line);
    for (const teacher of row.teachers) add(teacherTopic(teacher), line);
  }
  const result: Fingerprints = {};
  for (const [topic, list] of lines) {
    // Sorted, so EduPage reordering rows isn't a change.
    result[topic] = await sha256(list.sort().join('\n'));
  }
  return result;
}

/** Topics whose rows were added, changed or removed (a removed row is news too: the lesson is back). */
export function changedTopics(before: Fingerprints, after: Fingerprints): string[] {
  const topics = new Set([...Object.keys(before), ...Object.keys(after)]);
  return [...topics].filter((topic) => before[topic] !== after[topic]).sort();
}

async function sha256(text: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return [...new Uint8Array(digest).slice(0, 8)].map((b) => b.toString(16).padStart(2, '0')).join('');
}
