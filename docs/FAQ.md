# FAQ

## No more authentication methods available?
Q: What does the following error mean when I try running a script;

```
17:03:37.180 [command-1] ERROR io.hyperfoil.tools.qdup.SshSession - Exception while connecting to user@myserver.com
No more authentication methods available
org.apache.sshd.common.SshException: No more authentication methods available
	at org.apache.sshd.client.session.ClientUserAuthService.tryNext(ClientUserAuthService.java:322)
	at org.apache.sshd.client.session.ClientUserAuthService.processUserAuth(ClientUserAuthService.java:258)
        ...
```

A: qDup  uses ssh terminal sessions so need to be able to connect to the target machine without providing a password via prompt. This error is caused by not having the correct ssh credentials configured for the client machine trying to connect to the server machine. Please ensure that you can open a ssh terminal to ``user@myserver.com`` from the client machine you are running the qDup script from without needing to enter a password prompt.

## Failed to load qdup.yaml as YAML only

Q: Why does the parser fail with the following error message?
```
13:44:19.759 [main] ERROR i.h.tools.qdup.config.yaml.Parser - Failed to load qdup.yaml as yaml only
mapping values are not allowed here
 in 'string', line 6, column 14:
            regex: "\\s*Active: (?<active>\\w+) \ ... 
```
A: This is a yaml parsing error. The YAML parser will incorrectly see a colon and space ``: `` as a yaml mapping even when quoted.
Use ``\s`` instead of a space in ``regex`` patterns to avoid the parser confusion.

## Conditionals in qDup scripts

Q: How do I get qDup to conditionally run commands?

A: The best choice for conditional commands depends on the condition we are trying to detect and what we want to do in response to the condition. 
QDup detects conditions with the same tools we use in the terminal. If the condition is in a file we can 
use ``cat`` ``grep`` or ``jq`` in an ``sh`` command to read the file and `regex` if we want to check for multiple values.
``` yaml
scripts
  check-config: #checks configuration files 
  - sh: grep "version=maven" ./build.properties
    then:
    - sh: do_something_if_maven.sh
  - sh: grep "threads=" ./run.properties
    then:
    - regex: "all" #keyword used 
      then:
      - sh: do_something_for_all_threads.sh
    - regex: "^10$"  #using ^ and $ so the regex matches 10 and not 103 or 510
      then:
      - sh: 10_thread_script.sh  
```
We can also use `js` to perform more complicated condition checks (e.g. range or duration).
```yaml
scripts:
  check-config:
  - sh: grep "threads=" ./run.properties
    then:
    - js: (input,state)=>input > 10 && input < 20
      then:
      - sh: do_something_if_between_10_and_20.sh 
```
If the condition is in a log file or is something we need to monitor (e.g. ``/var/log/messages``) then we 
can use ``tail -f`` in an ``sh`` command and ``watch`` the output. 
```yaml
scripts:
  watch-log:
  - sh: tail -f /tmp/server.log
    watch:
    - regex: ERROR
      then:
      - signal: error_state
  process-error:
  - wait-for: error_state
  - sh: collect_stats.sh
```
We cannot use an `sh` command under a `watch` because the command we are watching (e.g. `tail -f`) is still running.
The best practice is to create a separate script that will `wait-for` the condition signal and run the desired commands
if the signal occurs. Think of the separate script as a second terminal window where we would run the commands while watching
the output in the first terminal.

There is also an `exec` command that will run a single shell command (similar to `sh`) on the same machine but `exec` will 
not have the same environment or working directory as the parent script. `exec` is good for a single command that does not 
need the benefit of the role setup or previous commands in the script.  