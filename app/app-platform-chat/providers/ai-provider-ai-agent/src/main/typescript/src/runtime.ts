import { readFile, realpath } from "node:fs/promises";
import { resolve, relative, isAbsolute } from "node:path";
import { createHash } from "node:crypto";
import * as openAiAgents from "@openai/agents";

import { CompiledAgent, CompiledGraph, JsonRecord, ToolIdentityDescriptor } from "./contracts.js";
import { buildApplicationInput } from "./compiler.js";
import { mapSdkStreamEvent, platformEvent } from "./events.js";
import { buildGatewayTool, invokeSkillGateway } from "./gateway.js";

type Writer = (frame: JsonRecord) => void;

export async function runGraph(graph: CompiledGraph, write: Writer): Promise<JsonRecord> {
  const sdk: any = openAiAgents;
  const agentsByKey = new Map<string, any>();
  const compiledBySdk = new Map<any, CompiledAgent>();
  const byKey = new Map(graph.agents.map((agent) => [agent.key, agent]));
  const functionTools = buildFunctionToolRegistry(sdk, graph);
  const toolBySdkName = new Map<string, ToolIdentityDescriptor>(
    Object.values(graph.gatewayTools).map((descriptor) => [descriptor.sdkName, descriptor]),
  );
  for (const spec of graph.agents) {
    for (const link of spec.agentTools) {
      const target = byKey.get(link.target);
      if (!target) continue;
      const toolKey = link.toolName ?? `ask_${safeIdentifier(target.code)}`;
      toolBySdkName.set(toolKey, {
        key: toolKey,
        code: toolKey,
        sdkName: toolKey,
        name: target.name,
      });
    }
  }

  const build = (key: string): any => {
    const existing = agentsByKey.get(key);
    if (existing) return existing;
    const spec = byKey.get(key);
    if (!spec) throw new Error(`Unknown Agent: ${key}`);
    const agentTools = spec.agentTools.map((link) => {
      const target = build(link.target);
      const targetSpec = byKey.get(link.target)!;
      return target.asTool({
        toolName: link.toolName ?? `ask_${safeIdentifier(targetSpec.code)}`,
        toolDescription: link.description ?? targetSpec.description ?? `Delegate work to ${targetSpec.name}.`,
      });
    });
    const handoffs = spec.handoffs.map((link) => build(link.target));
    const tools = spec.tools.map((name) => {
      const tool = functionTools.get(name);
      if (!tool) throw new Error(`TypeScript function tool is not registered: ${name}`);
      return tool;
    });
    if (spec.skills.length > 0) tools.push(buildSkillTool(sdk, graph, write, spec));
    tools.push(...agentTools);
    const agent = new sdk.Agent({
      name: spec.name,
      instructions: spec.instructions,
      handoffDescription: spec.description || undefined,
      model: runtimeModel(spec.model),
      modelSettings: camelSettings(spec.modelSettings),
      tools,
      handoffs,
    });
    agentsByKey.set(key, agent);
    compiledBySdk.set(agent, spec);
    return agent;
  };

  const rootSpec = byKey.get(graph.rootAgent)!;
  const root = build(graph.rootAgent);
  write(platformEvent(graph, "thinking.analysis.started", {
    status: "RUNNING",
    message: "分析用户请求",
    agent: rootSpec,
    ext: {
      activityCode: "main-agent-request-analysis",
      activityType: "THINKING",
      activityName: "分析用户请求",
      inputSummary: requestAnalysisSummary(graph),
    },
  }));
  write(platformEvent(graph, "agent.started", {
    status: "RUNNING",
    message: `${rootSpec.name} started`,
    agent: rootSpec,
    ext: { enabledTools: rootSpec.tools, snapshotHash: graph.payload.snapshotHash },
  }));
  const result: any = await sdk.run(
    root,
    buildApplicationInput(graph.payload.messages, graph.payload.run.input, graph.payload.run.context),
    { stream: true, maxTurns: graph.maxTurns },
  );
  let analysisCompleted = false;
  for await (const event of result) {
    if (!analysisCompleted) {
      const decisionSummary = analysisDecisionSummary(event, toolBySdkName);
      if (decisionSummary) {
        write(platformEvent(graph, "thinking.analysis.completed", {
          status: "SUCCESS",
          message: "分析用户请求",
          agent: rootSpec,
          ext: {
            activityCode: "main-agent-request-analysis",
            activityType: "THINKING",
            activityName: "分析用户请求",
            outputSummary: decisionSummary,
          },
        }));
        analysisCompleted = true;
      }
    }
    const mapped = mapSdkStreamEvent(
      graph,
      event,
      (agent) => compiledBySdk.get(agent),
      (name) => name ? toolBySdkName.get(name) : undefined,
    );
    if (mapped) write(mapped);
  }
  if (!analysisCompleted) {
    write(platformEvent(graph, "thinking.analysis.completed", {
      status: "SUCCESS",
      message: "分析用户请求",
      agent: rootSpec,
      ext: {
        activityCode: "main-agent-request-analysis",
        activityType: "THINKING",
        activityName: "分析用户请求",
        outputSummary: requestAnalysisResult(graph, rootSpec),
      },
    }));
  }
  if (result.completed) await result.completed;
  const finalOutput = result.finalOutput ?? result.final_output ?? "";
  const lastSdkAgent = result.lastAgent ?? result.last_agent ?? result.state?._currentAgent;
  const lastAgent = compiledBySdk.get(lastSdkAgent) ?? rootSpec;
  const usage = extractUsage(result);
  const artifacts = artifactList(finalOutput);
  for (const artifact of artifacts) {
    write(platformEvent(graph, "artifact.created", {
      status: "SUCCESS",
      message: `Artifact ${String(artifact.artifactCode)} created`,
      agent: lastAgent,
      ext: {
        artifactCode: artifact.artifactCode,
        artifactType: artifact.artifactType,
        contentFormat: artifact.contentFormat,
      },
    }));
  }
  write(platformEvent(graph, "agent.completed", {
    status: "SUCCESS",
    message: `${lastAgent.name} completed`,
    agent: lastAgent,
    ext: { usage },
  }));
  write(platformEvent(graph, "round.completed", {
    status: "SUCCESS",
    message: "Agent round completed",
    agent: lastAgent,
    ext: { usage, artifactCount: artifacts.length },
  }));
  const finalText = typeof finalOutput === "string" ? finalOutput : JSON.stringify(finalOutput);
  return {
    protocolVersion: "2.0",
    runId: graph.payload.run.runId,
    requestId: graph.payload.run.requestId,
    model: runtimeModel(rootSpec.model),
    finishReason: "STOP",
    status: "SUCCESS",
    finalOutput: finalText,
    finalAgentCode: lastAgent.code,
    outputs: [{ type: "TEXT", text: finalText, json: {} }],
    usage,
    artifacts,
    providerMeta: {
      runtimeType: "OPENAI_AGENTS_TYPESCRIPT",
      sdkVersion: "0.13.4",
      protocolVersion: "2.0",
      snapshotHash: graph.payload.snapshotHash,
      lastAgent: lastAgent.code,
    },
  };
}

