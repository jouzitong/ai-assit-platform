import { createHash } from "node:crypto";

import { GatewayToolDescriptor, JsonRecord, NormalizedRun } from "./contracts.js";

export function buildGatewayTool(
  sdk: any,
  descriptor: GatewayToolDescriptor,
  run: NormalizedRun,
  snapshotHash?: string,
): any {
  return sdk.tool({
    name: descriptor.sdkName,
    description: descriptor.description,
    strict: false,
    parameters: descriptor.inputSchema,
    execute: async (argumentsValue: JsonRecord) => invokeToolGateway(descriptor, run, argumentsValue, snapshotHash),
  });
}

export async function invokeToolGateway(
  descriptor: GatewayToolDescriptor,
  run: NormalizedRun,
  argumentsValue: JsonRecord,
  snapshotHash?: string,
): Promise<JsonRecord> {
  const baseUrl = (process.env.AI_AGENT_TOOL_GATEWAY_URL ?? "").trim().replace(/\/+$/, "");
  const token = (process.env.AI_AGENT_TOOL_GATEWAY_TOKEN ?? "").trim();
  if (!baseUrl) return failure(descriptor, "AI_AGENT_TOOL_GATEWAY_URL is required");
  if (!token) return failure(descriptor, "AI_AGENT_TOOL_GATEWAY_TOKEN is required");
  const runId = String(run.runId ?? "").trim();
  if (!runId) return failure(descriptor, "Tool Gateway runId is required");
  const frozenHash = String(snapshotHash ?? "").trim();
  if (!frozenHash) return failure(descriptor, "Tool Gateway snapshotHash is required");

  const url = `${baseUrl}/api/v1/ai/tool-gateway/${encodeURIComponent(descriptor.code)}`
    + `/versions/${descriptor.version}/invoke`;
  const safeRun = Object.fromEntries(
    ["runId", "requestId", "traceId", "sessionCode", "roundCode", "userId"]
      .filter((key) => run[key] !== undefined && run[key] !== null)
      .map((key) => [key, run[key]]),
  );
  safeRun.snapshotHash = frozenHash;
  const body = JSON.stringify({ arguments: argumentsValue, run: safeRun });
  const argumentsHash = stableJson(argumentsValue);
  const idempotencyKey = createHash("sha256")
    .update(`${runId}|tool://${descriptor.code}/v${descriptor.version}|${argumentsHash}`)
    .digest("hex");
  const headers: Record<string, string> = {
    "Content-Type": "application/json; charset=utf-8",
    Authorization: token.toLowerCase().startsWith("bearer ") ? token : `Bearer ${token}`,
    "Idempotency-Key": idempotencyKey,
  };
  const approval = (process.env.AI_AGENT_TOOL_APPROVAL ?? "").trim();
  if (approval) headers["X-Tool-Approval"] = approval;
  try {
    const response = await fetch(url, {
      method: "POST",
      headers,
      body,
      signal: AbortSignal.timeout(Math.max(100, descriptor.timeoutMs)),
    });
    const responseText = await response.text();
    if (!response.ok) {
      return failure(descriptor, `Tool Gateway HTTP ${response.status}: ${redact(responseText).slice(0, 500)}`);
    }
    let decoded: unknown;
    try {
      decoded = responseText ? JSON.parse(responseText) as unknown : {};
    } catch {
      return failure(descriptor, `Tool Gateway returned non-JSON: ${redact(responseText).slice(0, 500)}`);
    }
    if (!isRecord(decoded)) return failure(descriptor, "Tool Gateway response must be a JSON object");
    if (decoded.toolCode !== descriptor.code || integer(decoded.toolVersion) !== descriptor.version) {
      return failure(descriptor, "Tool Gateway response identity does not match the invoked Tool");
    }
    const status = String(decoded.status ?? "").toUpperCase();
    if (status !== "SUCCESS" && status !== "FAILED") {
      return failure(descriptor, "Tool Gateway response status must be SUCCESS or FAILED");
    }
    return { ...decoded, status };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return failure(descriptor, `Tool Gateway request failed: ${redact(message).slice(0, 500)}`);
  }
}

export async function invokeSkillGateway(
  skill: JsonRecord,
  run: NormalizedRun,
  snapshotHash: string | undefined,
  resourcePath: string,
): Promise<JsonRecord> {
  const baseUrl = (
    process.env.AI_AGENT_SKILL_GATEWAY_URL
    ?? process.env.AI_AGENT_TOOL_GATEWAY_URL
    ?? ""
  ).trim().replace(/\/+$/, "");
  const token = (
    process.env.AI_AGENT_SKILL_GATEWAY_TOKEN
    ?? process.env.AI_AGENT_TOOL_GATEWAY_TOKEN
    ?? ""
  ).trim();
  const code = text(skill.code);
  const version = integer(skill.version);
  if (!code || version === undefined) throw new Error("Versioned Skill identity is required");
  if (!baseUrl) throw new Error("AI_AGENT_SKILL_GATEWAY_URL is required");
  if (!token) throw new Error("AI_AGENT_SKILL_GATEWAY_TOKEN is required");
  const runId = String(run.runId ?? "").trim();
  if (!runId) throw new Error("Skill Gateway runId is required");
  const frozenHash = String(snapshotHash ?? "").trim();
  if (!frozenHash) throw new Error("Skill Gateway snapshotHash is required");
  const url = `${baseUrl}/api/v1/ai/skill-gateway/${encodeURIComponent(code)}`
    + `/versions/${version}/resources/read`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      Authorization: token.toLowerCase().startsWith("bearer ") ? token : `Bearer ${token}`,
    },
    body: JSON.stringify({ path: resourcePath, run: { runId, snapshotHash: frozenHash } }),
    signal: AbortSignal.timeout(20_000),
  });
  const responseText = await response.text();
  if (!response.ok) throw new Error(`Skill Gateway HTTP ${response.status}`);
  let decoded: unknown;
  try {
    decoded = responseText ? JSON.parse(responseText) as unknown : {};
  } catch {
    throw new Error("Skill Gateway returned non-JSON");
  }
  if (!isRecord(decoded)) throw new Error("Skill Gateway response must be a JSON object");
  if (decoded.skillCode !== code || integer(decoded.skillVersion) !== version) {
    throw new Error("Skill Gateway response identity does not match the frozen Skill");
  }
  if (decoded.path !== resourcePath) throw new Error("Skill Gateway response path does not match the requested resource");
  return decoded;
}

function failure(descriptor: GatewayToolDescriptor, error: string): JsonRecord {
  return { toolCode: descriptor.code, toolVersion: descriptor.version, status: "FAILED", error };
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(",")}]`;
  if (isRecord(value)) {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(",")}}`;
  }
  return JSON.stringify(value) ?? "null";
}

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function text(value: unknown): string | undefined {
  const normalized = value === null || value === undefined ? "" : String(value).trim();
  return normalized || undefined;
}

function integer(value: unknown): number | undefined {
  const parsed = Number(value);
  return Number.isInteger(parsed) ? parsed : undefined;
}

function redact(value: string): string {
  return value
    .replace(/bearer\s+[^\s,;]+/gi, "Bearer [REDACTED]")
    .replace(/\bsk-[A-Za-z0-9_-]{8,}\b/g, "[REDACTED_OPENAI_KEY]");
}
