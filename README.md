# qDup 
qDup allows shell commands to be *queued up* across multiple servers to coordinate performance tests.
It is designed to follow the same workflow as a user at a terminal so that commands can be performed with or without qDup.
Commands are grouped into re-usable Scripts that are mapped to different Hosts by Roles. 
qDup has 3 pre-defined phases for script execution to follow the usual performance test workflow: setup, run, cleanup.   
## Example
The main way to use qDup is with yaml configuration files passed to the executable jar. 
Let's look at a sample yaml.
```YAML
name: example                                # the name of the test we plan to run
scripts:                                     # scripts are the series of commands to run a test
  sync-time:                                 # the unique script name
  - sh: ntpdate -u time.google.com           # runs ntpdate in the remote shell
  widflyScript:                              # the name of a new script
  - sh: cd ${{WF_HOME}}                      # cd to the WF_HOME state variable
  - sh: rm ./standalone/log/*                # remove the old logs
  - queue-download: ./standalone/log         # save the files after the script stops
  - sh: ./bin/standalone.sh &                # starts wildfly as a background process
  - sleep: 1s                                # wait a second for the new log files
  - sh: cd ./standalone/log                  # change to the log directory
  - sh:                                      # inline maps help for commands with multiple arguments
      silent : true                          # omit output from the run log
      command: tail -f server.log            # tail server.log       
    watch:                                   # watch streams each line of output to the sub commands
    - regex: ".*?FATAL.*"                    # if the line contains FATAL
      then:
      - ctrlC                                # send ctrl+c to the shell (ends tail -f)
      - abort: WF error                      # abort the run with a message WF error
    - regex: ".*? started in (?<startTime>\\d+)ms.*"
      then:
      - log: startTime=${{startTime}}        # named regex groups are added as state variables
      - ctrlC:
      - signal: WF_STARTED                   # notify other scripts that wildfly started
  - wait-for: DONE                           # pause this script until DONE is signalled
    timer: 
      30m:                                   # wait 30m then run sub-commands if still waiting for DONE
      - abort: run took longer than 30 minutes
hosts:                            # qDup needs a list of all the hosts involved in the test
  local : me@localhost:22         # use local as an alias for me on my laptop
  server :                        # use server as an alias for labserver4
    username : root               # the username
    hostname : labserver4         # the dns hostname
    port: 2222                    # the ssh port on the server
roles:                            # roles are how scripts are applied to hosts
  wildfly:                        # unique name for the role
    hosts:                        # a list of hosts in this role
    - server
    setup-scripts:                # scripts run sequentially before the run stage
    run-scripts:                  # scripts run in parallel during the run stage
      - wildflyScript             # wildflyScript will run on each host
      - wildflyScript:            # run a second copy of widflyScript
          with:                   # with allows an override for state variables
           WF_HOME : /dev/wf-x    # WF_HOME will be different for this instance of wildflyScript
    cleanup-scripts:              # scripts run sequentially after the run stage
  ALL:                            # the ALL role automacically includes all hosts from other roles
    setup-scripts: [sync-time]    # run sync-time on all hosts during the setup stage
    run-scripts:
      ${{hostMonitoring}}         # a script name defined in a state variable
                                  # leaving hostMonitoring undefined means a script is not run
states:
  WF_HOME: /runtime/wf-11         # sets WF_HOME state variable visible to all scripts
  server:                         # variables only visible to scripts running on server
    UNUSED : value
```
The main workflow is broken into 3 stages: setup, run, cleanup

Setup scripts execute **sequentially** with a shared ssh session to help
capture all the changes and ensure a consistent state for the run stage.
Any environment changes to the setup ssh session will be copied to all
the run stage sessions.

Run scripts execute in parallel and will start with environment changes from setup.

Cleanup scripts sequentially execute with a shared ssh session to ensure
a consistent ending state.
They occur after any pending `queue-download` from the run stage
so it is safe to cleanup anything left on the hosts

### Running a yaml
trying to run `java -jar qDup.jar` without any arguments will list
the supported options for the jar. The only required options are to either
specify the base folder where qDup should create the run folder
(`-b /tmp`) or to specify the full path where qDup should save the run
files (`-B /tmp/myRun`) and a yaml file.

> java -jar qDup.jar -b /tmp test.yaml

This example shows only 1 yaml but you can also load helper yamls with
shared definitions (e.g. scripts or hosts)

> java -jar qDup.jar -b /tmp test.yaml hosts.yaml scripts.yaml

