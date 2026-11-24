// @ts-check
// FCM topic names. The app subscribes with the same rules (android/core/.../push/PushTopics.kt);
// both sides are tested against fixtures/topics.json.

/** @type {Record<string, string>} */
const POLISH = { ą: 'a', ć: 'c', ę: 'e', ł: 'l', ń: 'n', ó: 'o', ś: 's', ź: 'z', ż: 'z' };

/** Lowercase ASCII words joined with "-": topic names allow only [a-zA-Z0-9-_.~%]. @param {string} text */
export function slug(text) {
  return text
    .toLowerCase()
    .replace(/[ąćęłńóśźż]/g, (ch) => POLISH[ch])
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

/** @param {string} className e.g. "4dft" */
export const classTopic = (className) => `klasa-${slug(className)}`;

/** @param {string} fullName as in substitutions, e.g. "Krystian Małeta" */
export const teacherTopic = (fullName) => `naucz-${slug(fullName)}`;
