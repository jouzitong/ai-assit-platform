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


def main():
    request = json.load(sys.stdin)
    module = load_module(sys.argv[1])
    handler = getattr(module, "run", None)
    if not callable(handler):
        raise RuntimeError("Python Tool must define run(arguments, context)")
    result = handler(request.get("arguments") or {}, request.get("context") or {})
    if inspect.isawaitable(result):
        result = asyncio.run(result)
    print(RESULT_PREFIX + json.dumps(result, ensure_ascii=False, separators=(",", ":")))


if __name__ == "__main__":
    main()