Remember to put your main yaml first because it will take naming precedence
over any scripts, state, or hosts that are defined in subsequent yaml.
Roles, are merged across all yaml based on name.

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
Commands can be used in one of two ways:

1. `- command : <argumentString>`
Commands that support multiple arguments will try and parse the
argumentString to identify the appropriate arguments.
3. `- command : { argumentName: argumentValue, ...}`
Arguments are explicitly mapped to the command's declared argument names.
This is the least ambiguous but most verbose and is rarely necessary.
All commands support the following keys:
* `with`
set custom state values for this command and all children commands
* `then`
a list of commands to run after the current command if it passes execution to its children
* `idle-timer`
set to `false` to disable the idle check or an expected duration to avoid unnecessary idle warnings
* `state-scan`
set to `false` to disable the state scanner that ensures all state references are defined or have a default before they are used.  
* `silent`
set to `true` to disable the default logging for the command
* `timer`, `on-signal`, `watch`
observe the execution of the command
* `prefix`, `suffix`, `separator`, `js-prefix`
change the state pattern indicator strings

### Available commands
* `abort: <message>`
Abort the current run and log the message. Aborted runs will still
downlaod any files from `queue-download` and will still run cleanup scripts
* `add-prompt: <prompt>`
Add another sequence of characters that indicates the previous `sh` command is done.
This is to support commands that change the prompt (e.g. jboss-cli)
* `countdown: <name> <initial>`
decrease a `name` counter which starts with `initial`.
Child commands will be invoked each time after `name` counter reaches 0.
* `ctrlC:`
send ctrl+c interrupt to the remote shell. This will kill
any currently running command (e.g. `sh: tail -f /tmp/server.log`)
* `ctrl/:`
send ctrl+/ to the remote shell.
* `ctrlU:`
send ctrl+u to the remote shell.
* `ctrlZ:`
send ctrl+z to the remote shell.
* `done:` signals that the current stage ended
and any remaining active commands should end (including `wait-for`).
Use this command if you have `wait-for` that may never be signaled
* `download: <path> ?<destination>`
download `path` from the connected host and save the output to the
run output path + `destination`
```yaml
download:
  path: /absoulte/path/to/file
  destination: path/relative/to/qdup/base/directory
```
* `echo:`
log the input to console
* `exec: <command>`
run a shell command in an independent connection similar to passing a command to ssh.
```yaml
exec:
  name: doSomething
  async: true #run the script without pausing the current script. Starts a new shell 
```
* `for-each: <variableName> [values]` re-run the children with `variableName`
set to each entry in `values` or the input from the previous command if `values`
is not provided. If the command acts in the input from the previous command it will 
try and identify the different value for `variableName` by checking for new lines, a json array, or a separated list.
```yaml
for-each:
  name: variableName #the variable name for children commands
  input: ${{input}} #reference to something iterable or it will use the command input
```
* `script: <name>`
Invoke the `name` script as part of the current script (or independently if `async`).
```yaml
script:
  name: doSomething
  async: true #run the script without pausing the current script. Starts a new shell 
```
* `js: <javascript (input,state)=>{...}>` invoke javascript function with
`input` and `state` as inputs. The command will invoke the next command
if either the function does not have a return value or if it returns a
value that is truthy (not null and not `false` or `"false"`)
```yaml
js: |
  (input,state)=>{
    //calculate something to update state based on input
    //decide to skip children
    return false; //skips children
  }
```
* `log: <message>`
log `message` to the run log
* `parse: <parser>`
use the text parsing rules from io.hyperfoil.tools.parse to parse the input from the previous command.
```yaml
parse:
  - name: timestamp
    pattern: "^(?<timestamp>\\d+)$"
    eat: Line
    rules: [ PreClose ]
    children: 
      - ...
  - ... 
```
* `queue-download: <path> ?<destination>`
queue the download action for after the run-scripts finish or after cleanup-scripts if the command is used in a cleanup-script. 
The download will occur if the run completes or aborts and will overwrite existing files.
* `read-signal: <name>`
checks if the signal `name` has already notified all `wait-for` and only invokes `then` commands if the `wait-for` were notified.
The command can also have an `else` list of children commands that run if `name` has not notified all `wait-for`.
```yaml
read-signal: ready
then:
- sh: doThis
else:
- sh: doThat
```
* `read-state: <name>`
read the current value of the named state variable
and pass it as input to the next command. Child `then` commands will be
called if the state exists and is not empty, otherwise child `else` commands will be called
```yaml
read-state: ${{RUN.ok}} #check 
then:
- sh: doThis
else:
- sh: doThat
```
* `regex: <pattern>`
try to match the input to a regular expression and add any named
capture groups to the current state at the named scope. `else` child commands
will invoke if the pattern does not match. 
* `repeat-until: <name>`
repeat the child commands until `name` is fully signalled.
Be sure to add a `sleep` to prevent a tight loop. 
A run should still end if the `repeat-until` is left waiting for a signal that will never occur.  
* `send-text: <text>`
sends text to the terminal. 
Use this when monitoring a long running interactive `sh` command that does not use a new prompt.
* `set-signal: <name> <count>`
set the expected number of `signal` calls for `name`. 
qDup waits for `signal: <name>` to be run `count` before the signal is reached and other scripts are notified. 
* `set-state: <name> ?<value>`
set the named state attribute to `value` if present or to the input if no value is provided
* `sh: <command>`
Execute a remote shell command. The silent option (when true) prevents
qDup from logging the command output (useful when tailing a long file)
```yaml
sh:
   command: ./doSomething.sh
   silent: true
   prompt:
     "'? ": Y #respond Y to any prompt questions
   ignore-exit-code: true #needed when qDup is run with -x or --exitCode to end a run if there is a non-zero exit code
```
* `signal: <name>`
send one signal for `name`. Runs calculate the expected number of `signals` for each
`name` and `wait-for` will wait for the expected number of `signal`
* `sleep: <amount>`
pause the current script for a fixed amount of time. Numbers are milliseconds but `1m 30s` is also valid input 
* `upload: <path> <destination>`
upload a file from the local `path` to `destination` on the remote host
* `wait-for: <name>`
pause the current script until `name` is fully signaled
* `xml: <path>`
This is an overloaded command that can perform an xpath
  based search or modification. Path takes the following forms
   - `file>xpath` - finds all xpath matches in file and passes them as
   input to the next command
   - `file>xpath == value` - set xpath matches to the specified value
   - `file>xpath ++ value` - add value to the xpath matches
   - `file>xpath --` - delete xpath matches from their parents
   - `file>xpath @key=value` - add the key=value attribute to xpath matches
   - `file>xpath -> stateName` - set stateName to the value found from xpath

