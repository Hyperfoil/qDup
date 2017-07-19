# Ssh
Coordinate multiple shells for running tests in a lab environment
## Introduction
Manually running benchmarks (e.g. specjms) requires opening several
terminals and manually monitoring the output of one terminal to start
a command in another terminal. This project provides a way to
start multiple shell sessions and coordinate between them without
having to manually monitor the output.

Each terminal connection starts by executing a sequence of commands
in a `Script`. All terminals start at the same time but `waitFor` and
`signal` commands help coordinate the execution. For example, the eap
`Script` would call `waitFor DATABASE_READY` and the database script
would call `signal DATABASE_READY` when it finished loading.

## Commands
* __abort "message"__ - Abort the current run and log the message
* __sh "command"__ - Execute a remote shell command
* __script "name"__ - Invoke the `name` script as part of the current script
* __regex "pattern"__ - try to match the previous output to a regular
expression and add any named capture groups to the current state at
the named scope.
* __download "path" "destination"__ - download `path` from the connected
host and save the output to the  run output path + `destination`
* __queueDownload "path" "destination"__ - queue the download action for
after the run finishes. The download will occur if the run completes
or aborts
* __ctrlC__ - send ctrl+C interrupt to the remote shell. This will kill
any currently running command (e.g. `sh tail -f /tmp/server.log`)
* __sleep "ms"__ - pause the current script for the given number of milliseconds
* __waitFor "name"__ - pause the current script until "name" is fully signaled
* __signal "name"__ - send one signal for "name." Runs parse all script :
host associations to calculate the expected number of `signal`s for each
`name`. All `waitFor` will wait for the expected number of `signal`
* __echo__ - log the input to console
* __log "message"__ - log `message` to the run log
* __code `(input,state)->{...}`__ - execute a `Code` instance that returns `Result`
* __xpath "path"__ - This is an overloaded command that can perform an xpath
  based search or modification. Path takes the following forms
   - `file>xpath` - finds all xpath matches in file and passes them as
   input to the next command
   - `file>xpath == value` - set xpath matches to the specified value
   - `file>xpath ++ value` - add value to the xpath matches
   - `file>xpath --` - delete xpath matches from their parents
   - `file>xpath @key=value` - add the key=value attribute to xpath matches

## Watching
Some commands (e.g. sh commands) can provide updates during execution.
A child command with the `watch` prefix will execute while the parent is
executing and will receive each new line of output from the parent command
as it is available. This is mostly used to monitor output with `regex`
and to subsequently `signal` or `ctrlC` the command.

## State
State is the main way to introduce variability into a run. Commands can
reference state variables by name with `${{name}}` and `regex` can define
new entries with standard java regex capture groups `(?<name>.*)`