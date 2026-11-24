// Every 5 minutes: fetch the watched days' substitutions, compare per-topic fingerprints with the
// last run, and wake the phones subscribed to changed topics. The push carries only the date;
// the app fetches the day itself and works out what changed in its own plan.
import { watchedDates } from './calendar.ts';
import { changedTopics, fingerprints, type Fingerprints } from './changes.ts';
import { fetchSubstitutionsHtml, parseSubstitutions } from './edupage.ts';
import { accessToken, parseServiceAccount, sendToTopics, TOPICS_PER_MESSAGE, type ServiceAccount } from './fcm.ts';

/** The part of Workers KV this worker uses. */
export interface KVNamespace {
  get(key: string, type: 'json'): Promise<unknown>;
  put(key: string, value: string, options?: { expiration?: number }): Promise<void>;
}

export interface Env {
  STATE: KVNamespace;
  /** Service account JSON (wrangler secret). */
  FCM_SERVICE_ACCOUNT?: string;
}

type Log = Pick<Console, 'log' | 'error'>;
type Notify = (topics: string[], date: string) => Promise<void>;

// Free plan: 50 subrequests per run. 3 fetches + 1 token leave room for this many sends;
// topics beyond it keep their old fingerprint and go out on the next run.
const MAX_SENDS_PER_RUN = 40;

export default {
  async scheduled(controller: { scheduledTime: number }, env: Env): Promise<void> {
    await run(env, { now: new Date(controller.scheduledTime) });
  },
};

export async function run(
  env: Env,
  { now = new Date(), fetchImpl = fetch, log = console }: { now?: Date; fetchImpl?: typeof fetch; log?: Log } = {},
): Promise<void> {
  if (!env.FCM_SERVICE_ACCOUNT) {
    // Without a way to notify, recording the state would swallow the changes.
    log.error('FCM_SERVICE_ACCOUNT is not set; nothing checked');
    return;
  }
  let account: ServiceAccount;
  try {
    account = parseServiceAccount(env.FCM_SERVICE_ACCOUNT);
  } catch (error) {
    log.error(`${message(error)}; nothing checked`);
    return;
  }
  let sendsLeft = MAX_SENDS_PER_RUN;
  const notify: Notify = async (topics, date) => {
    const token = await accessToken(account, fetchImpl, now.getTime());
    await sendToTopics({ token, projectId: account.project_id, topics, data: { date }, collapseKey: `subst-${date}` }, fetchImpl);
  };

  for (const date of watchedDates(now)) {
    try {
      sendsLeft -= await checkDay(env, date, sendsLeft, notify, fetchImpl, log);
    } catch (error) {
      log.error(`${date}: ${message(error)}`);
    }
  }
}

/** Returns the number of messages sent. */
async function checkDay(env: Env, date: string, sendsLeft: number, notify: Notify, fetchImpl: typeof fetch, log: Log): Promise<number> {
  const current = await fingerprints(parseSubstitutions(await fetchSubstitutionsHtml(date, fetchImpl)));
  const key = `subst:${date}`;
  const expiration = stateExpiration(date);
  const previous = (await env.STATE.get(key, 'json')) as Fingerprints | null;
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
      log.error(`${date}: ${batch.join(', ')} not notified, retrying next run: ${message(error)}`);
      // Keep the old fingerprints so the next run sees these topics as changed again.
      for (const topic of batch) {
        const old = previous[topic];
        if (old !== undefined) stored[topic] = old;
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
 */
export function stateExpiration(date: string): number {
  return Date.parse(`${date}T00:00:00Z`) / 1000 + 2 * 24 * 60 * 60;
}

function message(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
