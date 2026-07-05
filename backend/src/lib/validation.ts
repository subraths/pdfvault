import { HttpError } from "./http";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function requireEmail(value: unknown): string {
  if (typeof value !== "string" || !EMAIL_RE.test(value.trim())) {
    throw new HttpError(400, "A valid email is required");
  }
  return value.trim().toLowerCase();
}

export function requirePassword(value: unknown): string {
  if (typeof value !== "string" || value.length < 8) {
    throw new HttpError(400, "Password must be at least 8 characters");
  }
  return value;
}

export function requireString(value: unknown, field: string, maxLen = 512): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpError(400, `${field} is required`);
  }
  if (value.length > maxLen) throw new HttpError(400, `${field} is too long`);
  return value.trim();
}

export function optionalInt(value: unknown, fallback: number): number {
  if (typeof value === "number" && Number.isFinite(value)) return Math.trunc(value);
  return fallback;
}

export function optionalNumber(value: unknown, fallback: number): number {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  return fallback;
}
