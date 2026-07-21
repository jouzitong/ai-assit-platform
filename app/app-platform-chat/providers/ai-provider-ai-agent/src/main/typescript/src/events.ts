import { CompiledAgent, CompiledGraph, GatewayToolDescriptor, JsonRecord, PlatformEvent } from "./contracts.js";

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
  toolLookup: (sdkName: string | undefined) => GatewayToolDescriptor | undefined = () => undefined,
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
  const toolCode = text(rawItem.name, item.name, rawItem.type);
  const gatewayTool = toolLookup(toolCode);
  const callId = text(rawItem.callId, rawItem.call_id, item.callId, item.call_id);
  const ext: JsonRecord = {
    activityCode: callId ?? `${name}:${toolCode ?? "agent"}`,
    activityType: name.includes("tool") ? "TOOL_CALL" : "AGENT_HANDOFF",
    toolCode: gatewayTool?.code ?? toolCode,
    callId,
  };
  if (gatewayTool) ext.toolVersion = gatewayTool.version;
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
