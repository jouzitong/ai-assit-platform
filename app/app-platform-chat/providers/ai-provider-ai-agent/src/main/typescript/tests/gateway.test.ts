import assert from "node:assert/strict";
import test from "node:test";

import { GatewayToolDescriptor, JsonRecord, NormalizedRun } from "../src/contracts.js";
import { invokeSkillGateway, invokeToolGateway } from "../src/gateway.js";

const descriptor: GatewayToolDescriptor = {
  key: "issue-create",
  code: "issue-create",
  version: 4,
  sdkName: "gateway_issue_create_v4",
  name: "Issue Create",
  description: "Create an issue",
  adapterType: "JAVA_INTERNAL",
  inputSchema: { type: "object" },
  timeoutMs: 5000,
};
const run = { runId: "run-1", requestId: "request-1", input: "create" } as NormalizedRun;

test("invokes the versioned Tool Gateway with auth and idempotency", async () => {
  const originalFetch = globalThis.fetch;
  const originalUrl = process.env.AI_AGENT_TOOL_GATEWAY_URL;
  const originalToken = process.env.AI_AGENT_TOOL_GATEWAY_TOKEN;
  const originalApproval = process.env.AI_AGENT_TOOL_APPROVAL;
  let capturedUrl = "";
  let capturedInit: RequestInit | undefined;
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    capturedUrl = String(input);
    capturedInit = init;
    return new Response(JSON.stringify({
      toolCode: "issue-create",
      toolVersion: 4,
      status: "SUCCESS",
      output: { id: "ISSUE-1" },
      durationMs: 12,
    }), { status: 200 });
  }) as typeof fetch;
  process.env.AI_AGENT_TOOL_GATEWAY_URL = "http://tool-gateway/base/";
  process.env.AI_AGENT_TOOL_GATEWAY_TOKEN = "token-value";
  process.env.AI_AGENT_TOOL_APPROVAL = "approved-grant";
  try {
    const result = await invokeToolGateway(descriptor, run, { title: "Fix" }, "sha256:snapshot");
    assert.equal(capturedUrl,
      "http://tool-gateway/base/api/v1/ai/tool-gateway/issue-create/versions/4/invoke");
    const headers = new Headers(capturedInit?.headers);
    assert.equal(headers.get("Authorization"), "Bearer token-value");
    assert.ok(headers.get("Idempotency-Key"));
    assert.equal(headers.get("X-Tool-Approval"), "approved-grant");
    const requestBody = JSON.parse(String(capturedInit?.body)) as JsonRecord;
    assert.deepEqual(requestBody.arguments, { title: "Fix" });
    assert.equal((requestBody.run as JsonRecord).runId, "run-1");
    assert.equal((requestBody.run as JsonRecord).snapshotHash, "sha256:snapshot");
    assert.equal(result.status, "SUCCESS");
  } finally {
    globalThis.fetch = originalFetch;
    if (originalUrl === undefined) delete process.env.AI_AGENT_TOOL_GATEWAY_URL;
    else process.env.AI_AGENT_TOOL_GATEWAY_URL = originalUrl;
    if (originalToken === undefined) delete process.env.AI_AGENT_TOOL_GATEWAY_TOKEN;
    else process.env.AI_AGENT_TOOL_GATEWAY_TOKEN = originalToken;
    if (originalApproval === undefined) delete process.env.AI_AGENT_TOOL_APPROVAL;
    else process.env.AI_AGENT_TOOL_APPROVAL = originalApproval;
  }
});

test("fails closed when Gateway auth is missing", async () => {
  const originalUrl = process.env.AI_AGENT_TOOL_GATEWAY_URL;
  const originalToken = process.env.AI_AGENT_TOOL_GATEWAY_TOKEN;
  process.env.AI_AGENT_TOOL_GATEWAY_URL = "http://gateway";
  delete process.env.AI_AGENT_TOOL_GATEWAY_TOKEN;
  try {
    const result = await invokeToolGateway(descriptor, run, { title: "Fix" });
    assert.equal(result.status, "FAILED");
    assert.match(String(result.error), /TOKEN/);
  } finally {
    if (originalUrl === undefined) delete process.env.AI_AGENT_TOOL_GATEWAY_URL;
    else process.env.AI_AGENT_TOOL_GATEWAY_URL = originalUrl;
    if (originalToken === undefined) delete process.env.AI_AGENT_TOOL_GATEWAY_TOKEN;
    else process.env.AI_AGENT_TOOL_GATEWAY_TOKEN = originalToken;
  }
});

test("fails closed when the frozen snapshot hash is missing", async () => {
  const originalUrl = process.env.AI_AGENT_TOOL_GATEWAY_URL;
  const originalToken = process.env.AI_AGENT_TOOL_GATEWAY_TOKEN;
  process.env.AI_AGENT_TOOL_GATEWAY_URL = "http://gateway";
  process.env.AI_AGENT_TOOL_GATEWAY_TOKEN = "token-value";
  try {
    const result = await invokeToolGateway(descriptor, run, { title: "Fix" });
    assert.equal(result.status, "FAILED");
    assert.match(String(result.error), /snapshotHash/);
  } finally {
    if (originalUrl === undefined) delete process.env.AI_AGENT_TOOL_GATEWAY_URL;
    else process.env.AI_AGENT_TOOL_GATEWAY_URL = originalUrl;
    if (originalToken === undefined) delete process.env.AI_AGENT_TOOL_GATEWAY_TOKEN;
    else process.env.AI_AGENT_TOOL_GATEWAY_TOKEN = originalToken;
  }
});

test("reads a versioned Skill resource through the run-scoped Gateway", async () => {
  const originalFetch = globalThis.fetch;
  const originalUrl = process.env.AI_AGENT_SKILL_GATEWAY_URL;
  const originalToken = process.env.AI_AGENT_SKILL_GATEWAY_TOKEN;
  let capturedUrl = "";
  let capturedBody: JsonRecord = {};
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    capturedUrl = String(input);
    capturedBody = JSON.parse(String(init?.body)) as JsonRecord;
    return new Response(JSON.stringify({
      skillCode: "database-skill",
      skillVersion: 3,
      path: "SKILL.md",
      mediaType: "text/markdown",
      checksum: "checksum",
      encoding: "utf-8",
      content: "# Database skill",
    }), { status: 200 });
  }) as typeof fetch;
  process.env.AI_AGENT_SKILL_GATEWAY_URL = "http://skill-gateway/base/";
  process.env.AI_AGENT_SKILL_GATEWAY_TOKEN = "token-value";
  try {
    const result = await invokeSkillGateway(
      { code: "database-skill", version: 3 },
      run,
      "sha256:snapshot",
      "SKILL.md",
    );
    assert.equal(capturedUrl,
      "http://skill-gateway/base/api/v1/ai/skill-gateway/database-skill/versions/3/resources/read");
    assert.equal((capturedBody.run as JsonRecord).runId, "run-1");
    assert.equal((capturedBody.run as JsonRecord).snapshotHash, "sha256:snapshot");
    assert.equal(result.content, "# Database skill");
  } finally {
    globalThis.fetch = originalFetch;
    if (originalUrl === undefined) delete process.env.AI_AGENT_SKILL_GATEWAY_URL;
    else process.env.AI_AGENT_SKILL_GATEWAY_URL = originalUrl;
    if (originalToken === undefined) delete process.env.AI_AGENT_SKILL_GATEWAY_TOKEN;
    else process.env.AI_AGENT_SKILL_GATEWAY_TOKEN = originalToken;
  }
});
