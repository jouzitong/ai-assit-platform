import { CompiledAgent, CompiledGraph, JsonRecord, PlatformEvent, ToolIdentityDescriptor } from "./contracts.js";

const BUILTIN_TOOL_DISPLAY_NAMES: Record<string, string> = {
  data_preview_query_tool: "查询数据预览",
  data_format_validate_tool: "校验数据格式",
  knowledge_base_search_tool: "检索知识库",
  load_skill_resource: "读取技能资源",
  render_json_validate_tool: "校验 Render JSON",
  web_search_tool: "搜索网页",
};

const BUILTIN_TOOL_CALL_REASONS: Record<string, string> = {
  data_preview_query_tool: "需要核对授权数据的字段、结构和实际记录。",
  data_format_validate_tool: "需要确认数据格式符合后续处理要求。",
  knowledge_base_search_tool: "当前任务需要补充已授权知识库中的业务语义和事实依据。",
  load_skill_resource: "需要读取已选技能的执行规范和资源内容。",
  render_json_validate_tool: "需要确认生成内容符合页面渲染结构与组件契约。",
  web_search_tool: "当前任务需要补充公开网页中的最新信息或事实依据。",
};

export function platformEvent(
  graph: CompiledGraph,
  eventType: string,
  options: {
    status?: string;
    message?: string;
    delta?: string;
    agent?: CompiledAgent;
    ext?: JsonRecord;
    frameType?: "event" | "error";
  } = {},
): PlatformEvent {
  const run = graph.payload.run;
  return {
    protocolVersion: "2.0",
    type: options.frameType ?? "event",
    eventType,
    runId: run.runId,
    requestId: run.requestId,
    traceId: run.traceId,
    sessionCode: run.sessionCode,
    roundCode: run.roundCode,
    agentCode: options.agent?.code,
    agentVersion: options.agent?.version,
    agentName: options.agent?.name,
    status: options.status,
    message: options.message,
    delta: options.delta,
    timestamp: new Date().toISOString(),
    source: "OPENAI_AGENTS_TYPESCRIPT",
    ext: options.ext ?? {},
  };
}

export function mapSdkStreamEvent(
  graph: CompiledGraph,
  event: unknown,
  agentLookup: (agent: unknown) => CompiledAgent | undefined,
  toolLookup: (sdkName: string | undefined) => ToolIdentityDescriptor | undefined = () => undefined,
): PlatformEvent | undefined {
  const value = asRecord(event);
  const eventType = String(value.type ?? "");
  if (eventType === "raw_model_stream_event" || eventType === "raw_response_event") {
    const data = asRecord(value.data);
    const dataType = String(data.type ?? "");
    if (dataType === "output_text_delta" || dataType === "response.output_text.delta") {
      const delta = typeof data.delta === "string" ? data.delta : "";
      if (delta) return platformEvent(graph, "assistant.message.delta", { status: "RUNNING", delta });
    }
    return undefined;
  }
  if (eventType === "agent_updated_stream_event") {
    const agent = agentLookup(value.agent ?? value.newAgent ?? value.new_agent);
    return platformEvent(graph, "agent.changed", {
      status: "RUNNING",
      message: `已切换至${agent?.name ? `“${agent.name}”` : "另一个"}智能体执行`,
      agent,
    });
  }
  if (eventType !== "run_item_stream_event") return undefined;
  const name = String(value.name ?? "");
  const item = asRecord(value.item);
  const rawItem = asRecord(item.rawItem ?? item.raw_item);
  const agent = agentLookup(value.agent ?? item.agent ?? item.sourceAgent ?? item.source_agent);
  const rawToolCode = text(rawItem.name, item.name, rawItem.type);
  const toolCode = isGenericOutputType(rawToolCode) ? undefined : rawToolCode;
  const toolIdentity = toolLookup(toolCode);
  const callId = text(rawItem.callId, rawItem.call_id, item.callId, item.call_id);
  const platformToolKey = text(toolIdentity?.key, toolIdentity?.code, toolCode);
  const displayName = toolIdentity?.name
    ?? BUILTIN_TOOL_DISPLAY_NAMES[platformToolKey ?? ""]
    ?? platformToolKey
    ?? "未命名工具";
  const ext: JsonRecord = {
    activityCode: callId ?? fallbackActivityCode(name, platformToolKey),
    activityType: name.includes("tool") ? "TOOL_CALL" : "AGENT_HANDOFF",
    toolKey: platformToolKey,
    // Compatibility alias for consumers created before key/name identity.
    toolCode: platformToolKey,
    toolName: displayName,
    callId,
  };
  if (name.includes("tool") && platformToolKey) ext.activityName = `调用工具：${displayName}`;
  else if (name.includes("handoff")) ext.activityName = "智能体协作交接";
  if (name.includes("tool") && platformToolKey) {
    ext.callReason = toolCallReason(platformToolKey, displayName);
  }
  if (toolIdentity?.version !== undefined) ext.toolVersion = toolIdentity.version;
  const inputSummary = summary(rawItem.arguments ?? rawItem.input ?? item.input);
  const outputSummary = summary(item.output ?? item.result ?? rawItem.output ?? rawItem.result);
  if (inputSummary) ext.inputSummary = inputSummary;
  if (outputSummary) ext.outputSummary = outputSummary;
  if (name === "tool_called" || name === "tool_search_called") {
    return platformEvent(graph, "tool.started", { status: "RUNNING", message: "开始调用工具", agent, ext });
  }
  if (name === "tool_output" || name === "tool_search_output_created") {
    const output = decodedRecord(item.output);
    const outputStatus = String(output.status ?? "").toUpperCase();
    const failed = output.success === false
      || output.valid === false && Array.isArray(output.errors) && output.errors.length > 0
      || ["FAILED", "ERROR", "CANCELLED"].includes(outputStatus);
    return platformEvent(graph, failed ? "tool.failed" : "tool.completed", {
      status: failed ? "FAILED" : "SUCCESS",
      message: failed ? "工具调用失败" : "工具调用完成",
      agent,
      ext,
    });
  }
  if (name === "handoff_requested") {
    return platformEvent(graph, "handoff.requested", { status: "RUNNING", message: "正在将任务交接给协作智能体", agent, ext });
  }
  if (name === "handoff_occured" || name === "handoff_occurred") {
    return platformEvent(graph, "handoff.completed", { status: "SUCCESS", message: "协作智能体交接完成", agent, ext });
  }
  return undefined;
}