function requestAnalysisSummary(graph: CompiledGraph): string {
  const request = latestUserRequest(graph);
  if (!request) return "识别本轮目标、可用上下文以及是否需要工具或协作智能体。";
  return `收到用户请求：${compact(request, 360)}。将识别任务目标，并判断是否需要工具或协作智能体。`;
}

function requestAnalysisResult(graph: CompiledGraph, root: CompiledAgent): string {
  const target = compact(latestUserRequest(graph) ?? "继续处理当前对话任务", 240);
  const collaborationCount = root.agentTools.length + root.handoffs.length;
  const capabilities: string[] = [];
  if (root.tools.length) capabilities.push(`${root.tools.length} 个可用工具`);
  if (collaborationCount) capabilities.push(`${collaborationCount} 个协作智能体入口`);
  const capabilityText = capabilities.length ? capabilities.join("、") : "当前智能体自身能力";
  return `已识别任务目标：${target}。本轮将由“${root.name}”处理，可使用${capabilityText}。`;
}

function analysisDecisionSummary(
  event: unknown,
  toolBySdkName: Map<string, ToolIdentityDescriptor>,
): string | undefined {
  const value = asRecord(event);
  const eventType = String(value.type ?? "");
  if (eventType === "run_item_stream_event") {
    const name = String(value.name ?? "");
    const item = asRecord(value.item);
    const rawItem = asRecord(item.rawItem ?? item.raw_item);
    if (name === "tool_called" || name === "tool_search_called") {
      const sdkName = text(rawItem.name, item.name);
      const descriptor = sdkName ? toolBySdkName.get(sdkName) : undefined;
      const toolName = text(descriptor?.name, descriptor?.code, sdkName) ?? "可用工具";
      return `分析结果：本轮需要调用“${toolName}”获取或校验必要信息，然后再形成回答。`;
    }
    if (name === "handoff_requested") {
      return "分析结果：本轮需要交由协作智能体继续处理，以使用更匹配的专业能力。";
    }
  }
  if (eventType === "agent_updated_stream_event") {
    return "分析结果：本轮需要由协作智能体继续处理，以使用更匹配的专业能力。";
  }
  if (eventType === "raw_model_stream_event" || eventType === "raw_response_event") {
    const data = asRecord(value.data);
    const dataType = String(data.type ?? "");
    if (dataType === "output_text_delta" || dataType === "response.output_text.delta") {
      return "分析结果：当前上下文足以形成回答，本轮将直接整理并输出结论。";
    }
  }
  return undefined;
}

