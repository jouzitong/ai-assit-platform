import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

import { buildApplicationInput, compileSnapshot } from "../src/compiler.js";
import { JsonRecord } from "../src/contracts.js";

const fixturePath = resolve(process.cwd(), "../../test/fixtures/agent-runtime-v2.json");
const fixture = JSON.parse(readFileSync(fixturePath, "utf8")) as JsonRecord;

test("compiles the shared protocol v2 fixture", () => {
  const graph = compileSnapshot(fixture);
  assert.equal(graph.rootAgent, "agent://requirement-manager/v2");
  assert.equal(graph.agents.length, 2);
  assert.equal(graph.maxTurns, 9);
  const root = graph.agents.find((agent) => agent.key === graph.rootAgent)!;
  assert.deepEqual(root.tools, ["render_json_validate_tool"]);
  assert.equal(root.agentTools[0]?.target, "agent://requirement-reviewer/v1");
  assert.equal(root.handoffs[0]?.target, "agent://requirement-reviewer/v1");
  assert.match(root.instructions, /metadata only/);
  assert.doesNotMatch(root.instructions, /Use the template only/);
});

test("accepts the legacy @version alias and keeps canonical /v keys", () => {
  const payload = structuredClone(fixture);
  const root = payload.rootAgent as JsonRecord;
  const spec = root.spec as JsonRecord;
  const collaboration = spec.collaboration as JsonRecord;
  const agentTools = collaboration.agentTools as JsonRecord[];
  agentTools[0]!.targetAgentRef = "agent://requirement-reviewer@1";
  const graph = compileSnapshot(payload);
  assert.equal(graph.agents.find((agent) => agent.key === graph.rootAgent)?.agentTools[0]?.target,
    "agent://requirement-reviewer/v1");
});

test("rejects a skill hash mismatch", () => {
  const payload = structuredClone(fixture);
  const root = payload.rootAgent as JsonRecord;
  const spec = root.spec as JsonRecord;
  const skillRefs = spec.skillRefs as JsonRecord[];
  skillRefs[0]!.contentHash = "sha256:tampered";
  assert.throws(() => compileSnapshot(payload), /content hash/);
});

test("application replay keeps assistant history and sends current user once", () => {
  const replay = buildApplicationInput(
    [
      { role: 2, content: "previous question" },
      { role: 3, content: "previous answer" },
      { role: 2, content: "current question" },
    ],
    "current question",
  );
  assert.ok(replay.some((item) => item.role === "assistant" && item.content === "previous answer"));
  assert.equal(
    replay.filter((item) => item.role === "user" && item.content === "current question").length,
    1,
  );
});

test("application replay excludes system instructions already compiled on the Agent", () => {
  const replay = buildApplicationInput(
    [
      { role: "system", content: "compiled instruction" },
      { role: "user", content: "question" },
    ],
    "question",
  );
  assert.deepEqual(replay, [{ role: "user", content: "question" }]);
});

test("compiles a versioned JAVA_INTERNAL Tool to the Tool Gateway", () => {
  const payload = structuredClone(fixture);
  const root = payload.rootAgent as JsonRecord;
  const spec = root.spec as JsonRecord;
  spec.toolRefs = [{ ref: "tool://issue-create/v4" }];
  const capabilities = payload.resolvedCapabilities as JsonRecord;
  capabilities.tools = [{
    code: "issue-create",
    version: 4,
    adapterType: "FUNCTION",
    definition: {
      inputSchema: {
        type: "object",
        properties: { title: { type: "string" } },
        required: ["title"],
        additionalProperties: false,
      },
      timeoutMs: 5000,
      bindings: [{ bindingType: "JAVA_INTERNAL", endpointRef: "http://service/issues" }],
    },
  }];

  const graph = compileSnapshot(payload);
  const compiledRoot = graph.agents.find((agent) => agent.key === graph.rootAgent)!;
  assert.deepEqual(compiledRoot.tools, ["gateway::issue-create::v4"]);
  assert.equal(graph.gatewayTools["gateway::issue-create::v4"]?.code, "issue-create");
});

test("rejects unsupported MCP bindings fail closed", () => {
  const payload = structuredClone(fixture);
  const root = payload.rootAgent as JsonRecord;
  const spec = root.spec as JsonRecord;
  spec.toolRefs = [{ ref: "tool://remote/v1" }];
  const capabilities = payload.resolvedCapabilities as JsonRecord;
  capabilities.tools = [{
    code: "remote",
    version: 1,
    adapterType: "FUNCTION",
    definition: {
      inputSchema: { type: "object" },
      bindings: [{ bindingType: "MCP", endpointRef: "mcp://remote" }],
    },
  }];

  assert.throws(() => compileSnapshot(payload), /MCP/);
});

test("resolves the backend database Skill snapshot by canonical /v ref", () => {
  const payload = structuredClone(fixture);
  const root = payload.rootAgent as JsonRecord;
  const spec = root.spec as JsonRecord;
  spec.skillRefs = [{ ref: "skill://database-skill/v3", contentHash: "package-checksum" }];
  const capabilities = payload.resolvedCapabilities as JsonRecord;
  capabilities.skills = [{
    code: "database-skill",
    version: 3,
    checksum: "package-checksum",
    manifest: {
      files: [{ path: "SKILL.md", checksum: "file-checksum", mediaType: "text/markdown" }],
    },
  }];

  const graph = compileSnapshot(payload);
  const compiledRoot = graph.agents.find((agent) => agent.key === graph.rootAgent)!;
  assert.deepEqual(compiledRoot.skills.map((skill) => skill.ref), ["skill://database-skill/v3"]);
});

test("adds the frozen JSON Schema output contract to Agent instructions", () => {
  const payload = structuredClone(fixture);
  payload.responseFormat = {
    type: "JSON_SCHEMA",
    schema: { type: "object", required: ["artifacts"] },
  };

  const graph = compileSnapshot(payload);
  const root = graph.agents.find((agent) => agent.key === graph.rootAgent)!;
  assert.match(root.instructions, /strictly valid JSON/);
  assert.match(root.instructions, /artifacts/);
});
