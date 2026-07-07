// End-to-end smoke test against the configured MongoDB. Uses a throwaway DB name and drops it after.
// Run: node scripts/smoke.mjs   (expects MONGODB_URI/JWT_SECRET/ENCRYPTION_KEY in env)
import { createRequire } from "module";
const require = createRequire(import.meta.url);

// Force a disposable database so we never touch real data.
process.env.MONGODB_DB = "pdfvault_smoketest";

const auth = require("../dist/handlers/auth.js");
const accounts = require("../dist/handlers/accounts.js");
const recents = require("../dist/handlers/recents.js");
const { MongoClient } = require("mongodb");

const ctx = { callbackWaitsForEmptyEventLoop: true };

function event({ method, path, body, token, query }) {
  return {
    requestContext: { http: { method, path } },
    rawPath: path,
    headers: token ? { authorization: `Bearer ${token}` } : {},
    queryStringParameters: query || null,
    pathParameters: null,
    isBase64Encoded: false,
    body: body ? JSON.stringify(body) : undefined,
  };
}

function withId(e, id) {
  return { ...e, pathParameters: { id } };
}

const results = [];
let failures = 0;
function check(name, cond, detail = "") {
  results.push(`${cond ? "PASS" : "FAIL"}  ${name}${detail ? "  — " + detail : ""}`);
  if (!cond) failures++;
}

async function call(handler, e) {
  const res = await handler.handler(e, ctx);
  let json = {};
  try { json = res.body ? JSON.parse(res.body) : {}; } catch { /* 204 */ }
  return { status: res.statusCode, json };
}

