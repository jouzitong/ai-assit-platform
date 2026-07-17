import { pathToFileURL } from "node:url";

const RESULT_PREFIX = "__AI_TOOL_RESULT__";
let input = "";
for await (const chunk of process.stdin) input += chunk;
const request = input.trim() ? JSON.parse(input) : {};
const module = await import(`${pathToFileURL(process.argv[2]).href}?run=${Date.now()}`);
if (typeof module.run !== "function") {
  throw new Error("JavaScript Tool must export run(args, context)");
}
const result = await module.run(request.arguments ?? {}, request.context ?? {});
process.stdout.write(`${RESULT_PREFIX}${JSON.stringify(result ?? null)}\n`);
