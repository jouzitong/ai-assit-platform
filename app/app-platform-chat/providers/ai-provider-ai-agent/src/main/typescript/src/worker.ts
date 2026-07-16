import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

import { compileSnapshot } from "./compiler.js";
import { JsonRecord } from "./contracts.js";
import { platformEvent } from "./events.js";
import { runGraph } from "./runtime.js";

export async function processPayload(input: JsonRecord, dryRun = false): Promise<JsonRecord[]> {
  const frames: JsonRecord[] = [];
  const graph = compileSnapshot(input);
  if (dryRun) {
    frames.push({
      protocolVersion: "2.0",
      type: "result",
      data: {
        status: "COMPILED",
        rootAgent: graph.rootAgent,
        agentCount: graph.agents.length,
        agents: graph.agents,
      },
    });
    return frames;
  }
  const result = await runGraph(graph, (frame) => frames.push(frame));
  frames.push({ protocolVersion: "2.0", type: "result", data: result });
  return frames;
}

async function main(): Promise<void> {
  let graph: ReturnType<typeof compileSnapshot> | undefined;
  try {
    const text = await readStdin();
    const input = JSON.parse(text) as JsonRecord;
    graph = compileSnapshot(input);
    const dryRun = process.env.AI_AGENT_DRY_RUN === "1";
    const frames = dryRun
      ? await processPayload(input, true)
      : [
          ...await collectRuntimeFrames(graph),
        ];
    for (const frame of frames) emit(frame);
  } catch (error) {
    const message = safeMessage(error);
    if (graph) {
      emit(platformEvent(graph, "round.failed", {
        status: "FAILED",
        message,
        ext: { errorType: error instanceof Error ? error.name : "Error" },
        frameType: "error",
      }));
    } else {
      emit({ protocolVersion: "2.0", type: "error", eventType: "round.failed", status: "FAILED", message });
    }
    process.exitCode = 1;
  }
}

async function collectRuntimeFrames(graph: ReturnType<typeof compileSnapshot>): Promise<JsonRecord[]> {
  const frames: JsonRecord[] = [];
  const result = await runGraph(graph, (frame) => {
    emit(frame);
  });
  frames.push({ protocolVersion: "2.0", type: "result", data: result });
  return frames;
}

async function readStdin(): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of process.stdin) chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  return Buffer.concat(chunks).toString("utf8");
}

function emit(frame: JsonRecord): void {
  process.stdout.write(`${JSON.stringify(frame)}\n`);
}

function safeMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error);
  return message
    .replace(/bearer\s+[^\s,;]+/gi, "Bearer [REDACTED]")
    .replace(/\bsk-[A-Za-z0-9_-]{8,}\b/g, "[REDACTED_OPENAI_KEY]")
    .slice(0, 1000);
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : undefined;
if (invokedPath && fileURLToPath(import.meta.url) === invokedPath) {
  await main();
}
