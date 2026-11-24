// @ts-check
import assert from 'node:assert/strict';
import { createVerify } from 'node:crypto';
import { test } from 'node:test';
import { accessToken, clearTokenCache, parseServiceAccount, sendToTopics, signJwt } from '../src/fcm.js';
import { testAccount, testPublicKey as publicKey } from './helpers.js';

/** @param {string} part */
const decode = (part) => JSON.parse(Buffer.from(part, 'base64url').toString());

test('the JWT is RS256-signed with the service account key', async () => {
  const jwt = await signJwt(testAccount, 1_800_000_000);
  const [header, claims, signature] = jwt.split('.');
  assert.deepEqual(decode(header), { alg: 'RS256', typ: 'JWT' });
  assert.equal(decode(claims).iss, testAccount.client_email);
  assert.equal(decode(claims).exp - decode(claims).iat, 3600);
  const verify = createVerify('RSA-SHA256');
  verify.update(`${header}.${claims}`);
  assert.ok(verify.verify(publicKey, Buffer.from(signature, 'base64url')));
});

test('the access token is fetched once and reused', async () => {
  clearTokenCache();
  let calls = 0;
  /** @type {typeof fetch} */
  const fetchImpl = async () => {
    calls++;
    return Response.json({ access_token: 'token-1', expires_in: 3600 });
  };
  assert.equal(await accessToken(testAccount, fetchImpl, 0), 'token-1');
  assert.equal(await accessToken(testAccount, fetchImpl, 60_000), 'token-1');
  assert.equal(calls, 1);
  clearTokenCache();
});

test('one data message for up to five topics', async () => {
  /** @type {any} */
  let sent;
  /** @type {typeof fetch} */
  const fetchImpl = async (url, init) => {
    sent = { url, body: JSON.parse(String(init?.body)), auth: /** @type {any} */ (init?.headers).Authorization };
    return new Response('{}');
  };
  await sendToTopics({ token: 't', projectId: 'p', topics: ['klasa-1at', 'naucz-anna-grzebien'], data: { date: '2026-11-25' }, collapseKey: 'subst-2026-11-25' }, fetchImpl);
  assert.equal(sent.url, 'https://fcm.googleapis.com/v1/projects/p/messages:send');
  assert.equal(sent.auth, 'Bearer t');
  assert.equal(sent.body.message.condition, "'klasa-1at' in topics || 'naucz-anna-grzebien' in topics");
  assert.deepEqual(sent.body.message.data, { date: '2026-11-25' });
  assert.equal(sent.body.message.android.priority, 'HIGH');
  await assert.rejects(sendToTopics({ token: 't', projectId: 'p', topics: ['a', 'b', 'c', 'd', 'e', 'f'], data: {}, collapseKey: 'x' }, fetchImpl));
});

test('the key file parses as pasted, even with real line breaks inside private_key', async () => {
  const file = JSON.stringify({ type: 'service_account', ...testAccount }, null, 2);
  assert.deepEqual(parseServiceAccount(file).client_email, testAccount.client_email);

  // A phone paste can turn the \\n escapes into line breaks: no longer JSON, still usable.
  const mangled = file.replace(/\\n/g, '\n');
  assert.throws(() => JSON.parse(mangled));
  const account = parseServiceAccount(mangled);
  assert.equal(account.project_id, testAccount.project_id);
  const [header, claims, signature] = (await signJwt(account, 1_800_000_000)).split('.');
  const verify = createVerify('RSA-SHA256');
  verify.update(`${header}.${claims}`);
  assert.ok(verify.verify(publicKey, Buffer.from(signature, 'base64url')));
});

test('a wrong secret is named, never echoed', () => {
  assert.throws(() => parseServiceAccount('sk-not-a-key-file'), (error) => {
    assert.ok(error instanceof Error);
    assert.match(error.message, /missing project_id, client_email, private_key/);
    assert.ok(!error.message.includes('sk-not-a-key-file'));
    return true;
  });
  assert.throws(() => parseServiceAccount(JSON.stringify({ ...testAccount, private_key: 'abc' })), /not a PEM key/);
});
