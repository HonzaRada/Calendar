import { DurableObject } from "cloudflare:workers";

const TOKEN = /^[A-Za-z0-9_-]{32,128}$/;
const RECORD_ID = /^(note|event|calendar)\/([A-Za-z0-9_-]{1,50})$/;
const MUTATION = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const COLORS = new Set(["PRIMARY", "SECONDARY", "TERTIARY", "ERROR"]);
const REPEAT_RULES = new Set(["NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"]);
const REMINDER_OFFSETS = new Set([0, 5, 10, 15, 30, 60, 1440]);
const MAX_BODY = 8 * 1024 * 1024;
const MAX_VALUE = 8_000_000 - 1024; // Leave room for the other columns within the 8 MB SQLite row limit.
const PAGE_SIZE = 128;
const encoder = new TextEncoder();

function json(status, body) {
  return Response.json(body, { status, headers: { "Cache-Control": "no-store" } });
}

function isObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function validDate(value) {
  if (typeof value !== "string" || value.length !== 10 || !/^\d{4}-\d{2}-\d{2}$/.test(value) ||
      value.startsWith("0000")) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().startsWith(`${value}T`);
}

function validRecord(id, value) {
  const parsed = typeof id === "string" && RECORD_ID.exec(id);
  if (!parsed || parsed[0] !== id) return false;
  const [, kind, key] = parsed;
  if (kind === "note" && !validDate(key)) return false;
  if (value === null) return true;
  if (!isObject(value)) return false;

  if (kind === "note") return typeof value.text === "string";
  if (kind === "calendar") {
    const { name, color, customColor = null } = value;
    return (name === null || typeof name === "string") && COLORS.has(color) &&
      (customColor === null || (Number.isInteger(customColor) && customColor >= 0 && customColor <= 0xFFFFFF));
  }

  const { date, start, end, title, group, sticker = "", repeat = "NONE", reminders = [], zone = null } = value;
  return validDate(date) && Number.isInteger(start) && Number.isInteger(end) &&
    start >= 0 && start < end && end <= 1440 &&
    typeof title === "string" && typeof group === "string" && typeof sticker === "string" &&
    REPEAT_RULES.has(repeat) && Array.isArray(reminders) && reminders.length <= REMINDER_OFFSETS.size &&
    reminders.every((lead) => REMINDER_OFFSETS.has(lead)) &&
    (zone === null || (typeof zone === "string" && zone.length <= 128));
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname !== "/v1/sync" || url.search || request.method !== "POST") {
      return json(404, { error: "not_found" });
    }
    const secret = env.CALENDAR_SYNC_TOKEN;
    if (typeof secret !== "string" || secret.trim() !== secret || !TOKEN.test(secret)) {
      return json(503, { error: "storage_unavailable" });
    }
    const expected = encoder.encode(`Bearer ${secret}`);
    const supplied = encoder.encode(request.headers.get("Authorization") ?? "");
    if (supplied.byteLength !== expected.byteLength || !crypto.subtle.timingSafeEqual(supplied, expected)) {
      return json(401, { error: "unauthorized" });
    }

    const length = Number(request.headers.get("Content-Length"));
    if (!Number.isInteger(length) || length < 1 || length > MAX_BODY) {
      return json(413, { error: "invalid_size" });
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json(400, { error: "invalid_request" });
    }
    if (!isObject(body) || !Number.isSafeInteger(body.cursor) || body.cursor < 0 ||
        !Array.isArray(body.changes) || body.changes.length > 64) {
      return json(400, { error: "invalid_request" });
    }

    const changes = [];
    for (const change of body.changes) {
      if (!isObject(change) || typeof change.mutation !== "string" || change.mutation.length !== 36 ||
          !MUTATION.test(change.mutation) ||
          !validRecord(change.id, change.value)) {
        return json(400, { error: "invalid_request" });
      }
      const value = change.value === null ? null : JSON.stringify(change.value);
      if (value !== null && encoder.encode(value).byteLength > MAX_VALUE) {
        return json(413, { error: "record_too_large" });
      }
      changes.push({ id: change.id, mutation: change.mutation, value });
    }

    try {
      const store = env.CALENDAR.getByName("calendar");
      const page = await store.sync(body.cursor, changes);
      if (page === null) return json(409, { error: "cursor_ahead" });
      return json(200, page);
    } catch {
      return json(503, { error: "storage_unavailable" });
    }
  },
};

export class CalendarStore extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS records (
        id TEXT PRIMARY KEY,
        revision INTEGER UNIQUE NOT NULL,
        value TEXT
      );
      CREATE TABLE IF NOT EXISTS applied (mutation TEXT PRIMARY KEY);
      CREATE TABLE IF NOT EXISTS head (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        revision INTEGER NOT NULL
      );
      INSERT OR IGNORE INTO head VALUES (1, 0);
    `);
  }

  sync(cursor, changes) {
    return this.ctx.storage.transactionSync(() => {
      const previous = this.sql.exec("SELECT revision FROM head WHERE id = 1").one().revision;
      if (cursor > previous) return null;

      let head = previous;
      for (const change of changes) {
        const firstDelivery = this.sql.exec(
          "INSERT OR IGNORE INTO applied (mutation) VALUES (?) RETURNING mutation",
          change.mutation,
        ).toArray();
        if (firstDelivery.length === 0) continue;

        head += 1;
        this.sql.exec(
          `INSERT INTO records (id, revision, value) VALUES (?, ?, ?)
           ON CONFLICT(id) DO UPDATE SET revision = excluded.revision, value = excluded.value`,
          change.id, head, change.value,
        );
      }
      if (head !== previous) this.sql.exec("UPDATE head SET revision = ? WHERE id = 1", head);

      let more = false;
      let bytes = 0;
      let lastRevision = cursor;
      const page = [];
      for (const record of this.sql.exec(
        "SELECT id, revision, value FROM records WHERE revision > ? ORDER BY revision LIMIT ?",
        cursor, PAGE_SIZE + 1,
      )) {
        const recordBytes = (record.value === null ? 0 : encoder.encode(record.value).byteLength) + 100;
        if (page.length === PAGE_SIZE || bytes + recordBytes > MAX_BODY) {
          more = true;
          break;
        }
        bytes += recordBytes;
        lastRevision = record.revision;
        page.push({ id: record.id, value: record.value === null ? null : JSON.parse(record.value) });
      }
      return { cursor: more ? lastRevision : head, more, changes: page };
    });
  }
}
