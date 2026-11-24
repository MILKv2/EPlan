// @ts-check
// FCM HTTP v1 with a service account, without SDKs: sign a JWT, trade it for an access token.

/**
 * @typedef {object} ServiceAccount
 * @property {string} project_id
 * @property {string} client_email
 * @property {string} private_key PEM, PKCS#8
 */

const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';

/**
 * The FCM_SERVICE_ACCOUNT secret. Pasting the key file on a phone can turn the "\n" escapes in
 * private_key into real line breaks, which JSON.parse rejects; the three fields are then read
 * directly. Errors name the problem, never the secret's content.
 * @param {string} raw
 * @returns {ServiceAccount}
 */
export function parseServiceAccount(raw) {
  /** @type {Partial<ServiceAccount>} */
  let account;
  try {
    account = JSON.parse(raw);
  } catch {
    const field = (/** @type {string} */ name) => new RegExp(`"${name}"\\s*:\\s*"([^"]*)"`).exec(raw)?.[1];
    account = { project_id: field('project_id'), client_email: field('client_email'), private_key: field('private_key') };
  }
  const missing = ['project_id', 'client_email', 'private_key'].filter((key) => !account[/** @type {keyof ServiceAccount} */ (key)]);
  if (missing.length > 0) throw new Error(`FCM_SERVICE_ACCOUNT is not a service account key file (missing ${missing.join(', ')})`);
  if (!account.private_key?.includes('PRIVATE KEY')) throw new Error('FCM_SERVICE_ACCOUNT: private_key is not a PEM key');
  return /** @type {ServiceAccount} */ (account);
}

/** A condition may combine at most five topics. */
export const TOPICS_PER_MESSAGE = 5;

/** @type {{ token: string, expiresAt: number } | null} */
let cachedToken = null;

/**
 * @param {ServiceAccount} account
 * @param {typeof fetch} fetchImpl
 * @param {number} now ms
 */
export async function accessToken(account, fetchImpl = fetch, now = Date.now()) {
  if (cachedToken && cachedToken.expiresAt - 60_000 > now) return cachedToken.token;
  const assertion = await signJwt(account, Math.floor(now / 1000));
  const response = await fetchImpl(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  });
  if (!response.ok) throw new Error(`OAuth token: HTTP ${response.status} ${await response.text()}`);
  const json = /** @type {{ access_token: string, expires_in: number }} */ (await response.json());
  cachedToken = { token: json.access_token, expiresAt: now + json.expires_in * 1000 };
  return json.access_token;
}

/** For tests. */
export function clearTokenCache() {
  cachedToken = null;
}

/**
 * @param {ServiceAccount} account
 * @param {number} issuedAt seconds
 */
export async function signJwt(account, issuedAt) {
  const header = { alg: 'RS256', typ: 'JWT' };
  const claims = { iss: account.client_email, scope: SCOPE, aud: TOKEN_URL, iat: issuedAt, exp: issuedAt + 3600 };
  const unsigned = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToDer(account.private_key),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned));
  return `${unsigned}.${base64url(new Uint8Array(signature))}`;
}

/**
 * A data-only message to devices subscribed to any of [topics] (at most five).
 * @param {{ token: string, projectId: string, topics: string[], data: Record<string, string>, collapseKey: string }} message
 * @param {typeof fetch} fetchImpl
 */
export async function sendToTopics({ token, projectId, topics, data, collapseKey }, fetchImpl = fetch) {
  if (topics.length === 0 || topics.length > TOPICS_PER_MESSAGE) throw new Error(`FCM: ${topics.length} topics in one message`);
  const response = await fetchImpl(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message: {
        condition: topics.map((topic) => `'${topic}' in topics`).join(' || '),
        data,
        // High priority so the app may fetch the day and post a notification right away.
        android: { priority: 'HIGH', ttl: '21600s', collapse_key: collapseKey },
      },
    }),
  });
  if (!response.ok) throw new Error(`FCM send: HTTP ${response.status} ${await response.text()}`);
}

/** @param {string} pem */
function pemToDer(pem) {
  // Literal "\n" pairs remain when the key was read without JSON.parse.
  const base64 = pem.replace(/-----[^-]+-----/g, '').replace(/\\n/g, '').replace(/\s+/g, '');
  return Uint8Array.from(atob(base64), (ch) => ch.charCodeAt(0));
}

/** @param {string | Uint8Array} input */
function base64url(input) {
  const bytes = typeof input === 'string' ? new TextEncoder().encode(input) : input;
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
