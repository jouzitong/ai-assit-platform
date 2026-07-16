import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

import { compileSnapshot } from "../src/compiler.js";
import { JsonRecord } from "../src/contracts.js";
import { mapSdkStreamEvent, platformEvent } from "../src/events.js";
import { artifactList } from "../src/runtime.js";

const fixture = JSON.parse(
  readFileSync(resolve(process.cwd(), "../../test/fixtures/agent-runtime-v2.json"), "utf8"),
) as JsonRecord;
const graph = compileSnapshot(fixture);

test("maps text deltas to the platform event contract", () => {
  const event = mapSdkStreamEvent(
    graph,
    { type: "raw_model_stream_event", data: { type: "output_text_delta", delta: "hello" } },
    () => undefined,
  );
  assert.equal(event?.eventType, "assistant.message.delta");
  assert.equal(event?.delta, "hello");
  assert.equal(event?.runId, "run-fixture-1");
});

test("builds a normalized handoff event", () => {
  const event = platformEvent(graph, "handoff.completed", { status: "SUCCESS" });
  assert.equal(event.type, "event");
  assert.equal(event.source, "OPENAI_AGENTS_TYPESCRIPT");
});

test("extracts normalized artifacts from JSON string output", () => {
  const finalOutput = readFileSync(
    resolve(process.cwd(), "../../test/fixtures/agent-runtime-artifact-result.json"),
    "utf8",
  );
  const artifacts = artifactList(finalOutput);
  assert.equal(artifacts.length, 1);
  assert.equal(artifacts[0]?.artifactCode, "render-document");
  assert.equal(artifacts[0]?.artifactType, "RENDER_JSON");
  assert.equal(artifacts[0]?.contentFormat, "JSON");
  assert.equal((artifacts[0]?.content as JsonRecord).component, "Text");
});

test("extracts artifacts from one complete markdown JSON fence", () => {
  const artifacts = artifactList('```json\n{"artifacts":[{"code":"checked","content":{"ok":true}}]}\n```');
  assert.equal(artifacts[0]?.artifactCode, "checked");
  assert.equal(artifacts[0]?.contentFormat, "JSON");
});

test("normalizes string-encoded failed tool output and keeps Agent identity", () => {
  const reviewer = graph.agents.find((agent) => agent.code === "requirement-reviewer")!;
  const sdkAgent = {};
  const event = mapSdkStreamEvent(
    graph,
    {
      type: "run_item_stream_event",
      name: "tool_output",
      agent: sdkAgent,
      item: { output: '{"success":false,"error":"bad"}', rawItem: { name: "validator", callId: "call-1" } },
    },
    (candidate) => candidate === sdkAgent ? reviewer : undefined,
  );
  assert.equal(event?.eventType, "tool.failed");
  assert.equal(event?.agentCode, "requirement-reviewer");
});

test("maps a Gateway SDK name back to versioned platform Tool identity", () => {
  const event = mapSdkStreamEvent(
    graph,
    {
      type: "run_item_stream_event",
      name: "tool_output",
      item: { output: { status: "SUCCESS" }, rawItem: { name: "gateway_issue_create_v4", callId: "call-2" } },
    },
    () => undefined,
    (name) => name === "gateway_issue_create_v4" ? {
      code: "issue-create",
      version: 4,
      sdkName: name,
      name: "Issue Create",
      description: "Create issue",
      adapterType: "JAVA_INTERNAL",
      inputSchema: { type: "object" },
      timeoutMs: 5000,
    } : undefined,
  );
  assert.equal(event?.ext.toolCode, "issue-create");
  assert.equal(event?.ext.toolVersion, 4);
});
