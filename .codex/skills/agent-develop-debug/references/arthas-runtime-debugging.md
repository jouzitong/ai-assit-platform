# Arthas runtime debugging

Use Arthas after source, configuration, logs, and database evidence leave a live-JVM question. The wrapper is intended primarily for local/development/test use, requires the environment to be named explicitly, and attaches only to a discovered ai-assit-platform service JVM.

## Preconditions

1. Name the environment and service.
2. Preserve a trace/request/run ID and reproduction trigger where method observation is needed.
3. Identify one exact fully qualified class and method from current source and `sc`/`sm` evidence.
4. Confirm the process owner matches the current user.
5. State the expected evidence and whole-command timeout.
6. Avoid peak load and broad patterns; production requires explicit authorization plus `--environment production --confirm-production`.

List targets without attaching:

```bash
python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . list
```

The resolver first checks the project's service port, but accepts that PID only when its `jps`/process command also contains the exact service main-class or boot-module marker. It also collects instances found by the same exact markers away from the default port. This rejects ordinary unrelated IDE/tool JVMs; identity is still marker-based, so inspect the displayed service, PID, source and main class before attaching. If multiple instances exist, select one of the discovered PIDs with `--pid`.

## Diagnostic ladder

### JVM and threads

```bash
python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . --environment local \
  overview --service chat --top 5

python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . --environment local \
  thread --service chat --top 10
```

Use for CPU, GC, thread count, blocking, and hot-stack triage. Capture only the relevant stack frames in the result.

### Loaded class and method

```bash
python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . --environment local \
  class-info --service chat --class-name ai.platform.ExactClass

python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . --environment local \
  methods --service chat --class-name ai.platform.ExactClass --method-name exactMethod
```

Use this before enhancement to detect stale binaries, unexpected classloaders, or a wrong method target.

### Call timing and callers

```bash
python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . --environment local --timeout 90 \
  trace --service chat --class-name ai.platform.ExactClass --method-name exactMethod \
  --count 3 --min-cost-ms 100

python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . --environment local --timeout 90 \
  stack --service chat --class-name ai.platform.ExactClass --method-name exactMethod --count 3
```

The wrapper rejects wildcard class/method targets, caps execution count at five, sets max matched classes to one, and terminates the whole batch on timeout.

### Bounded value observation

Prefer `cost`. Use exception/parameter/return observation only after confirming it cannot expose sensitive or personal data:

```bash
python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . --environment local --timeout 60 \
  watch --service chat --class-name ai.platform.ExactClass --method-name exactMethod \
  --expression exception --allow-value-output --count 1 --depth 1
```

Never observe provider credentials, authorization headers, cookies, complete prompts, uploaded content, complete SQL results, or user records. Output redaction only catches common labeled credential shapes.

### Spring runtime state

```bash
python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . --environment local \
  spring-property --service chat --property-name server.port

python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . --environment local \
  spring-bean --service chat --bean-name conversationExecutionService --mode contains
```

The wrapper blocks sensitive property names and uses `containsBean`/`containsLocalBean`/`containsBeanDefinition`/`getAliases`; it never calls `getBean()` and therefore does not intentionally initialize a Bean.

## Safety and cleanup

- Use a loopback-only ephemeral telnet port and disable the HTTP port.
- Disable dump, heapdump, compiler, class-redefinition, arbitrary OGNL, environment/system-property dump, profiler/JFR, and option mutation commands.
- Append `reset;stop` to every batch so bytecode enhancement is removed and the Arthas server exits.
- On client timeout or error, attempt `reset;stop` through `arthas-client.jar` and report cleanup status.
- If cleanup fails, do not assume enhancement is gone; reconnect to the recorded target/port or restart only the owned service during an approved window.
- Do not use `tt` through this wrapper. It retains method data in JVM memory and supports replay; use it only under a separately reviewed diagnostic plan with bounded `-n`, `-m 1`, data-sensitivity review, and explicit cache cleanup.

Use `--dry-run` to validate target/action/limits without attaching. A dry run can succeed while the service is stopped; it does not prove runtime attach or command behavior.

Official behavior references:

- Arthas batch mode: <https://arthas.aliyun.com/en/doc/batch-support.html>
- Arthas command index and bytecode-enhancement warning: <https://arthas.aliyun.com/en/doc/commands.html>
- Trace limits and conditions: <https://arthas.aliyun.com/en/doc/trace.html>
- Time Tunnel memory/limit cautions: <https://arthas.aliyun.com/en/doc/tt.html>
