import { MongoClient, Db, Collection } from "mongodb";
import { ProfileDoc, RecentDoc, UserDoc } from "../models/types";

// Cached across warm Lambda invocations so we don't reconnect on every request.
let client: MongoClient | null = null;
let dbPromise: Promise<Db> | null = null;
let indexesEnsured = false;

async function connect(): Promise<Db> {
  const uri = process.env.MONGODB_URI;
  if (!uri) throw new Error("MONGODB_URI is not set");
  client = new MongoClient(uri, { maxPoolSize: 5, serverSelectionTimeoutMS: 8000 });
  await client.connect();
  const db = client.db(process.env.MONGODB_DB || "pdfvault");
  if (!indexesEnsured) {
    await ensureIndexes(db);
    indexesEnsured = true;
  }
  return db;
}

/** Returns the shared Db, connecting on first use and reusing the connection thereafter. */
export function getDb(): Promise<Db> {
  if (!dbPromise) {
    dbPromise = connect().catch((err) => {
      // Reset so the next invocation retries instead of caching a failed connection.
      dbPromise = null;
      throw err;
    });
  }
  return dbPromise;
}

export async function users(): Promise<Collection<UserDoc>> {
  return (await getDb()).collection<UserDoc>("users");
}

export async function profiles(): Promise<Collection<ProfileDoc>> {
  return (await getDb()).collection<ProfileDoc>("profiles");
}

export async function recents(): Promise<Collection<RecentDoc>> {
  return (await getDb()).collection<RecentDoc>("recents");
}

async function ensureIndexes(db: Db): Promise<void> {
  await db.collection("users").createIndex({ email: 1 }, { unique: true });
  await db.collection("profiles").createIndex({ userId: 1 });
  await db.collection("recents").createIndex({ userId: 1, docId: 1 }, { unique: true });
  await db.collection("recents").createIndex({ userId: 1, openedAt: -1 });
}
