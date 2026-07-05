import { APIGatewayProxyEventV2 } from "aws-lambda";
import { ObjectId } from "mongodb";
import { users } from "../lib/db";
import { hashPassword, requireAuth, signToken, verifyPassword } from "../lib/auth";
import { HttpError, created, method, ok, parseBody, wrap } from "../lib/http";
import { requireEmail, requirePassword } from "../lib/validation";

async function register(event: APIGatewayProxyEventV2) {
  const body = parseBody(event);
  const email = requireEmail(body.email);
  const password = requirePassword(body.password);

  const col = await users();
  const doc = { _id: new ObjectId(), email, passwordHash: await hashPassword(password), createdAt: new Date() };
  try {
    await col.insertOne(doc);
  } catch (err) {
    if ((err as { code?: number }).code === 11000) throw new HttpError(409, "Email already registered");
    throw err;
  }
  return created({ token: signToken(doc._id.toHexString()), user: { id: doc._id.toHexString(), email } });
}

async function login(event: APIGatewayProxyEventV2) {
  const body = parseBody(event);
  const email = requireEmail(body.email);
  const password = requirePassword(body.password);

  const user = await (await users()).findOne({ email });
  if (!user || !(await verifyPassword(password, user.passwordHash))) {
    throw new HttpError(401, "Invalid email or password");
  }
  return ok({ token: signToken(user._id.toHexString()), user: { id: user._id.toHexString(), email: user.email } });
}

async function me(event: APIGatewayProxyEventV2) {
  const userId = requireAuth(event);
  const user = await (await users()).findOne({ _id: userId });
  if (!user) throw new HttpError(404, "User not found");
  return ok({ id: user._id.toHexString(), email: user.email });
}

export const handler = wrap(async (event) => {
  const path = event.requestContext.http.path;
  const m = method(event);
  if (m === "POST" && path.endsWith("/auth/register")) return register(event);
  if (m === "POST" && path.endsWith("/auth/login")) return login(event);
  if (m === "GET" && path.endsWith("/me")) return me(event);
  throw new HttpError(404, "Not found");
});
