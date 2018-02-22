# Qdup
Running benchmarks requires opening several
terminals and monitoring the output of one terminal to time the start
a command in another terminal. This project provides a way to script
multiple shells and coordinate between them so that timings are
consistent and runs can be queued.

## Example
The main way to use qDup is with yaml-esque* configuration files passed to the executable jar.
We say yaml-esque because yaml parsers do not support key value pairs with nested lists or maps.
It can help to think of the config as yaml but not all yaml features are supported. See the yaml section
for more details

Let's look at a sample yaml.
```YAML
name: example                                  # the name of the test we plan to run
scripts:                                       # scripts are the series of commands to run a test
  sync-time:                                   # the unique script name
    - sh: ntpdate -u time.google.com           # runs ntpdate in the remote shell

  widflyScript:                                # the name of a new script
    - sh: cd ${{WF_HOME}}                      # cd to the WF_HOME state variable
    - sh: rm ./standalone/log/*                # remove the old logs
    - queue-download: ./standalone/log         # save the files after the script stops
    - sh: ./bin/standalone.sh &                # starts wildfly as a background process
    - sleep: 1s                                # wait a second for the new log files
    - sh: cd ./standalone/log                  # change to the log directory
    - sh: {                                    # inline maps help for commands with multiple arguments
         silent : true                         # omit output from the run log
         command: tail -f server.log           # tail server.log
      }
         watch:                                # watch streams each line of output to the sub commands
          - regex: ".*?tail: no files.*"       # a tail error message that server.log was missing
             - abort: WF error                 # abort the run with a message WF error
          - regex: ".*?FATAL.*"                # if the line contains FATAL
             - ctrlC:                          # send ctrl+c to the shell (ends tail -f)
             - abort: WF error                 # abort the run with a message WF error
          - regex: ".*? started in (?<startTime>\\d+)ms.*"
             - log: startTime=${{startTime}}   # named regex groups are added as state variables
             - ctrlC:
             - signal: WF_STARTED              # notify other scripts that wildfly started
    - wait-for: DONE                           # pause this script until DONE is signalled
         timer: 30m                            # wait 30m then run sub-commands if still waiting for DONE
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
      server
    setup-scripts:                # scripts run sequentially before the run stage
    run-scripts:                  # scripts run in parallel during the run stage
      - wildflyScript             # wildflyScript will run on each host
      - wildflyScript             # run a second copy of widflyScript
          with:                   # with allows an override for state variables
           WF_HOME : /dev/wf-x    # WF_HOME will be different for this instance of wildflyScript
    cleanup-scripts:              # scripts run sequentially after the run stage
  ALL:                            # the ALL role automacically includes all hosts from other roles
    setup-scripts: [sync-time]    # run sync-time on all hosts during the setup stage
    run-scripts:
      ${{hostMonitoring}}         # a script name defined in a state variable
                                  # leaving hostMonitoring undefined means a script is not run
states:
  run:                            # variables visible to the entire run
    WF_HOME: /runtime/wf-11       # sets WF_HOME state variable
  server:                         # variables only visible to scripts running on server
    UNUSED : value
```
The main workflow is broken into 3 stages: setup, run, cleanup

Setup scripts sequentially execute with a shared ssh session to help
capture all the changes and ensure a consistent state for the run stage.
Any environment changes to the setup ssh session will be copied to all
the run stage sessions.

Run scripts execute in parallel and will start with whatever
environment changes occur from setup.

Cleanup scripts sequentially execute with a shared ssh session to ensure
a consistent ending state.
They occur after any pending `queue-download` from the run stage
so it is safe to cleanup anything left on the hosts

### Running a yaml
trying to run `java -jar qDup.jar` without any arguments will list
the supported options for the jar. The only required option is to either
specify the base folder where qDup should create the run folder
(`-b /tmp`) or to specify the full path where qDup should save the run
files (`-B /tmp/myRun`)

> java -jar qDup.jar -b /tmp test.yaml

This example shows only 1 yaml but you can also load helper yamls with
shared definitions (e.g. scripts)

> java -jar qDup.jar -b /tmp test.yaml helper.yaml

Remember to put your main yaml first because it will take naming precedence
over any scripts, state, or hosts that are defined in subsequent yaml.
Roles, however, are merged between yaml

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
* `upload: <path> <destination>`
upload a file from the local `path` to `destination` on the remote host
* `echo:`
log the input to console
* `invoke: <name>`
Invoke the `name` script as part of the current script
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
* `sh: <command> [<silent>]`
Execute a remote shell command. The silent option (when true) prevents
qDup from logging the command output (useful when tailing a long file)
* `signal: <name>`
send one signal for `name`. Runs calculate the expected number of `signals` for each
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

## Monitoring
### Watching
Some commands (e.g. sh commands) can provide updates during execution.
A child command with the `watch` prefix will receive each new line of
output from the parent command as they are available (instead of after
the parent completes). This is mostly used to monitor output with `regex`
and to subsequently call `signal STATE` or `ctrlC` the command when a
condition is met.
```YAML
 - sh: tail -f /tmp/server.log
   - watch:
     - regex: .*?FATAL.*
       - ctrlC:
       - abort: FATAL error
```

Note: `sh`, `waitFor`, and `repeat-until` cannot be used when watching
a command because they can block the execution of other watchers.

### Timers
Another option for long running commands is to set a timer that will
execute if the command has been active for longer than the timeout.
```YAML
 - wait-for: SERVER_STARTED
   - timer: 120_000 #ms
     - abort: server took too long to start, aborting
```
Note: `sh`, `waitFor`, and `repeat-until` cannot be used in a timer
because they can block the execution of other timers

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
qDup configuration is not yaml but it looks very similar to yaml so we often call it yaml.
The problem (as mentioned in the Introduction) is that yaml does not support key value pairs
with nested lists or maps and we use those to denote sub-commands.

We use a custom parser based on yaml and recommend files stick to using a
`-` before each command and using inline list or map notation when specifying
multiple arguments to a command.

```YAML
  - command : value
     - subCommand: subCommandValue
  - command : {
       arg1 : value,
       arg2 : secondValue }
     - subCommand: subCommandValue
```

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
    biz : buzz
    hostName:
      key : value
      scriptName:
        key : otherValue
```
script commands can reference state variables by surrouning the variable
names with `${{` `}}` (e.g. `${{greeting}}`)

## Building
qDup builds to a single executable jar that includes all the dependencies
but it has a dependency that is not part of maven central.  [RedHatPerf/yaup](https://github.com/RedHatPerf/yaup)
is a utility library that needs to be downloaded and installed in the local
maven repo before qDup will build. Yaup builds with
> gradle clean build install

Once yaup installs you can build qDup with the jar task

> gradle jar
