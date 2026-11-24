// @ts-check
import { generateKeyPairSync } from 'node:crypto';
import { readFileSync } from 'node:fs';

const FIXTURES = new URL('../../fixtures/', import.meta.url);

/** The "r" HTML of a saved substitutions response. @param {string} date */
export function substitutionsHtml(date) {
  return JSON.parse(readFileSync(new URL(`substitutions/${date}.json`, FIXTURES), 'utf8')).r;
}

/** @param {string} path */
export function fixtureJson(path) {
  return JSON.parse(readFileSync(new URL(path, FIXTURES), 'utf8'));
}

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });

/** A throwaway service account; its key signs nothing real. */
export const testAccount = {
  project_id: 'qbitplan-test',
  client_email: 'worker@qbitplan-test.iam.gserviceaccount.com',
  private_key: privateKey.export({ type: 'pkcs8', format: 'pem' }).toString(),
};

export const testPublicKey = publicKey;
