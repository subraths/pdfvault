import { ObjectId } from "mongodb";

/** A registered user. */
export interface UserDoc {
  _id: ObjectId;
  email: string;
  passwordHash: string;
  createdAt: Date;
}

/**
 * An S3 "account" (profile) owned by a user, synced across their devices. The secret access key
 * is stored encrypted at rest ([secretEnc]); it is only ever decrypted for the owning user.
 */
export interface ProfileDoc {
  _id: ObjectId;
  userId: ObjectId;
  name: string;
  region: string;
  bucket: string;
  accessKeyId: string;
  secretEnc: string;
  active: boolean;
  createdAt: Date;
  updatedAt: Date;
}

/** A recently-opened PDF, synced across devices. [docId] is the S3 object key (or "local:<path>"). */
export interface RecentDoc {
  _id: ObjectId;
  userId: ObjectId;
  docId: string;
  name: string;
  openedAt: number;
  totalPages: number;
  lastPage: number;
  updatedAt: number;
  /**
   * Tombstone: deleting keeps the row (flagged, with a fresh updatedAt) instead of removing it,
   * so another device's sync can't resurrect the doc from its stale local copy. Re-opening the
   * doc (a strictly newer write) clears the flag.
   */
  deleted?: boolean;
}

/** Profile shape returned to clients — includes the decrypted secret for the authenticated owner. */
export interface ProfileView {
  id: string;
  name: string;
  region: string;
  bucket: string;
  accessKeyId: string;
  secretAccessKey: string;
  active: boolean;
  updatedAt: number;
}

/** Recent shape exchanged with clients. */
export interface RecentView {
  docId: string;
  name: string;
  openedAt: number;
  totalPages: number;
  lastPage: number;
  updatedAt: number;
}