## Monitoring
### watch
Some commands (e.g. `sh` commands) can provide output updates during execution.
A child command with the `watch` prefix will receive each new line of
output from the parent command as they are available (instead of after
the parent completes). This is mostly used to monitor output with `regex`
and to subsequently call `signal: STATE` or `ctrlC` the command when a
condition is met.
```YAML
- sh: tail -f /tmp/server.log
  watch:
  - regex: .*?FATAL.*
    then:
    - ctrlC:
    - abort: FATAL error
```
Note: `sh`, `wait-for`, and `repeat-until` cannot be used in `watch`
because they can block the execution of other watchers.

### timer
Another option for long running commands is to set a timer that will
execute if the command has been active for longer than the timeout.
```YAML
 - wait-for: SERVER_STARTED
   timer:
     60s:
     - log: still waiting 
     120_000: #ms
     - abort: server took too long to start, aborting
```
Note: `sh`, `wait-for`, and `repeat-until` cannot be used in a timer
because they can block the execution of other timers

### on-signal
A long running command might need to be altered if another script sends a signal.
For example a signal to stop a trace process with `ctrlC` or to send text to an interactive command (e.g. top).
```YAML
- sh: top
  on-signal:
    DONE:
    - send-text: q
``` 
Note: `sh`, `wait-for`, and `repeat-until` cannot be used in an on-signal
because they can block the execution of other timers

