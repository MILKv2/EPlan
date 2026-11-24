const WARSAW_DATE = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Warsaw', year: 'numeric', month: '2-digit', day: '2-digit' });

/** School date in Poland, YYYY-MM-DD. */
export function warsawDate(now: Date): string {
  return WARSAW_DATE.format(now);
}

function isSchoolDay(date: string): boolean {
  const day = new Date(`${date}T12:00:00Z`).getUTCDay();
  return day !== 0 && day !== 6;
}

function addDays(date: string, days: number): string {
  const d = new Date(`${date}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/**
 * Today (if a school day) and the following school days, [count] in total. A day enters the
 * window two school days ahead, before the school usually publishes its substitutions.
 */
export function watchedDates(now: Date, count = 3): string[] {
  const dates: string[] = [];
  for (let date = warsawDate(now); dates.length < count; date = addDays(date, 1)) {
    if (isSchoolDay(date)) dates.push(date);
  }
  return dates;
}
