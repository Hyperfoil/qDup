# Ssh
Coordinate multiple shells for running tests in a lab environment
## Introduction
Manually running benchmarks (e.g. specjms) requires opening several
terminals and manually monitoring the output of one terminal to start
a command in another terminal. The Ssh project provides a way to
start multiple shell sessions and coordinate between them without
having to manually monitor the output.

Each terminal connection starts by executing a sequence of commands
in a `Script`. All terminals start at the same time but `waitFor` and
`signal` commands help coordinate the execution.



## Commands
* sh - Execute a command in the remote shell
* script - Invoke the named script as part of the current script
* regex - try to match the previous output to a regular expression and