function latestUserRequest(graph: CompiledGraph): string | undefined {
  const runInput = graph.payload.run.input;
  if (typeof runInput === "string" && runInput.trim()) return runInput.trim();
  for (const message of [...graph.payload.messages].reverse()) {
    const value = message as JsonRecord;
    if (String(value.role ?? "").toLowerCase() !== "user") continue;
    const content = text(value.content, value.text);
    if (content) return content;
  }
  return undefined;
}

function compact(value: string, limit: number): string {
  const normalized = value.replace(/\s+/g, " ").trim();
  return normalized.length <= limit ? normalized : `${normalized.slice(0, limit - 1)}…`;
}

function buildFunctionToolRegistry(sdk: any, graph: CompiledGraph): Map<string, any> {
  const jsonValidator = (name: string, description: string) => sdk.tool({
    name,
    description,
    strict: false,
    parameters: {
      type: "object",
      properties: { content: { type: "string" }, render_json: { type: "string" } },
      additionalProperties: false,
    },
    execute: async (input: JsonRecord) => {
      const text = String(input.content ?? input.render_json ?? "");
      try {
        return { tool: name, valid: true, normalized: JSON.parse(text), errors: [] };
      } catch (error) {
        return { tool: name, valid: false, errors: [safeMessage(error)] };
      }
    },
  });
  const tools = new Map<string, any>([
    ["data_format_validate_tool", jsonValidator("data_format_validate_tool", "Validate JSON syntax and return normalized data.")],
    ["render_json_validate_tool", jsonValidator("render_json_validate_tool", "Validate render JSON syntax.")],
  ]);
  for (const [runtimeName, descriptor] of Object.entries(graph.gatewayTools)) {
    tools.set(runtimeName, buildGatewayTool(sdk, descriptor, graph.payload.run, graph.payload.snapshotHash));
  }
  return tools;
}