## State
State is the main way to introduce variables into a run. Commands can
reference state variables by name with `${{name}}` and `regex` can define
new entries with standard java regex capture groups `(?<name>.*)`
```YAML
- sh: tail -f /tmp/server.log
  watch:
- regex : ".*? WFLYSRV0025: (?<eapVersion>.*?) started in (?<eapStartTime>\\d+)ms.*"
  then:
  - log : eap ${{eapVersion}} started in ${{eapStartTime}}
  - ctrlC
```
### State javascript
The `${{=` state prefix tells qDup to use the javascript engine to evaluate the content. 
This lets us use javascript methods, string templates, any arithmetic operations.
A `:` can also be used to define a default value should any of the state variable names not be defined
```YAML
 - sh: tail -f /tmp/server.log
   timer: 
     ${{= ${{RAMP_UP}} + ${{MEASURE}} + ${{RAMP_DOWN}} : 100}}:
     - echo : "${{= ${{RAMP_UP}}+${{MEASURE}}+${{RAMP_DOWN}} : 100}}ms have has elapsed"
     - echo : "$[[> $[[RAMP_UP]]+$[[MEASURE]]+$[[RAMP_DOWN]] _ 100]]ms have has elapsed"
       prefix: "$[[" #use a custom state reference prefix
       suffix: "]]" #use a custom state reference suffix
       js-prefix: ">" #used after state prefix to tell qDup to use the javascript engine
       separator: "_" #use a custom separator between state expression and the default value
```

State references also have built in functions for time conversion
* `seconds(val)` - converts `val` like `10m 2s` into seconds
* `milliseconds(val)` - converts `val` like `60s` into milliseconds

qDup uses a state hierarchy so scripts default to their own state namespace but can use a shared namespace if desired.
There are 3 namespaces: default, HOST, and RUN.

#### default
The default namespace is for all variables without a namespace prefix and ensures scripts do not have to use unique state names

#### HOST
The host namespace is shared by all scripts on the same host. State values can be set on the host namespace by using the `HOST.`
prefix for the variable name.
```yaml
- set-state: HOST.name 
- echo: ${{HOST.name}} ${{name}}
``` 
The host namespace can be explicitly used with ${{HOST.name}} but will also be used for ${{name}} if name is not in the default namespace.

#### RUN
The run namespace is the top namespace and contains all state values defined in yaml. It shared by all scripts in the run
and can be accessed using the `RUN.` prefix. Like the host namespace, the run namespace will be used to resolve default scoped variables
if the value is not found in the default or host namespace. 
```yaml
- set-state: RUN.name
- echo: ${{RUN.name}} ${{name}}
```

#### with
State values can also be bound when adding a script to a role or when calling a command (e.g. 'script') in script.
Values defined in with (or the with on a parent command) do not use a prefix and take priority over default, host, or run namespace.
```yaml
scripts:
  exampleScript:
  - script: otherScript
    with:
      name: "exampleName"
      
roles:
  exampleRole:
  run-scripts:
  - dosomething:
      with:
        name: "testName"
- 
```
## YAML
Configuration files can include the following sections:

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
__roles__ a map of roleName : a hosts list and at least one of:
`setup-scripts`, `run-scripts`, `cleanup-scripts`. If the hosts list is missing
then the role is ignored. The reserved role name `ALL` applies to all hosts used in the run.
```YAML
roles:
  test:
    hosts: [local]
    run-scripts: [test-script]
  notTest:
    hosts: = ALL - tests                  #host expressions start with = and include +, -, and other role names
    run-scripts: [notTest-script]
  ALL:                                    #ALL automatically includes all hosts from other roles
    setup-scripts: [some-script]
```

__states__ a map of name : value used for variable substitution in the run

a nested map of name : value pairs used to inject variables
into the run configuration. Children are either a name : value pair or the name of a host.
Hosts can have name : value pairs or the name of a script as children.
Scripts are the lowest level in the state tree and therefore they can only have name : value pairs as children
```YAML
states:
  foo : bar
  biz : buzz
```

## Building
qDup builds to a single executable jar that includes all the dependencies
but it has a dependency that is not part of maven central.  [RedHatPerf/yaup](https://github.com/RedHatPerf/yaup)
is a utility library that needs to be downloaded and installed in the local
maven repo before qDup will build. Yaup builds with
> gradle clean build install

Once yaup installs you can build qDup with the jar task

> gradle jar

## Debug
qDup.jar starts a json server with a few endpoints at hostname:31337
* `/state` - GET the active state
* `/stage` - GET the current run stage [setup, run, cleanup]
* `/active` - GET the active commands and their current output
* `/session` - GET the active ssh sessions 
* `/session/:sessionId` - POST a string to send it to the remote session. ^C will send a ctrl+c
* `/signal` - the timestamp each signal was reached or the remaining count for each signal
* `/signal/:name` - POST a number to set the current signal count
* `/timer` - GET the command timers that track start and end time for each command
* `/counter` - GET the current value for eah counter
* `/waiter` - GET the waiting commands for all signal names
* `/pendingDownloads` - what files are queued for download from which hosts
