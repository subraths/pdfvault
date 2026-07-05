import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { ObjectId } from "mongodb";
import { APIGatewayProxyEventV2 } from "aws-lambda";
import { HttpError } from "./http";

const BCRYPT_ROUNDS = 10;

export function hashPassword(password: string): Promise<string> {
  return bcrypt.hash(password, BCRYPT_ROUNDS);
}

export function verifyPassword(password: string, hash: string): Promise<boolean> {
  return bcrypt.compare(password, hash);
}

function secret(): string {
  const s = process.env.JWT_SECRET;
  if (!s) throw new Error("JWT_SECRET is not set");
  return s;
}

export function signToken(userId: string): string {
  const ttl = parseInt(process.env.JWT_TTL_SECONDS || "2592000", 10);
  return jwt.sign({ sub: userId }, secret(), { expiresIn: ttl });
}

/** Extracts and verifies the bearer token, returning the caller's user id. Throws 401 otherwise. */
export function requireAuth(event: APIGatewayProxyEventV2): ObjectId {
  const header = event.headers?.authorization || event.headers?.Authorization;
  if (!header || !header.toLowerCase().startsWith("bearer ")) {
    throw new HttpError(401, "Missing bearer token");
  }
  const token = header.slice(7).trim();
  try {
    const payload = jwt.verify(token, secret()) as { sub?: string };
    if (!payload.sub || !ObjectId.isValid(payload.sub)) throw new Error("bad subject");
    return new ObjectId(payload.sub);
  } catch {
    throw new HttpError(401, "Invalid or expired token");
  }
}
