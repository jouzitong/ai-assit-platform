// Execution shim for the OpenAI Agents SDK tool({...}) authoring contract.
export function tool(definition) {
  if (!definition || typeof definition !== "object" || typeof definition.execute !== "function") {
    throw new TypeError("tool({...}) requires an execute function");
  }
  return {
    ...definition,
    type: "function",
    async invoke(context, input) {
      const argumentsValue = typeof input === "string" ? JSON.parse(input || "{}") : (input ?? {});
      return definition.execute(argumentsValue, context);
    },
  };
}
