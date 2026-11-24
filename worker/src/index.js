// @ts-check
// Every 5 minutes: fetch the watched days' substitutions, compare per-topic fingerprints with the
// last run, and wake the phones subscribed to changed topics. The push carries only the date;
// the app fetches the day itself and works out what changed in its own plan.
import { changedTopics, fingerprints } from './changes.js';
import { fetchSubstitutionsHtml, parseSubstitutions } from './edupage.js';
import { accessToken, parseServiceAccount, sendToTopics, TOPICS_PER_MESSAGE } from './fcm.js';
import { watchedDates } from './calendar.js';

/**
 * @typedef {object} Env
 * @property {KVNamespace} STATE
 * @property {string} [FCM_SERVICE_ACCOUNT] service account JSON (wrangler secret)
 */

/** @typedef {{ get(key: string, type: 'json'): Promise<any>, put(key: string, value: string, options?: { expiration?: number }): Promise<void> }} KVNamespace */

// Free plan: 50 subrequests per run. 3 fetches + 1 token leave room for this many sends;
// topics beyond it keep their old fingerprint and go out on the next run.
const MAX_SENDS_PER_RUN = 40;

export default {
  /**
   * @param {{ scheduledTime: number }} controller
   * @param {Env} env
   */
  async scheduled(controller, env) {
    await run(env, { now: new Date(controller.scheduledTime) });
  },
};

/**
 * @param {Env} env
 * @param {{ now?: Date, fetchImpl?: typeof fetch, log?: Pick<Console, 'log' | 'error'> }} [options]
 */
export async function run(env, { now = new Date(), fetchImpl = fetch, log = console } = {}) {
  if (!env.FCM_SERVICE_ACCOUNT) {
    // Without a way to notify, recording the state would swallow the changes.
    log.error('FCM_SERVICE_ACCOUNT is not set; nothing checked');
    return;
  }
  /** @type {import('./fcm.js').ServiceAccount} */
  let account;
  try {
    account = parseServiceAccount(env.FCM_SERVICE_ACCOUNT);
  } catch (error) {
    log.error(`${error instanceof Error ? error.message : error}; nothing checked`);
    return;
  }
  let sendsLeft = MAX_SENDS_PER_RUN;
  /** @type {(topics: string[], date: string) => Promise<void>} */
  const notify = async (topics, date) => {
    const token = await accessToken(account, fetchImpl, now.getTime());
    await sendToTopics({ token, projectId: account.project_id, topics, data: { date }, collapseKey: `subst-${date}` }, fetchImpl);
  };

  for (const date of watchedDates(now)) {
    try {
      sendsLeft -= await checkDay(env, date, sendsLeft, notify, fetchImpl, log);
    } catch (error) {
      log.error(`${date}: ${error instanceof Error ? error.message : error}`);
    }
  }
}

/**
 * @param {Env} env
 * @param {string} date
 * @param {number} sendsLeft
 * @param {(topics: string[], date: string) => Promise<void>} notify
 * @param {typeof fetch} fetchImpl
 * @param {Pick<Console, 'log' | 'error'>} log
 * @returns {Promise<number>} messages sent
 */
async function checkDay(env, date, sendsLeft, notify, fetchImpl, log) {
  const current = await fingerprints(parseSubstitutions(await fetchSubstitutionsHtml(date, fetchImpl)));
  const key = `subst:${date}`;
  const expiration = stateExpiration(date);
  /** @type {Record<string, string> | null} */
  const previous = await env.STATE.get(key, 'json');
  if (previous === null) {
    // First sighting of the day (new in the window, or a fresh deploy): remember, don't notify.
    await env.STATE.put(key, JSON.stringify(current), { expiration });
    return 0;
  }

  const changed = changedTopics(previous, current);
  const stored = { ...current };
  let sent = 0;
  for (let i = 0; i < changed.length; i += TOPICS_PER_MESSAGE) {
    const batch = changed.slice(i, i + TOPICS_PER_MESSAGE);
    try {
      if (sent >= sendsLeft) throw new Error('send limit for this run reached');
      await notify(batch, date);
      sent++;
      log.log(`${date}: notified ${batch.join(', ')}`);
    } catch (error) {
      log.error(`${date}: ${batch.join(', ')} not notified, retrying next run: ${error instanceof Error ? error.message : error}`);
      // Keep the old fingerprints so the next run sees these topics as changed again.
      for (const topic of batch) {
        if (topic in previous) stored[topic] = previous[topic];
        else delete stored[topic];
      }
    }
  }
  if (changedTopics(previous, stored).length > 0) {
    await env.STATE.put(key, JSON.stringify(stored), { expiration });
  }
  return sent;
}

/**
 * Unix seconds when a day's fingerprints may go: a day after it ends. A fixed point rather than a
 * TTL, because the key is written only on change and a day can sit in the window for up to 5
 * calendar days (Friday → Tuesday); a TTL could drop it mid-window and swallow the next change.
 * @param {string} date YYYY-MM-DD
 */
export function stateExpiration(date) {
  return Date.parse(`${date}T00:00:00Z`) / 1000 + 2 * 24 * 60 * 60;
}
