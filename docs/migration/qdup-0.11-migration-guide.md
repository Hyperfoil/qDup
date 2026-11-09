# qDup 0.11 Migration Guide

## Breaking Changes in qDup 0.11

qDup 0.11 introduces a breaking change in how commands handle input/output flow. This document explains the change and provides examples to help you migrate your scripts.

## The Key Change: Input Flow in `then` Blocks

**qDup passes the output of one command as the input of the next command.**

In qDup 0.11, the `then` keyword is used to interrupt the default flow so that multiple commands can receive the input from the parent command without affecting each other. This is necessary for commands that create output (e.g., `json`).

**Key principle:** Actions nested under a command's `then` block receive their input from that parent command, not from sibling commands at the same level.

### Rule Summary

✅ **Works in both 0.10 and 0.11:**
```yaml
- sh: command
  then:
  - set-state: RESULT
```
The `set-state` gets its input from the `sh` command because it's nested under the `sh`'s `then` block.

✅ **Also works in both 0.10 and 0.11 (if NOT under another command's `then`):**
```yaml
- sh: doSomething.sh
  regex: findInOutput
```
The `regex` receives output from `sh` because they are at the same level and not nested under another command's `then`.

❌ **Does NOT work in 0.11 (but worked in 0.10):**
```yaml
- regex: something
  then:
  - sh: command
  - set-state: RESULT
```
The `set-state` will get its input from the `regex`, NOT from the `sh` command, because they are siblings under the same `then` block.

**The fix:** Introduce another `then` so the previous command becomes the parent:
```yaml
- regex: something
  then:
  - sh: command
    then:
    - set-state: RESULT
```

**NOTE:** This also impacts commands under an `else` block.

## Migration Examples

### Example 1: Sequential Commands Under a `then` Block

**Old Pattern (qDup < 0.11):**
```yaml
- sh: scp ./file.war ${{SCP_PATH}}/file.war
- regex: No such file or directory
  then:
  - abort: failed to build file.war
```

**New Pattern (qDup 0.11+):**
```yaml
- sh: scp ./file.war ${{SCP_PATH}}/file.war
  then:
  - regex: No such file or directory
    then:
    - abort: failed to build file.war
```

**Why?** In 0.11, the `regex` must be nested under the `sh`'s `then` block to receive the `sh` command's output.

### Example 2: Multiple Sequential Commands

**Old Pattern (qDup < 0.11):**
```yaml
- read-state: ${{SCP_PATH}}
  then:
  - sh: scp ./file1.war ${{SCP_PATH}}/file1.war
  - regex: No such file or directory
    then:
    - abort: failed to build file1.war
  - sh: scp ./file2.war ${{SCP_PATH}}/file2.war
  - regex: No such file or directory
    then:
    - abort: failed to build file2.war
```

**New Pattern (qDup 0.11+):**
```yaml
- read-state: ${{SCP_PATH}}
  then:
  - sh: scp ./file1.war ${{SCP_PATH}}/file1.war
    then:
    - regex: No such file or directory
      then:
      - abort: failed to build file1.war
  - sh: scp ./file2.war ${{SCP_PATH}}/file2.war
    then:
    - regex: No such file or directory
      then:
      - abort: failed to build file2.war
```

**Why?** Each `regex` must be nested under its corresponding `sh` command's `then` block to receive that command's output.

### Example 3: JSON Command with Multiple Outputs

**Old Pattern (qDup < 0.11):**
```yaml
- sh: makeJson.sh
  then:
  - json: $.first.path
    then:
    - set-state: firstPath
  - json: $.second.path
    then:
    - set-state: secondPath
```

**New Pattern (qDup 0.11+):**
```yaml
- sh: makeJson.sh
  then:
  - json: $.first.path
    then:
    - set-state: firstPath
  - json: $.second.path
    then:
    - set-state: secondPath
```

**Why?** This pattern actually works in both versions! The `json` commands are siblings under the `sh`'s `then` block, so both receive the `sh` output. This is the intended use case for `then` - allowing multiple commands to process the same parent output.

### Example 4: Command with State Setting

**Works in both 0.10 and 0.11:**
```yaml
- sh: echo $(($(date "+%s%N" -d "$(head -1 /tmp/first_response.txt)")/1000000))
  then:
  - set-state: RUN.LOOP_SERVICE_FIRST_RESP_TIMESTAMP
```

**Why?** The `set-state` is nested under the `sh`'s `then` block, so it receives the `sh` command's output in both versions.

### Example 5: Conditional Execution

**Old Pattern (qDup < 0.11):**
```yaml
- sh: "[[ -f ${{RAPL_PATH}}/rapl-plot ]] || echo GETRAPL;"
- regex: GET
  then:
  - sh: rm -Rf ${{RAPL_PATH}}
  - sh: mkdir -p ${{RAPL_PATH}}
  - sh: cd ${{RAPL_PATH}}
  - sh: git clone https://github.com/example/repo.git
```

**New Pattern (qDup 0.11+):**
```yaml
- sh: "[[ -f ${{RAPL_PATH}}/rapl-plot ]] || echo GETRAPL;"
  then:
  - regex: GET
    then:
    - sh: rm -Rf ${{RAPL_PATH}}
    - sh: mkdir -p ${{RAPL_PATH}}
    - sh: cd ${{RAPL_PATH}}
    - sh: git clone https://github.com/example/repo.git
```

**Why?** The `regex` must be nested under the first `sh`'s `then` block to check its output. The subsequent `sh` commands are sequential and don't need further nesting.

## Key Principles

1. **Default flow**: qDup passes the output of one command as the input of the next command (when at the same level and not under a `then` block).

2. **`then` interrupts the flow**: The `then` keyword makes child commands receive input from the parent command, not from sibling commands.

3. **Multiple commands under `then`**: When multiple commands are under a `then` block, they all receive input from the parent command. This is useful for commands like `json` that need to process the same input multiple times.

4. **Nesting for sequential processing**: If you need command B to process the output of command A (when both are under a `then` block), nest command B under command A's own `then` block.

5. **`else` blocks behave the same**: Commands under `else` blocks follow the same input flow rules as `then` blocks.

## Migration Checklist

When migrating to qDup 0.11:

- [ ] Identify commands under `then` or `else` blocks that need to process output from sibling commands
- [ ] For each such command, nest it under its own `then` block under the command whose output it needs
- [ ] Pay special attention to patterns like `sh` followed by `regex` or `set-state` under a parent `then` block
- [ ] Remember that commands like `json` that need the same input multiple times should remain as siblings under the parent's `then`
- [ ] Test your scripts with qDup 0.11 to verify correct behavior

## Finding Files to Update

Use this command to find YAML files containing `- sh` commands:

```bash
find . -name "*.yaml" -type f -exec grep -l -- "- sh" {} \;
```