function buildSkillTool(sdk: any, graph: CompiledGraph, write: Writer, owner: CompiledAgent): any {
  return sdk.tool({
    name: "load_skill_resource",
    description: "Load an approved skill package resource by its metadata key, never by its display name.",
    strict: false,
    parameters: {
      type: "object",
      properties: {
        skill_key: { type: "string" },
        resource_path: { type: "string", default: "SKILL.md" },
      },
      required: ["skill_key"],
      additionalProperties: false,
    },
    execute: async (input: JsonRecord) => {
      const skillKey = String(input.skill_key ?? "").trim();
      const resourcePath = safeRelativePath(String(input.resource_path ?? "SKILL.md"));
      const skill = findSkill(graph, skillKey);
      const canonicalRef = text(skill.ref) ?? skillKey;
      if (!owner.skills.some((candidate) => candidate.ref === canonicalRef)) {
        throw new Error(`Skill is not assigned to this Agent: ${skillKey}`);
      }
      const manifest = asRecord(skill.manifest);
      const files = normalizedInlineFiles(
        Object.keys(asRecord(skill.files)).length > 0 ? skill.files : manifest.files,
      );
      let content = files.get(resourcePath);
      let contentBytes: Buffer | undefined;
      let gatewayMetadata: JsonRecord = {};
      if (content === undefined && resourcePath === "SKILL.md") {
        content = typeof skill.skillMd === "string"
          ? skill.skillMd
          : typeof skill.content === "string" ? skill.content : undefined;
      }
      if (content === undefined) {
        const rootPath = text(
          skill.rootPath, skill.extractedPath, skill.path,
          manifest.rootPath, manifest.extractedPath, manifest.path,
        );
        if (rootPath) {
          const root = await realpath(resolve(rootPath));
          const candidate = await realpath(resolve(root, resourcePath));
          const escaped = relative(root, candidate);
          if (escaped.startsWith("..") || isAbsolute(escaped)) throw new Error("Skill resource escapes the configured package root");
          content = await readFile(candidate, "utf8");
        } else {
          const expected = packageFile(manifest, resourcePath);
          if (Array.isArray(manifest.files) && !expected) {
            throw new Error(`Skill resource is not part of the frozen package: ${resourcePath}`);
          }
          const gateway = await invokeSkillGateway(skill, graph.payload.run, graph.payload.snapshotHash, resourcePath);
          const validated = validateGatewaySkillContent(gateway, expected);
          content = validated.content;
          contentBytes = validated.bytes;
          gatewayMetadata = validated.metadata;
        }
      }
      if ((contentBytes?.byteLength ?? Buffer.byteLength(content, "utf8")) > 256 * 1024) {
        throw new Error("Skill resource exceeds 262144 bytes");
      }
      write(platformEvent(graph, "skill.loaded", {
        status: "SUCCESS",
        message: `已加载技能：${text(skill.name, skill.ref)}`,
        agent: owner,
        ext: {
          activityCode: `skill:${text(skill.ref) ?? skillKey}:${resourcePath}`,
          activityType: "SKILL_LOAD",
          activityName: `加载技能：${text(skill.name, skill.ref)}`,
          skillKey: text(skill.key, skill.code, skill.ref),
          skillRef: text(skill.ref), skillName: text(skill.name), resourcePath,
          contentHash: text(skill.contentHash, skill.checksum),
          outputSummary: `已加载技能“${text(skill.name, skill.ref)}”的资源：${resourcePath}。`,
        },
      }));
      return {
        skillKey: text(skill.key, skill.code, skill.ref),
        skillRef: text(skill.ref), skillName: text(skill.name), resourcePath,
        contentHash: text(skill.contentHash, skill.checksum), content,
        ...gatewayMetadata,
      };
    },
  });
}

