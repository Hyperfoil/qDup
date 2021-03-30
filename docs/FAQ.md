# FAQ

## No more authentication methods available?
Q: What does the following error mean when i try running a script;

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
A: 
