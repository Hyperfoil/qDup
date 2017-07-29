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

## Scripts
A `Script` is tree of commands that run one at a time. Each command accepts
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
Arguments are passed as a list of objects (using either bracket or dash notation). Arguments
are matched based on their position in the list and the declared order for the command.
3. `- command : { argumentName: argumentValue, ...}`
Arguments are explicitly mapped to the command's declared argument names.
This is the least ambiguous but most verbose and is rarely necessary

### Available commands
* __abort: <message>__
Abort the current run and log the message
* __code: <className>__
create an instance of `className` which implements `Code` using the
default constructor and execute the `run(...)` method.
Note: This command is best suited for the Java API. Please share your
use case if you find you need this command for the YAML API and we can
see if a new command is warranted
* __countdown: <name> <initial>__
decrease a `name` counter which starts with `initial`.
Child commands will be invoked each time after `name` counter reaches 0.
* __ctrlC:__
send ctrl+C interrupt to the remote shell. This will kill
any currently running command (e.g. `sh tail -f /tmp/server.log`)
* __download: <path> ?<destination>__
download `path` from the connected
host and save the output to the  run output path + `destination`
* __echo:__
log the input to console
* __log: <message>__
log `message` to the run log
* __read-state: <name>__
read the current value of the named state variable
and pass it as input to the next command
* __regex: <pattern>__
try to match the previous output to a regular
expression and add any named capture groups to the current state at
the named scope.
* __repeat-until: <name>__
repeat the child commands until `name` is signalled.
Be sure to add a `sleep` to prevent a tight loop and pick a `name` that
is signalled in all runs (e.g. be careful of error conditions)
* __set-state <name> ?<value>__
set the named state attribute to `value`
if present or to the input if no value is provided
* __script: <name>__
Invoke the `name` script as part of the current script
* __sh: <command>__
Execute a remote shell command
* __signal: <name>__
send one signal for "name." Runs parse all script :
host associations to calculate the expected number of `signal`s for each
`name`. All `waitFor` will wait for the expected number of `signal`
* __sleep: <ms>__
pause the current script for the given number of milliseconds
* __queue-download: <path> ?<destination>__
queue the download action for
after the run finishes. The download will occur if the run completes
or aborts
* __waitFor: <name>__
pause the current script until "name" is fully signaled
* __xpath: <path>__
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
 ...
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
__hosts__ a map of hostShortName : host
```YAML
hosts:
 - local : me@localhost
```
__roles___ a map of roleName : a hosts list and at least one of: setup-scripts, run-scripts,
cleanup-scripts. If a host list is not specified then the role applies
to all hosts in the configuration. In this case it might be best to name
the role ALL or some other clear indication that it applies to all hosts
```YAML
roles:
  test:
    hosts:
     - local
    run-scripts:
     - test-script
  ALL:
    setup-scripts:
     - other-script
```
You can also use `setup-scripts`, `run-scripts`, `cleanup-scripts` as
top level configuration entries to apply scripts to all hosts but using
an `ALL` role helps keep all the host to script mappings under one location

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
2. YAML does not allow a `key :` to have both a value and child mappings.
This is a problem when a command has arguemnts but also child commands
or watchers. We get around this by using double dash on the child command
/ watcher.
```YAML
 - command: argument
 - - child-command: child-arguments
 - command: argument
 - - watch:
     - command: argument
```

## YAML support
 - [x] name
 - [x] scripts
   - [x] commands
   - [x] watchers
   - [x] with
 - [x] hosts
   - [x] shortname : user@host:port
   - [x] shortname : {user,host,port}
   - [ ] user@host:post ?
   - [ ] {user,host,port} ?
 - [ ] roles
   - [x] role names
   - [x] hosts by shortname
   - [ ] host declaration (e.g. `user@host:port`) in role:hosts
         ? use Provider<Host>
 - [ ] setup-scripts
   - [x] script name (applies to all
   - [ ] {select,script} to filer hosts
 - [ ] run-scripts
   - [ ] script name (applies to all
   - [ ] {select,script} to filer hosts
 - [ ] cleanup-scripts
   - [ ] script name (applies to all
   - [ ] {select,script} to filer hosts
 - [ ] state
   - [ ] run
   - [ ] host
   - [ ] script
 - [ ] coordinator
   - [ ] latches
   - [ ] counters