function findSkill(graph: CompiledGraph, key: string): JsonRecord {
  const source = graph.payload.resolvedCapabilities.skills;
  const records: JsonRecord[] = Array.isArray(source)
    ? source.filter(isRecord)
    : Object.entries(asRecord(source))
      .filter((entry): entry is [string, JsonRecord] => isRecord(entry[1]))
      .map(([key, item]): JsonRecord => ({ ref: key, ...item }));
  const normalized: JsonRecord[] = records.map((item): JsonRecord => {
    const manifest = asRecord(item.manifest);
    const code = text(item.code, manifest.code, terminalRef(text(item.ref) ?? ""));
    const version = optionalInteger(item.version ?? manifest.version);
    const canonicalRef = text(item.ref)
      ?? (code ? version === undefined ? `skill://${code}` : `skill://${code}/v${version}` : undefined);
    return { ...item, key: text(item.key, code, canonicalRef), code, version, ref: canonicalRef, manifest };
  });
  const found = normalized.find((item) => [
    text(item.key), text(item.ref), text(item.name), text(item.code), terminalRef(text(item.ref) ?? ""),
    item.code && item.version !== undefined ? `skill://${item.code}@${item.version}` : undefined,
  ].includes(key));
  if (!found) throw new Error(`Unknown skill key: ${key}`);
  return found;
}

function packageFile(manifest: JsonRecord, resourcePath: string): JsonRecord | undefined {
  return Array.isArray(manifest.files)
    ? manifest.files.filter(isRecord).find((item) => item.path === resourcePath)
    : undefined;
}

function validateGatewaySkillContent(
  gateway: JsonRecord,
  expected: JsonRecord | undefined,
): { content: string; bytes: Buffer; metadata: JsonRecord } {
  const content = gateway.content;
  if (typeof content !== "string") throw new Error("Skill Gateway response content must be a string");
  const encoding = String(gateway.encoding ?? "utf-8").toLowerCase();
  const contentBytes = encoding === "base64"
    ? strictBase64(content)
    : encoding === "utf-8" ? Buffer.from(content, "utf8") : undefined;
  if (!contentBytes) throw new Error(`Skill Gateway returned unsupported encoding: ${encoding}`);
  if (contentBytes.byteLength > 256 * 1024) throw new Error("Skill resource exceeds 262144 bytes");
  const responseChecksum = text(gateway.checksum);
  const expectedChecksum = text(expected?.checksum);
  if (expectedChecksum && responseChecksum !== expectedChecksum) {
    throw new Error("Skill Gateway checksum does not match the frozen package manifest");
  }
  if (responseChecksum) {
    const normalized = responseChecksum.toLowerCase().replace(/^sha256:/, "");
    const actual = createHash("sha256").update(contentBytes).digest("hex");
    if (normalized !== actual) throw new Error("Skill Gateway content checksum is invalid");
  }
  return {
    content,
    bytes: contentBytes,
    metadata: {
      mediaType: gateway.mediaType,
      checksum: responseChecksum,
      encoding,
    },
  };
}

function strictBase64(value: string): Buffer {
  if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(value)) {
    throw new Error("Skill Gateway returned invalid base64 content");
  }
  const decoded = Buffer.from(value, "base64");
  if (decoded.toString("base64") !== value) {
    throw new Error("Skill Gateway returned invalid base64 content");
  }
  return decoded;
}

function extractUsage(result: any): JsonRecord {
  const candidates = [result.context?.usage, result.contextWrapper?.usage, result.state?._context?.usage, result.usage];
  for (const value of candidates) {
    if (!value) continue;
    const inputTokens = numberValue(value.inputTokens ?? value.input_tokens ?? value.promptTokens ?? value.prompt_tokens);
    const outputTokens = numberValue(value.outputTokens ?? value.output_tokens ?? value.completionTokens ?? value.completion_tokens);
    const totalTokens = numberValue(value.totalTokens ?? value.total_tokens) ?? inputTokens + outputTokens;
    return { inputTokens, outputTokens, totalTokens };
  }
  return { inputTokens: 0, outputTokens: 0, totalTokens: 0 };
}

