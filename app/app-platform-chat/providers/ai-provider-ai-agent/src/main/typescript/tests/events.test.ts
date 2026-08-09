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
      key: "issue-create",
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
  assert.equal(event?.ext.toolKey, "issue-create");
  assert.equal(event?.ext.toolCode, "issue-create");
  assert.equal(event?.ext.toolName, "Issue Create");
  assert.equal(event?.ext.toolVersion, 4);
});

test("maps a built-in Tool key to its Chinese display name", () => {
  const event = mapSdkStreamEvent(
    graph,
    {
      type: "run_item_stream_event",
      name: "tool_called",
      item: { rawItem: { name: "load_skill_resource", callId: "call-skill" } },
    },
    () => undefined,
  );

  assert.equal(event?.ext.toolKey, "load_skill_resource");
  assert.equal(event?.ext.toolName, "读取技能资源");
  assert.equal(event?.ext.activityName, "调用工具：读取技能资源");
  assert.equal(event?.ext.callReason, "需要读取已选技能的执行规范和资源内容。");
});

test("explains why an authorized knowledge base search is needed", () => {
  const event = mapSdkStreamEvent(
    graph,
    {
      type: "run_item_stream_event",
      name: "tool_called",
      item: {
        rawItem: {
          name: "knowledge_base_search_tool",
          callId: "call-kb",
          arguments: '{"kb_code":"data-semantic-catalog","query":"用户地址字段"}',
        },
      },
    },
    () => undefined,
  );

  assert.equal(event?.ext.toolName, "检索知识库");
  assert.equal(event?.ext.callReason, "当前任务需要补充已授权知识库中的业务语义和事实依据。");
  assert.equal(event?.ext.inputSummary, '{"kb_code":"data-semantic-catalog","query":"用户地址字段"}');
});

test("maps a collaboration Tool key to the target Agent name", () => {
  const event = mapSdkStreamEvent(
    graph,
    {
      type: "run_item_stream_event",
      name: "tool_called",
      item: { rawItem: { name: "ask_dashboard_application_builder", callId: "call-agent" } },
    },
    () => undefined,
    (name) => name === "ask_dashboard_application_builder" ? {
      key: name,
      code: name,
      sdkName: name,
      name: "看板与应用构建 Agent",
    } : undefined,
  );

  assert.equal(event?.ext.toolKey, "ask_dashboard_application_builder");
  assert.equal(event?.ext.toolName, "看板与应用构建 Agent");
  assert.equal(event?.ext.activityName, "调用工具：看板与应用构建 Agent");
  assert.equal(event?.ext.callReason, "当前任务需要“看板与应用构建 Agent”的专业能力，因此发起协作。");
});

test("keeps one tool activity identity and returns useful input/output summaries", () => {
  const started = mapSdkStreamEvent(
    graph,
    {
      type: "run_item_stream_event",
      name: "tool_called",
      item: { rawItem: { name: "validator", arguments: '{"value":1}', callId: "call-3" } },
    },
    () => undefined,
  );
  const completed = mapSdkStreamEvent(
    graph,
    {
      type: "run_item_stream_event",
      name: "tool_output",
      item: { rawItem: { type: "function_call_output", callId: "call-3" }, output: { success: true, count: 3 } },
    },
    () => undefined,
  );

  assert.equal(started?.ext.activityCode, "call-3");
  assert.equal(completed?.ext.activityCode, "call-3");
  assert.equal(started?.ext.inputSummary, '{"value":1}');
  assert.equal(completed?.ext.outputSummary, '{"success":true,"count":3}');
  assert.equal(started?.ext.callReason, "当前步骤需要通过“validator”补充、验证或执行任务所需信息。");
});
