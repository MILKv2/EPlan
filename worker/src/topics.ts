// FCM topic names. The app subscribes with the same rules (android/core/.../push/PushTopics.kt);
// both sides are tested against fixtures/topics.json.

const POLISH: Record<string, string> = { ą: 'a', ć: 'c', ę: 'e', ł: 'l', ń: 'n', ó: 'o', ś: 's', ź: 'z', ż: 'z' };

/** Lowercase ASCII words joined with "-": topic names allow only [a-zA-Z0-9-_.~%]. */
export function slug(text: string): string {
  return text
    .toLowerCase()
    .replace(/[ąćęłńóśźż]/g, (ch) => POLISH[ch] ?? ch)
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

/** e.g. "4dft" */
export const classTopic = (className: string): string => `klasa-${slug(className)}`;

/** Full name as in substitutions, e.g. "Jan Kowalski". */
export const teacherTopic = (fullName: string): string => `naucz-${slug(fullName)}`;

/** Every install with notifications on: a new timetable version was published. */
export const TIMETABLE_TOPIC = 'plan-lekcji';
