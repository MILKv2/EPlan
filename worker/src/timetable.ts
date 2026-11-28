// New timetable versions: EduPage lists every version in getTTViewerData; a newly published one
// (not hidden) is announced once to every install on the timetable topic.
import { warsawDate } from './calendar.ts';

const BASE_URL = 'https://elektryk.edupage.org';

export interface Version {
  ttNum: string;
  name: string;
  /** YYYY-MM-DD */
  validFrom: string;
}

interface RawVersion {
  tt_num?: unknown;
  year?: unknown;
  text?: unknown;
  datefrom?: unknown;
  hidden?: unknown;
}

/** EduPage school year: 2026 = 2026/27, switching in August (as SchoolYear.kt in the app). */
export function schoolYear(now: Date): number {
  const [year, month] = warsawDate(now).split('-').map(Number) as [number, number];
  return month >= 8 ? year : year - 1;
}

export async function fetchVersions(year: number, fetchImpl: typeof fetch = fetch): Promise<unknown> {
  const response = await fetchImpl(`${BASE_URL}/timetable/server/ttviewer.js?__func=getTTViewerData`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=UTF-8', 'User-Agent': 'E-Plan-worker (nieoficjalna apka)' },
    body: JSON.stringify({ __args: [null, year], __gsh: '00000000' }),
  });
  if (!response.ok) throw new Error(`EduPage HTTP ${response.status} for timetable versions`);
  return response.json();
}

/** The newest visible version of [year] (latest datefrom, then highest number), or null. */
export function newestVersion(json: unknown, year: number): Version | null {
  const timetables = (json as { r?: { regular?: { timetables?: unknown } } } | null)?.r?.regular?.timetables;
  if (!Array.isArray(timetables)) throw new Error('EduPage: unexpected timetable versions response');
  const versions = (timetables as RawVersion[])
    .filter((v) => v.hidden !== true && v.year === year && typeof v.tt_num === 'string' && typeof v.datefrom === 'string')
    .map((v) => ({ ttNum: v.tt_num as string, name: typeof v.text === 'string' ? v.text : '', validFrom: v.datefrom as string }));
  versions.sort((a, b) => b.validFrom.localeCompare(a.validFrom) || Number(b.ttNum) - Number(a.ttNum));
  return versions[0] ?? null;
}

/** Whether [next] replaces [previous] going forward, rather than an older version reappearing. */
export function isNewer(next: Version, previous: Version): boolean {
  return next.validFrom > previous.validFrom || (next.validFrom === previous.validFrom && Number(next.ttNum) > Number(previous.ttNum));
}

const WARSAW_HOUR = new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Warsaw', hour: '2-digit', hourCycle: 'h23' });

/** Versions are often published at midnight; the announcement waits for the morning. */
export function isQuietHour(now: Date): boolean {
  const hour = Number(WARSAW_HOUR.format(now));
  return hour < 7 || hour >= 21;
}
