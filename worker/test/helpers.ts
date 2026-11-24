import { generateKeyPairSync } from 'node:crypto';
import { readFileSync } from 'node:fs';
import type { ServiceAccount } from '../src/fcm.ts';

const FIXTURES = new URL('../../fixtures/', import.meta.url);

/** The "r" HTML of a saved substitutions response. */
export function substitutionsHtml(date: string): string {
  return (JSON.parse(readFileSync(new URL(`substitutions/${date}.json`, FIXTURES), 'utf8')) as { r: string }).r;
}

export function fixtureJson<T>(path: string): T {
  return JSON.parse(readFileSync(new URL(path, FIXTURES), 'utf8')) as T;
}

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });

/** A throwaway service account; its key signs nothing real. */
export const testAccount: ServiceAccount = {
  project_id: 'qbitplan-test',
  client_email: 'worker@qbitplan-test.iam.gserviceaccount.com',
  private_key: privateKey.export({ type: 'pkcs8', format: 'pem' }).toString(),
};

export const testPublicKey = publicKey;