export function artifactList(value: unknown): JsonRecord[] {
  const decoded = decodeJsonValue(value);
  const artifacts = asRecord(decoded).artifacts;
  if (!Array.isArray(artifacts)) return [];
  return artifacts.filter(isRecord).flatMap((item) => {
    const artifactCode = text(item.artifactCode, item.code);
    if (!artifactCode) return [];
    const content = "content" in item
      ? item.content
      : firstValue(item, "value", "data", "json", "text");
    const artifactType = text(item.artifactType, item.type) ?? "AGENT_OUTPUT";
    const contentFormat = text(item.contentFormat, item.format)
      ?? (isRecord(content) || Array.isArray(content) ? "JSON" : "PLAIN_TEXT");
    const normalized = { ...item };
    delete normalized.code;
    delete normalized.type;
    delete normalized.format;
    return [{ ...normalized, artifactCode, artifactType, contentFormat, content }];
  });
}

function decodeJsonValue(value: unknown): unknown {
  if (isRecord(value) || Array.isArray(value)) return value;
  if (typeof value !== "string") return undefined;
  let normalized = value.trim();
  if (normalized.startsWith("```") && normalized.endsWith("```")) {
    const lines = normalized.split(/\r?\n/);
    if (lines.length < 3 || lines.at(-1)?.trim() !== "```") return undefined;
    normalized = lines.slice(1, -1).join("\n").trim();
  }
  if (!normalized || !["{", "["].includes(normalized[0] ?? "") || Buffer.byteLength(normalized, "utf8") > 4 * 1024 * 1024) {
    return undefined;
  }
  try {
    return JSON.parse(normalized) as unknown;
  } catch {
    return undefined;
  }
}

function normalizedInlineFiles(value: unknown): Map<string, string> {
  const files = new Map<string, string>();
  for (const [path, content] of Object.entries(asRecord(value))) {
    if (typeof content === "string") files.set(safeRelativePath(path), content);
  }
  return files;
}

function runtimeModel(model?: string): string {
  const configured = process.env.OPENAI_MODEL?.trim();
  if (configured) return configured;
  if (model && !model.includes("://")) return model;
  return "gpt-5.5";
}

function camelSettings(value: JsonRecord): JsonRecord {
  const aliases: Record<string, string> = { top_p: "topP", max_tokens: "maxTokens", parallel_tool_calls: "parallelToolCalls" };
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [aliases[key] ?? key, item]));
}

function safeRelativePath(value: string): string {
  const normalized = value.replaceAll("\\", "/").trim() || "SKILL.md";
  if (normalized.startsWith("/") || normalized.split("/").some((part) => !part || part === "." || part === "..")) {
    throw new Error("Skill resource path must be a normalized relative path");
  }
  return normalized;
}

function safeIdentifier(value: string): string {
  return value.toLowerCase().replaceAll(/[^a-z0-9]+/g, "_").replaceAll(/^_+|_+$/g, "") || "agent";
}

function numberValue(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function optionalInteger(value: unknown): number | undefined {
  const parsed = Number(value);
  return Number.isInteger(parsed) ? parsed : undefined;
}

function asRecord(value: unknown): JsonRecord {
  return isRecord(value) ? value : {};
}

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function text(...values: unknown[]): string | undefined {
  for (const value of values) {
    if (value === undefined || value === null) continue;
    const normalized = String(value).trim();
    if (normalized) return normalized;
  }
  return undefined;
}

function terminalRef(value: string): string {
  const path = value.replace(/\/$/, "").replace(/^[a-z][a-z0-9+.-]*:\/\//i, "");
  const parts = path.split("/").filter(Boolean);
  if (parts.length === 0) return "";
  const last = parts.at(-1) ?? "";
  const terminal = /^v\d+$/i.test(last) && parts.length > 1 ? parts.at(-2) ?? "" : last;
  return terminal.split("@", 1)[0] ?? "";
}

function firstValue(value: JsonRecord, ...keys: string[]): unknown {
  for (const key of keys) {
    if (key in value) return value[key];
  }
  return undefined;
}

function safeMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error);
  return redact(message).slice(0, 1000);
}

function redact(value: string): string {
  return value
    .replace(/bearer\s+[^\s,;]+/gi, "Bearer [REDACTED]")
    .replace(/\bsk-[A-Za-z0-9_-]{8,}\b/g, "[REDACTED_OPENAI_KEY]");
}
