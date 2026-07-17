import { pathToFileURL } from "node:url";

const RESULT_PREFIX = "__AI_TOOL_RESULT__";
const module = await import(`${pathToFileURL(process.argv[2]).href}?run=${Date.now()}`);
const sdkTool = Object.values(module).find(
  (value) => value?.type === "function" && typeof value?.invoke === "function",
);

if (process.argv[3] === "--describe") {
  if (sdkTool) {
    process.stdout.write(`${RESULT_PREFIX}${JSON.stringify({
      name: sdkTool.name,
      description: sdkTool.description,
      inputSchema: sdkTool.parameters ?? {},
      outputSchema: {},
    })}\n`);
  } else if (typeof module.run === "function") {
    process.stdout.write(`${RESULT_PREFIX}${JSON.stringify({ inputSchema: {}, outputSchema: {} })}\n`);
  } else {
    throw new Error("JavaScript Tool must use tool({...}) or export run(args, context)");
  }
  process.exit(0);
}

let input = "";
for await (const chunk of process.stdin) input += chunk;
const request = input.trim() ? JSON.parse(input) : {};
let result;
if (sdkTool) {
  result = await sdkTool.invoke(
    { context: request.context ?? {} },
    JSON.stringify(request.arguments ?? {}),
  );
} else if (typeof module.run === "function") {
  result = await module.run(request.arguments ?? {}, request.context ?? {});
} else {
  throw new Error("JavaScript Tool must use tool({...}) or export run(args, context)");
}
process.stdout.write(`${RESULT_PREFIX}${JSON.stringify(result ?? null)}\n`);
