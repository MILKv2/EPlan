// @ts-check

const WARSAW_DATE = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Warsaw', year: 'numeric', month: '2-digit', day: '2-digit' });

/** School date in Poland, YYYY-MM-DD. @param {Date} now */
export function warsawDate(now) {
  return WARSAW_DATE.format(now);
}

/** @param {string} date YYYY-MM-DD */
function isSchoolDay(date) {
  const day = new Date(`${date}T12:00:00Z`).getUTCDay();
  return day !== 0 && day !== 6;
}

/** @param {string} date YYYY-MM-DD @param {number} days */
function addDays(date, days) {
  const d = new Date(`${date}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/**
 * Today (if a school day) and the following school days, [count] in total. A day enters the
 * window two school days ahead, before the school usually publishes its substitutions.
 * @param {Date} now
 */
export function watchedDates(now, count = 3) {
  /** @type {string[]} */
  const dates = [];
  for (let date = warsawDate(now); dates.length < count; date = addDays(date, 1)) {
    if (isSchoolDay(date)) dates.push(date);
  }
  return dates;
}
