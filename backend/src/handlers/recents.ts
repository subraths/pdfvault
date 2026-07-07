import { APIGatewayProxyEventV2 } from "aws-lambda";
import { Collection, ObjectId } from "mongodb";
import { recents } from "../lib/db";
import { requireAuth } from "../lib/auth";
import { HttpError, method, noContent, ok, parseBody, wrap } from "../lib/http";
import { optionalInt, optionalNumber, requireString } from "../lib/validation";
import { RecentDoc, RecentView } from "../models/types";

function toView(r: RecentDoc): RecentView {
  return { docId: r.docId, name: r.name, openedAt: r.openedAt, totalPages: r.totalPages, lastPage: r.lastPage, updatedAt: r.updatedAt };
}

async function listView(col: Collection<RecentDoc>, userId: ObjectId) {
  // Tombstoned (deleted) docs stay in the collection to block resurrection, but never leave the API.
  const docs = await col.find({ userId, deleted: { $ne: true } }).sort({ openedAt: -1 }).toArray();
  return docs.map(toView);
}

async function list(userId: ObjectId) {
  return ok({ recents: await listView(await recents(), userId) });
}

/** Upserts one recent unconditionally (the caller asserts this is the current state). */
async function upsert(event: APIGatewayProxyEventV2, userId: ObjectId) {
  const body = parseBody(event);
  const docId = requireString(body.docId, "docId", 1024);
  const now = Date.now();
  const fields = {
    name: requireString(body.name ?? docId.split("/").pop() ?? docId, "name", 512),
    openedAt: optionalNumber(body.openedAt, now),
    totalPages: optionalInt(body.totalPages, 0),
    lastPage: optionalInt(body.lastPage, 0),
    updatedAt: optionalNumber(body.updatedAt, now),
  };
  const col = await recents();
  // An explicit open/progress push always revives a tombstoned doc.
  await col.updateOne(
    { userId, docId },
    { $set: { ...fields, deleted: false }, $setOnInsert: { userId, docId } },
    { upsert: true },
  );
  return ok({ recent: { docId, ...fields } });
}

/**
 * Merges a client's recents into the server's, per-document last-write-wins by [updatedAt], then
 * returns the full merged set — so mobile and desktop converge to the same list + reading progress.
 */
async function sync(event: APIGatewayProxyEventV2, userId: ObjectId) {
  const body = parseBody(event);
  const rawItems = body.items;
  const items = Array.isArray(rawItems) ? rawItems : [];
  const col = await recents();
  const now = Date.now();

  for (const raw of items) {
    const it = (raw ?? {}) as Record<string, unknown>;
    if (typeof it.docId !== "string" || it.docId.length === 0) continue;
    const docId = it.docId;
    const incoming = {
      name: typeof it.name === "string" && it.name ? it.name : (docId.split("/").pop() ?? docId),
      openedAt: optionalNumber(it.openedAt, now),
      totalPages: optionalInt(it.totalPages, 0),
      lastPage: optionalInt(it.lastPage, 0),
      updatedAt: optionalNumber(it.updatedAt, now),
    };
    // Overwrite only when the incoming copy is strictly newer — this also revives a tombstone
    // (the user re-opened the doc after deleting it elsewhere). A stale client copy loses to a
    // newer tombstone, so a delete on one device cannot be resurrected by another's sync.
    await col.updateOne(
      { userId, docId, updatedAt: { $lt: incoming.updatedAt } },
      { $set: { ...incoming, deleted: false } },
    );
    // ...and insert it if the server has never seen this document.
    await col.updateOne({ userId, docId }, { $setOnInsert: { userId, docId, ...incoming } }, { upsert: true });
  }

  return ok({ recents: await listView(col, userId) });
}

async function remove(event: APIGatewayProxyEventV2, userId: ObjectId) {
  const docId = event.queryStringParameters?.docId;
  if (!docId) throw new HttpError(400, "docId query parameter is required");
  // Tombstone instead of hard delete, stamped strictly newer than the doc's last update (client
  // clocks can run ahead of ours) — so other devices' syncs drop the doc instead of re-uploading
  // it. Only a genuinely newer write (re-opening the doc) revives it.
  const col = await recents();
  const existing = await col.findOne({ userId, docId }, { projection: { updatedAt: 1 } });
  const ts = Math.max(Date.now(), (existing?.updatedAt ?? 0) + 1);
  await col.updateOne({ userId, docId }, { $set: { deleted: true, updatedAt: ts } });
  return noContent();
}

export const handler = wrap(async (event) => {
  const userId = requireAuth(event);
  const path = event.requestContext.http.path;
  const m = method(event);
  if (m === "GET") return list(userId);
  if (m === "PUT") return upsert(event, userId);
  if (m === "POST" && path.endsWith("/recents/sync")) return sync(event, userId);
  if (m === "DELETE") return remove(event, userId);
  throw new HttpError(404, "Not found");
});
