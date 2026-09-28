import assert from "node:assert/strict";
import test from "node:test";
import { loadConnectionProfiles, resolveProfile } from "./config.js";
import { MAX_SQL_RESULT_ROWS, validateReadOnlySql } from "./index.js";

test("adds a bounded LIMIT to otherwise valid read-only SQL", () => {
  assert.equal(validateReadOnlySql("SELECT id FROM orders"), `SELECT id FROM orders LIMIT ${MAX_SQL_RESULT_ROWS}`);
  assert.equal(validateReadOnlySql("SELECT id FROM orders LIMIT 999"), `SELECT id FROM orders LIMIT ${MAX_SQL_RESULT_ROWS}`);
  assert.equal(validateReadOnlySql("SELECT id FROM orders LIMIT 5, 999"), `SELECT id FROM orders LIMIT 5, ${MAX_SQL_RESULT_ROWS}`);
});

test("rejects write, locking, and multiple statements", () => {
  for (const sql of [
    "DELETE FROM orders",
    "SELECT * FROM orders FOR UPDATE",
    "SELECT 1; SELECT 2",
  ]) {
    assert.throws(() => validateReadOnlySql(sql));
  }
});

test("routes connections through configured profile names only", () => {
  const profiles = loadConnectionProfiles(
    JSON.stringify({ test: "mysql://readonly:test@db-test/app", gray: "mysql://readonly:gray@db-gray/app" }),
    undefined,
    "test",
    "MySQL",
  );
  assert.equal(resolveProfile(profiles, "gray", "MySQL")[0], "gray");
  assert.throws(() => resolveProfile(profiles, "prod", "MySQL"));
});
