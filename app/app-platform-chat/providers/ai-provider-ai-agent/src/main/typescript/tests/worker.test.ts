import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

import { JsonRecord } from "../src/contracts.js";
import { processPayload } from "../src/worker.js";

test("worker dry-run consumes the shared snapshot without loading the SDK", async () => {
  const fixture = JSON.parse(
    readFileSync(resolve(process.cwd(), "../../test/fixtures/agent-runtime-v2.json"), "utf8"),
  ) as JsonRecord;
  const frames = await processPayload(fixture, true);
  assert.equal(frames.length, 1);
  assert.equal((frames[0]?.data as JsonRecord).status, "COMPILED");
  assert.equal((frames[0]?.data as JsonRecord).agentCount, 2);
});

test("production ESM bundle executes as a standalone Node worker", () => {
  const fixture = readFileSync(
    resolve(process.cwd(), "../../test/fixtures/agent-runtime-v2.json"),
    "utf8",
  );
  const result = spawnSync(process.execPath, [resolve(process.cwd(), "dist/worker.mjs")], {
    input: fixture,
    encoding: "utf8",
    timeout: 5000,
    env: {
      ...process.env,
      AI_AGENT_DRY_RUN: "1",
      OPENAI_API_KEY: "test",
    },
  });

  assert.equal(result.status, 0, result.error?.message ?? result.stderr);
  const frame = JSON.parse(result.stdout.trim()) as JsonRecord;
  assert.equal(frame.type, "result");
  assert.equal((frame.data as JsonRecord).status, "COMPILED");
});
