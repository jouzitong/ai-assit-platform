export type JsonRecord = Record<string, unknown>;

export interface NormalizedRun extends JsonRecord {
  runId: string;
  requestId: string;
  traceId?: string;
  sessionCode?: string;
  roundCode?: string;
  input: string;
  maxTurns?: number;
}

export interface NormalizedPayload extends JsonRecord {
  protocolVersion: "2.0";
  sourceProtocolVersion: string;
  run: NormalizedRun;
  rootAgent: JsonRecord;
  agentGraph: JsonRecord[];
  resolvedCapabilities: JsonRecord;
  workflowSnapshot: JsonRecord;
  snapshotHash?: string;
  model?: string;
  messages: JsonRecord[];
  responseFormat: JsonRecord;
  options: JsonRecord;
}

export interface CompiledAgentLink {
  target: string;
  toolName?: string;
  description?: string;
}

export interface SkillMetadata {
  ref: string;
  name: string;
  description: string;
  contentHash?: string;
}

export interface CompiledAgent {
  key: string;
  code: string;
  version?: number;
  name: string;
  description: string;
  instructions: string;
  model?: string;
  modelSettings: JsonRecord;
  tools: string[];
  skills: SkillMetadata[];
  agentTools: CompiledAgentLink[];
  handoffs: CompiledAgentLink[];
}

export interface CompiledGraph {
  protocolVersion: "2.0";
  payload: NormalizedPayload;
  rootAgent: string;
  maxTurns: number;
  maxAgentDepth: number;
  agents: CompiledAgent[];
  gatewayTools: Record<string, GatewayToolDescriptor>;
}

export interface GatewayToolDescriptor extends JsonRecord {
  code: string;
  version: number;
  sdkName: string;
  name: string;
  description: string;
  adapterType: string;
  inputSchema: JsonRecord;
  timeoutMs: number;
}

export interface PlatformEvent extends JsonRecord {
  protocolVersion: "2.0";
  type: "event" | "error";
  eventType: string;
  runId: string;
  requestId: string;
  traceId?: string;
  sessionCode?: string;
  roundCode?: string;
  agentCode?: string;
  agentVersion?: number;
  agentName?: string;
  status?: string;
  message?: string;
  delta?: string;
  timestamp: string;
  source: "OPENAI_AGENTS_TYPESCRIPT";
  ext: JsonRecord;
}
