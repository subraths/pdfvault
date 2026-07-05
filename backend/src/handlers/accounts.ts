import { APIGatewayProxyEventV2 } from "aws-lambda";
import { ObjectId } from "mongodb";
import { profiles } from "../lib/db";
import { requireAuth } from "../lib/auth";
import { decrypt, encrypt } from "../lib/crypto";
import { HttpError, created, method, noContent, ok, parseBody, wrap } from "../lib/http";
import { requireString } from "../lib/validation";
import { ProfileDoc, ProfileView } from "../models/types";

function toView(p: ProfileDoc): ProfileView {
  return {
    id: p._id.toHexString(),
    name: p.name,
    region: p.region,
    bucket: p.bucket,
    accessKeyId: p.accessKeyId,
    secretAccessKey: decrypt(p.secretEnc),
    active: p.active,
    updatedAt: p.updatedAt.getTime(),
  };
}

function pathId(event: APIGatewayProxyEventV2): ObjectId {
  const id = event.pathParameters?.id;
  if (!id || !ObjectId.isValid(id)) throw new HttpError(400, "Invalid account id");
  return new ObjectId(id);
}

async function list(userId: ObjectId) {
  const col = await profiles();
  const docs = await col.find({ userId }).sort({ createdAt: 1 }).toArray();
  return ok({ accounts: docs.map(toView) });
}

async function create(event: APIGatewayProxyEventV2, userId: ObjectId) {
  const body = parseBody(event);
  const name = requireString(body.name, "name", 200);
  const region = requireString(body.region, "region", 64);
  const bucket = requireString(body.bucket, "bucket", 256);
  const accessKeyId = requireString(body.accessKeyId, "accessKeyId", 256);
  const secretAccessKey = requireString(body.secretAccessKey, "secretAccessKey", 512);

  const col = await profiles();
  const now = new Date();
  // First account is active by default; honour an explicit request too.
  const makeActive = body.active === true || (await col.countDocuments({ userId })) === 0;
  if (makeActive) await col.updateMany({ userId }, { $set: { active: false, updatedAt: now } });

  const doc: ProfileDoc = {
    _id: new ObjectId(),
    userId,
    name,
    region,
    bucket,
    accessKeyId,
    secretEnc: encrypt(secretAccessKey),
    active: makeActive,
    createdAt: now,
    updatedAt: now,
  };
  await col.insertOne(doc);
  return created(toView(doc));
}

async function update(event: APIGatewayProxyEventV2, userId: ObjectId) {
  const id = pathId(event);
  const body = parseBody(event);
  const col = await profiles();
  const existing = await col.findOne({ _id: id, userId });
  if (!existing) throw new HttpError(404, "Account not found");

  const now = new Date();
  const set: Partial<ProfileDoc> = { updatedAt: now };
  if (typeof body.name === "string" && body.name.trim()) set.name = body.name.trim();
  if (body.active === true) {
    await col.updateMany({ userId }, { $set: { active: false, updatedAt: now } });
    set.active = true;
  }
  await col.updateOne({ _id: id, userId }, { $set: set });
  const updated = await col.findOne({ _id: id, userId });
  return ok(toView(updated!));
}

async function remove(event: APIGatewayProxyEventV2, userId: ObjectId) {
  const id = pathId(event);
  const col = await profiles();
  const res = await col.deleteOne({ _id: id, userId });
  if (res.deletedCount === 0) throw new HttpError(404, "Account not found");

  // If the active account was removed, promote the newest remaining one.
  if (!(await col.findOne({ userId, active: true }))) {
    const next = await col.find({ userId }).sort({ createdAt: -1 }).limit(1).next();
    if (next) await col.updateOne({ _id: next._id }, { $set: { active: true, updatedAt: new Date() } });
  }
  return noContent();
}

export const handler = wrap(async (event) => {
  const userId = requireAuth(event);
  const m = method(event);
  const hasId = Boolean(event.pathParameters?.id);
  if (m === "GET" && !hasId) return list(userId);
  if (m === "POST" && !hasId) return create(event, userId);
  if (m === "PATCH" && hasId) return update(event, userId);
  if (m === "DELETE" && hasId) return remove(event, userId);
  throw new HttpError(404, "Not found");
});