async function main() {
  const email = `smoke_${Date.now()}@example.com`;
  const password = "hunter2secret";

  // Auth
  const reg = await call(auth, event({ method: "POST", path: "/auth/register", body: { email, password } }));
  check("register returns 201 + token", reg.status === 201 && !!reg.json.token, `status=${reg.status}`);
  const login = await call(auth, event({ method: "POST", path: "/auth/login", body: { email, password } }));
  check("login returns token", login.status === 200 && !!login.json.token);
  const badLogin = await call(auth, event({ method: "POST", path: "/auth/login", body: { email, password: "wrong-password" } }));
  check("bad login -> 401", badLogin.status === 401);
  const dupe = await call(auth, event({ method: "POST", path: "/auth/register", body: { email, password } }));
  check("duplicate email -> 409", dupe.status === 409);
  const token = login.json.token;
  const me = await call(auth, event({ method: "GET", path: "/me", token }));
  check("me returns email", me.status === 200 && me.json.email === email);
  const noAuth = await call(accounts, event({ method: "GET", path: "/accounts" }));
  check("accounts without token -> 401", noAuth.status === 401);

  // Multi-account: create two, verify secret round-trips and active flips
  const acc1 = await call(accounts, event({
    method: "POST", path: "/accounts", token,
    body: { name: "Personal", region: "us-east-1", bucket: "b1", accessKeyId: "AKIA1", secretAccessKey: "topsecret1" },
  }));
  check("create account 1 -> 201, active", acc1.status === 201 && acc1.json.active === true);
  check("secret decrypts back", acc1.json.secretAccessKey === "topsecret1", `got=${acc1.json.secretAccessKey}`);
  const acc2 = await call(accounts, event({
    method: "POST", path: "/accounts", token,
    body: { name: "Work", region: "eu-west-1", bucket: "b2", accessKeyId: "AKIA2", secretAccessKey: "topsecret2", active: true },
  }));
  check("create account 2 active:true", acc2.status === 201 && acc2.json.active === true);
  const listed = await call(accounts, event({ method: "GET", path: "/accounts", token }));
  const actives = (listed.json.accounts || []).filter((a) => a.active);
  check("exactly one active account", actives.length === 1 && actives[0].name === "Work", `actives=${actives.length}`);
  const del = await call(accounts, withId(event({ method: "DELETE", path: `/accounts/${acc2.json.id}`, token }), acc2.json.id));
  check("delete active account -> 204", del.status === 204);
  const listed2 = await call(accounts, event({ method: "GET", path: "/accounts", token }));
  check("deleting active promotes another", (listed2.json.accounts || []).some((a) => a.active), "");

  // Recents sync
  const now = Date.now();
  await call(recents, event({ method: "PUT", path: "/recents", token, body: { docId: "docs/a.pdf", name: "a.pdf", openedAt: now, totalPages: 10, lastPage: 3, updatedAt: now } }));
  // "Mobile" pushes a newer progress for a.pdf, plus a new b.pdf. "Desktop" older copy of a.pdf must NOT win.
  const sync = await call(recents, event({
    method: "POST", path: "/recents/sync", token,
    body: { items: [
      { docId: "docs/a.pdf", name: "a.pdf", openedAt: now + 1000, totalPages: 10, lastPage: 7, updatedAt: now + 1000 },
      { docId: "docs/a.pdf", name: "a.pdf", openedAt: now - 5000, totalPages: 10, lastPage: 1, updatedAt: now - 5000 },
      { docId: "b.pdf", name: "b.pdf", openedAt: now + 2000, totalPages: 5, lastPage: 2, updatedAt: now + 2000 },
    ] },
  }));
  const merged = sync.json.recents || [];
  const a = merged.find((r) => r.docId === "docs/a.pdf");
  const b = merged.find((r) => r.docId === "b.pdf");
  check("sync merges to 2 docs", merged.length === 2, `count=${merged.length}`);
  check("last-write-wins keeps newer progress (lastPage=7)", a && a.lastPage === 7, `lastPage=${a && a.lastPage}`);
  check("new doc synced in", !!b && b.lastPage === 2);
  check("sorted newest-first", merged.length === 2 && merged[0].docId === "b.pdf");
  const delRecent = await call(recents, event({ method: "DELETE", path: "/recents", token, query: { docId: "docs/a.pdf" } }));
  check("delete recent -> 204", delRecent.status === 204);
  const afterDel = await call(recents, event({ method: "GET", path: "/recents", token }));
  check("recent removed", (afterDel.json.recents || []).length === 1);

  // Tombstone semantics: another device syncing its STALE copy must not resurrect the deleted doc...
  const staleSync = await call(recents, event({
    method: "POST", path: "/recents/sync", token,
    body: { items: [
      { docId: "docs/a.pdf", name: "a.pdf", openedAt: now + 1000, totalPages: 10, lastPage: 7, updatedAt: now + 1000 },
    ] },
  }));
  const staleMerged = staleSync.json.recents || [];
  check("stale sync can't resurrect a deleted recent", !staleMerged.some((r) => r.docId === "docs/a.pdf"), `count=${staleMerged.length}`);
  // ...but genuinely RE-OPENING the doc (a strictly newer write) revives it everywhere.
  const reopenAt = Date.now() + 60_000;
  await call(recents, event({ method: "PUT", path: "/recents", token, body: { docId: "docs/a.pdf", name: "a.pdf", openedAt: reopenAt, totalPages: 10, lastPage: 8, updatedAt: reopenAt } }));
  const afterReopen = await call(recents, event({ method: "GET", path: "/recents", token }));
  check("re-opening revives a deleted recent", (afterReopen.json.recents || []).some((r) => r.docId === "docs/a.pdf"));

  console.log("\n" + results.join("\n"));
}

async function cleanup() {
  const client = new MongoClient(process.env.MONGODB_URI, { serverSelectionTimeoutMS: 8000 });
  await client.connect();
  await client.db("pdfvault_smoketest").dropDatabase();
  await client.close();
}

main()
  .then(cleanup)
  .then(() => {
    console.log(`\n${failures === 0 ? "ALL PASSED" : failures + " FAILED"}`);
    process.exit(failures === 0 ? 0 : 1);
  })
  .catch(async (err) => {
    console.error("SMOKE TEST ERROR:", err.message);
    try { await cleanup(); } catch { /* ignore */ }
    process.exit(2);
  });
