import { createCipheriv, createDecipheriv, randomBytes } from "crypto";

// AES-256-GCM: output layout is base64(iv[12] | authTag[16] | ciphertext).
const IV_LEN = 12;
const TAG_LEN = 16;

function key(): Buffer {
  const b64 = process.env.ENCRYPTION_KEY;
  if (!b64) throw new Error("ENCRYPTION_KEY is not set");
  const k = Buffer.from(b64, "base64");
  if (k.length !== 32) throw new Error("ENCRYPTION_KEY must be base64 of exactly 32 bytes");
  return k;
}

/** Encrypts [plaintext] to a self-contained base64 token (iv + tag + ciphertext). */
export function encrypt(plaintext: string): string {
  const iv = randomBytes(IV_LEN);
  const cipher = createCipheriv("aes-256-gcm", key(), iv);
  const ct = Buffer.concat([cipher.update(plaintext, "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, ct]).toString("base64");
}

/** Reverses [encrypt]. Throws if the token is malformed or authentication fails. */
export function decrypt(token: string): string {
  const buf = Buffer.from(token, "base64");
  if (buf.length < IV_LEN + TAG_LEN) throw new Error("Ciphertext too short");
  const iv = buf.subarray(0, IV_LEN);
  const tag = buf.subarray(IV_LEN, IV_LEN + TAG_LEN);
  const ct = buf.subarray(IV_LEN + TAG_LEN);
  const decipher = createDecipheriv("aes-256-gcm", key(), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ct), decipher.final()]).toString("utf8");
}
