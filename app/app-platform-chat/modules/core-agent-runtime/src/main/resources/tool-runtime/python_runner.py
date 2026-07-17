import asyncio
import importlib.util
import inspect
import json
import sys

RESULT_PREFIX = "__AI_TOOL_RESULT__"


def load_module(source_path):
    spec = importlib.util.spec_from_file_location("managed_tool", source_path)
    if spec is None or spec.loader is None:
        raise RuntimeError("Unable to load Tool source")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def find_sdk_tool(module):
    for value in vars(module).values():
        if getattr(value, "_managed_function_tool", False):
            return value
    return None


def main():
    module = load_module(sys.argv[1])
    sdk_tool = find_sdk_tool(module)
    if len(sys.argv) > 2 and sys.argv[2] == "--describe":
        if sdk_tool is not None:
            result = sdk_tool._managed_describe()
        else:
            handler = getattr(module, "run", None)
            if not callable(handler):
                raise RuntimeError("Python Tool must use @function_tool or define run(arguments, context)")
            result = {"inputSchema": {}, "outputSchema": {}}
        print(RESULT_PREFIX + json.dumps(result, ensure_ascii=False, separators=(",", ":")))
        return

    request = json.load(sys.stdin)
    if sdk_tool is not None:
        result = sdk_tool._managed_invoke(
            request.get("arguments") or {},
            request.get("context") or {},
        )
        if inspect.isawaitable(result):
            result = asyncio.run(result)
        print(RESULT_PREFIX + json.dumps(result, ensure_ascii=False, separators=(",", ":")))
        return

    handler = getattr(module, "run", None)
    if not callable(handler):
        raise RuntimeError("Python Tool must use @function_tool or define run(arguments, context)")
    result = handler(request.get("arguments") or {}, request.get("context") or {})
    if inspect.isawaitable(result):
        result = asyncio.run(result)
    print(RESULT_PREFIX + json.dumps(result, ensure_ascii=False, separators=(",", ":")))


if __name__ == "__main__":
    main()
