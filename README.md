# Ssh
Coordinate multiple shell interactions to run tests in a lab environment
## Introduction
Running benchmarks (e.g. specjms) requires opening several
terminals and monitoring the output of one terminal to start
a command in another terminal. This project provides a way to script
multiple shells and coordinate between them.

Each connection starts by executing a sequence of commands
in a `Script`. All `Scripts` start at the same time but `waitFor`,
`signal`, and `repeat-until` commands help coordinate between `Scripts`

## TODO
 - waitFor when already at 0 does not dispatch?
 - abort doesn't stop waitFor

## Scripts
A `Script` a is tree of commands that run one at a time. Each command accepts
a `String` input from the previous sibling (or parent if no sibling
is available) as well as the current `Context`. Commands pass execution
to their children commands by default (depth first execution) but they can
skip their children commands when appropriate. The `regex` command, for
example, will skip its children commands if the pattern does not match
the input. The `abort` command is a special case that will not pass
execution because it ends the execution.

## Commands
Commands can be used in one of 3 ways:

1. `- command : <argumentString>`
Commands that support multiple arguments will try and parse the
argumentString to identify the appropriate arguments.
2. `- command : [<argument>, <argument>, ...]`
Arguments are passed as a list (using either bracket or dash notation). Arguments
are matched based on their position in the list and the declared order for the command.
Missing arguments will be passed a null
3. `- command : { argumentName: argumentValue, ...}`
Arguments are explicitly mapped to the command's declared argument names.
This is the least ambiguous but most verbose and is rarely necessary.

### Available commands
* `abort: <message>`
Abort the current run and log the message
* `code: <className>`
create an instance of `className` which implements `Code` using the
default constructor and execute the `run(...)` method.
Note: This command is best suited for the Java API. Please share your
use case if you find you need this command for the YAML API and we can
see if a new command is warranted
* `countdown: <name> <initial>`
decrease a `name` counter which starts with `initial`.
Child commands will be invoked each time after `name` counter reaches 0.
* `ctrlC:`
send ctrl+C interrupt to the remote shell. This will kill
any currently running command (e.g. `sh: tail -f /tmp/server.log`)
* `download: <path> ?<destination>`
download `path` from the connected host and save the output to the
run output path + `destination`
* `echo:`
log the input to console
* `log: <message>`
log `message` to the run log
* `read-state: <name>`
read the current value of the named state variable
and pass it as input to the next command
* `regex: <pattern>`
try to match the input to a regular expression and add any named
capture groups to the current state at the named scope.
* `repeat-until: <name>`
repeat the child commands until `name` is signalled.
Be sure to add a `sleep` to prevent a tight loop and pick a `name` that
is signalled in all runs (e.g. be careful of error conditions)
* `set-state <name> ?<value>`
set the named state attribute to `value`
if present or to the input if no value is provided
* `script: <name>`
Invoke the `name` script as part of the current script
* `sh: <command>`
Execute a remote shell command
* `signal: <name>`
send one signal for `name` Runs parse all script and
host associations to calculate the expected number of `signals` for each
`name` and `waitFor` will wait for the expected number of `signal`
* `sleep: <ms>`
pause the current script for the given number of milliseconds
* `queue-download: <path> ?<destination>`
queue the download action for after the run finishes. The download will
occur if the run completes or aborts
* `waitFor: <name>`
pause the current script until `name` is fully signaled
* `xpath: <path>`
This is an overloaded command that can perform an xpath
  based search or modification. Path takes the following forms
   - `file>xpath` - finds all xpath matches in file and passes them as
   input to the next command
   - `file>xpath == value` - set xpath matches to the specified value
   - `file>xpath ++ value` - add value to the xpath matches
   - `file>xpath --` - delete xpath matches from their parents
   - `file>xpath @key=value` - add the key=value attribute to xpath matches

## Watching
Some commands (e.g. sh commands) can provide updates during execution.
A child command with the `watch` prefix will receive each new line of
output from the parent command as they are available (instead of after
the parent completes). This is mostly used to monitor output with `regex`
and to subsequently call `signal STATE` or `ctrlC` the command when a
condition is met.
```YAML
 - sh: tail -f /tmp/server.log
 - - watch:
     - regex: .*?FATAL.*
       - ctrlC:
       - abort: FATAL error
```

Note: `sh`, `waitFor`, and `repeat-until` cannot be used when watching
a command because they can block the exection of other watchers.

## State
State is the main way to introduce variability into a run. Commands can
reference state variables by name with `${{name}}` and `regex` can define
new entries with standard java regex capture groups `(?<name>.*)`
```YAML
 - sh: tail -f /tmp/server.log
 - - watch:
     - regex : ".*? WFLYSRV0025: (?<eapVersion>.*?) started in (?<eapStartTime>\\d+)ms.*"
     - - log : eap ${{eapVersion}} started in ${{eapStartTime}}
       - ctrlC:
```
## YAML
The best way to use the tool is to build with `gradle jar` and use the executable
jar to run tests based on yaml configuration. The yaml format supports
the following:

__name__ the name of the benchmark
```YAML
name : test
```
__scripts__ a map of scriptName : command tree
```YAML
scripts:
  test-script :
    - log: "${{greeting}}, world"
```
__hosts__ a map of name : host
```YAML
hosts:
 - local : me@localhost:22
 - server :
   - username: user
   - hostname: server
   - port: 22 # port is optional and will default to 22
```
__roles___ a map of roleName : a hosts list and at least one of:
setup-scripts, run-scripts, cleanup-scripts. If the hosts list is missing
then the role is applied to all hosts in the run. It is a best practice
to use a role name that makes it clear it will apply ALL hosts
```YAML
roles:
  test:
    hosts:
     - local
    run-scripts:
     - test-script
  ALL:
    setup-scripts:
     - some-script
```


__states__ a nested map of name : value pairs used to inject variables
into the run configuration. The top level entry must be `run` then the
children of run are either a name : value pair or the name of a host.
Hosts can have name : value pairs or the name of a script as children.
Scripts are the lowest level in the state tree and therefore they can
only have name : value pairs as children
```YAML
states:
  run:
    foo : bar
    local: # host reference
      biz : buzz
      test-script:
        greeting: hello
      other-script:
        greeting: hola
```
script commands can reference state variables by surrouning the variable
names with `${{` `}}` (e.g. `${{greeting}}`)

### YAML syntax support
We use snakeyaml to parse the yaml configuration according to the yaml
spec but this leads to some unique corner cases with how the scripts
can be structured.
1. YAML uses `:` to separate a key and value pair so any arguments with
`:` in the value need to be wrapped in quotes.
2. YAML does not allow a `key : value` to have a child mappings (e.g. `watch`).
This is a problem when a command has arguments and child commands
or watchers. We get around this by using double dash on the child command
or watcher. In YAML this is technically a sibling with a nested list but
the parser will associate the nested list as a child of the previous
command.
```YAML
 - command: argument
 - - child-command: child-arguments
 - command: argument
 - - watch:
     - command: argument
```

## Building
> gradle jar

## Running Example