function toolCallReason(toolKey: string, displayName: string): string {
  const builtInReason = BUILTIN_TOOL_CALL_REASONS[toolKey];
  if (builtInReason) return builtInReason;
  if (toolKey.startsWith("ask_") && displayName) {
    return `当前任务需要“${displayName}”的专业能力，因此发起协作。`;
  }
  if (displayName) {
    return `当前步骤需要通过“${displayName}”补充、验证或执行任务所需信息。`;
  }
  return "当前步骤需要调用工具补充、验证或执行任务所需信息。";
}

function fallbackActivityCode(eventName: string, toolCode: string | undefined): string {
  const identity = toolCode ?? "agent";
  if (eventName.includes("tool")) return `tool:${identity}`;
  if (eventName.includes("handoff")) return `handoff:${identity}`;
  return `activity:${identity}`;
}

function isGenericOutputType(value: string | undefined): boolean {
  const normalized = value?.toLowerCase() ?? "";
  return normalized.endsWith("_output") || normalized === "function_call_output" || normalized === "tool_output";
}

function summary(value: unknown): string | undefined {
  if (value === undefined || value === null) return undefined;
  let normalized: string;
  if (typeof value === "string") {
    normalized = value.trim();
  } else {
    try {
      normalized = JSON.stringify(value);
    } catch {
      normalized = String(value);
    }
  }
  if (!normalized) return undefined;
  const redacted = normalized
    .replace(/\bbearer\s+[a-z0-9._~+/=-]+/gi, "Bearer [已隐藏]")
    .replace(/\bsk-[a-z0-9_-]+\b/gi, "[已隐藏]")
    .replace(
      /(["']?(?:authorization|api[ _-]?key|secret|password|access[_-]?token|refresh[_-]?token|token)["']?\s*[:=]\s*["']?)([^"'\s,;}]+)/gi,
      "$1[已隐藏]",
    );
  return redacted.slice(0, 1000);
}

function decodedRecord(value: unknown): JsonRecord {
  if (typeof value !== "string") return asRecord(value);
  try {
    return asRecord(JSON.parse(value) as unknown);
  } catch {
    return {};
  }
}

function asRecord(value: unknown): JsonRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value as JsonRecord : {};
}

function text(...values: unknown[]): string | undefined {
  for (const value of values) {
    if (value === undefined || value === null) continue;
    const normalized = String(value).trim();
    if (normalized) return normalized;
  }
  return undefined;
}
