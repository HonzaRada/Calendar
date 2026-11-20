#!/usr/bin/env python3
"""Private calendar sync service using the Python standard library."""

import argparse
import hmac
import json
import os
import re
import sqlite3
import ssl
from contextlib import closing
from datetime import date
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = re.compile(r"[A-Za-z0-9_-]{32,128}")
RECORD_ID = re.compile(r"(note|event|calendar)/[A-Za-z0-9_-]{1,50}")
MUTATION = re.compile(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
COLORS = {"PRIMARY", "SECONDARY", "TERTIARY", "ERROR"}
REPEAT_RULES = {"NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"}
REMINDER_OFFSETS = {0, 5, 10, 15, 30, 60, 1440}
MAX_BODY = 8 * 1024 * 1024
PAGE = 128


def initialize(database):
    with closing(sqlite3.connect(database)) as connection, connection:
        connection.executescript("""
            PRAGMA journal_mode = WAL;
            CREATE TABLE IF NOT EXISTS records (
                id TEXT PRIMARY KEY,
                revision INTEGER UNIQUE NOT NULL,
                value TEXT
            );
            CREATE TABLE IF NOT EXISTS applied (mutation TEXT PRIMARY KEY);
            CREATE TABLE IF NOT EXISTS head (id INTEGER PRIMARY KEY CHECK (id = 1), revision INTEGER NOT NULL);
            INSERT OR IGNORE INTO head VALUES (1, 0);
        """)


def valid_record(record_id, value):
    if not isinstance(record_id, str) or not RECORD_ID.fullmatch(record_id):
        return False
    kind, key = record_id.split("/")
    try:
        if kind == "note":
            date.fromisoformat(key)
        if value is None:
            return True
        if not isinstance(value, dict):
            return False
        if kind == "note":
            return isinstance(value["text"], str)
        if kind == "calendar":
            custom = value.get("customColor")
            return (
                (value["name"] is None or isinstance(value["name"], str)) and value["color"] in COLORS
                and (custom is None or type(custom) is int and 0 <= custom <= 0xFFFFFF)
            )
        date.fromisoformat(value["date"])
        reminders = value.get("reminders", [])
        zone = value.get("zone")
        return (
            type(value["start"]) is int and type(value["end"]) is int
            and 0 <= value["start"] < value["end"] <= 1440
            and isinstance(value["title"], str)
            and isinstance(value["group"], str)
            and isinstance(value.get("sticker", ""), str)
            and value.get("repeat", "NONE") in REPEAT_RULES
            and isinstance(reminders, list) and len(reminders) <= len(REMINDER_OFFSETS)
            and all(type(lead) is int and lead in REMINDER_OFFSETS for lead in reminders)
            and (zone is None or isinstance(zone, str) and len(zone) <= 128)
        )
    except (KeyError, TypeError, ValueError):
        return False


def synchronize(database, cursor, changes):
    with closing(sqlite3.connect(database, timeout=10)) as connection, connection:
        connection.execute("BEGIN IMMEDIATE")
        head = connection.execute("SELECT revision FROM head WHERE id = 1").fetchone()[0]
        if cursor > head:
            return 409, {"error": "cursor_ahead"}
        for change in changes:
            first_delivery = connection.execute(
                "INSERT OR IGNORE INTO applied (mutation) VALUES (?)", (change["mutation"],)
            )
            if first_delivery.rowcount == 0:
                continue
            value = None if change["value"] is None else json.dumps(
                change["value"], ensure_ascii=False, separators=(",", ":"), sort_keys=True
            )
            head += 1
            connection.execute(
                "INSERT INTO records VALUES (?, ?, ?) "
                "ON CONFLICT(id) DO UPDATE SET revision=excluded.revision, value=excluded.value",
                (change["id"], head, value),
            )
        connection.execute("UPDATE head SET revision = ? WHERE id = 1", (head,))
        rows = connection.execute(
            "SELECT id, revision, value FROM records WHERE revision > ? ORDER BY revision LIMIT ?",
            (cursor, PAGE + 1),
        ).fetchall()
        more = len(rows) > PAGE
        rows = rows[:PAGE]
        return 200, {
            "cursor": rows[-1][1] if more else head,
            "more": more,
            "changes": [{"id": record_id, "value": json.loads(value) if value is not None else None}
                        for record_id, _, value in rows],
        }


class CalendarHandler(BaseHTTPRequestHandler):
    timeout = 15

    def do_POST(self):
        if self.path != "/v1/sync":
            return self.respond(404, {"error": "not_found"})
        authorization = self.headers.get("Authorization", "").encode()
        if not hmac.compare_digest(authorization, self.server.authorization):
            return self.respond(401, {"error": "unauthorized"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= MAX_BODY:
                return self.respond(413, {"error": "invalid_size"})
            body = json.loads(self.rfile.read(length))
            cursor = body["cursor"]
            changes = body["changes"]
            if type(cursor) is not int or not 0 <= cursor <= 2**63 - 1:
                raise ValueError
            if not isinstance(changes, list) or len(changes) > 64:
                raise ValueError
            for change in changes:
                if not MUTATION.fullmatch(change["mutation"]) or not valid_record(change["id"], change["value"]):
                    raise ValueError
        except (KeyError, TypeError, ValueError, OSError, RecursionError):
            return self.respond(400, {"error": "invalid_request"})
        try:
            status, result = synchronize(self.server.database, cursor, changes)
        except (sqlite3.Error, UnicodeError):
            return self.respond(503, {"error": "storage_unavailable"})
        self.respond(status, result)

    def respond(self, status, data):
        body = json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, _format, *args):
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--database", default="calendar.sqlite3")
    parser.add_argument("--cert", help="TLS certificate chain in PEM format")
    parser.add_argument("--key", help="TLS private key in PEM format")
    args = parser.parse_args()
    token = os.environ.get("CALENDAR_SYNC_TOKEN", "")
    if not TOKEN.fullmatch(token):
        parser.error("Set CALENDAR_SYNC_TOKEN to a secret of 32–128 URL-safe characters.")
    if bool(args.cert) != bool(args.key):
        parser.error("--cert and --key must be supplied together.")
    if not args.cert and args.bind not in ("127.0.0.1", "localhost"):
        parser.error("TLS is required when binding a non-loopback interface.")
    os.umask(0o077)
    initialize(args.database)
    with ThreadingHTTPServer((args.bind, args.port), CalendarHandler) as server:
        server.database = args.database
        server.authorization = f"Bearer {token}".encode()
        if args.cert:
            tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            tls.load_cert_chain(args.cert, args.key)
            server.socket = tls.wrap_socket(server.socket, server_side=True)
        print(f"Calendar sync listening on {args.bind}:{args.port}", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
