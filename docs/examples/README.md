# Examples

This directory contains various qDup examples.

The examples are run using;

```sh
java -jar ../../target/qDup-*-uber.jar -b wildfly.yaml
```

## [helloWorld.yaml](helloWorld.yaml)

A simple Hello World example that will echo "Hello qDup!" to the terminal

## jbang

1. Install [JBang](https://www.jbang.dev/])
    ```shell script
    $ curl -Ls https://sh.jbang.dev | bash -s - app setup
    ```
2. Ensure ssh daemon is running on your machine
    ```shell script
    $ sudo systemctl start sshd
    ```
3. Ensure you can log into your local machine via ssh without a pswword
    ```shell script
    $ ssh localhost
   Last login: Mon Jan 01 00:00:00 2022 from ::1
    ```

4. Run qDup HelloWorld
    ```shell script
    $ jbang ./docs/examples/runQDup.java
    ```
You should see qDup print "Hello World" to the terminal;

```shell script
...
20:25:13.904 hello-qdup@localhost:hello-qdup
20:25:14.220 hello-qdup@localhost:echo Hello qDup!
Hello qDup!
...
```

### Want to change the greeting?

Pass a new GREETING value to Hello World;

```shell script
$ jbang ./docs/examples/runQDup.java -S GREETING='qDup ROCKS!!'
...
20:26:44.771 hello-world@localhost:hello-world
20:26:45.086 hello-world@localhost:echo qDup ROCKS!!
qDup ROCKS!!
...
```

### Want to run the qDup linter against a script in development?

Run the script with the `-T` option

```shell script
$ jbang ./docs/examples/runQDup.java -S GREETING='qDup ROCKS!!' -T
...
[jbang] Building jar...
SCRIPTS
hello-qdup
  1:hello-qdup parent=null skip=null next=sh: echo ${{GREETING}}
      with: ENV.SCRIPT_DIR=/projects/qDup/docs/examples
    2:sh: echo ${{GREETING}} parent=hello-qdup skip=null next=null
ROLES
  ALL
    HOSTS
      johara@localhost.localdomain:22
    SETUP
    RUN
    CLEANUP
  run-hello-qdup
    HOSTS
      johara@localhost.localdomain:22
    SETUP
    RUN
      script-cmd: hello-qdup
    CLEANUP
STATE
 GREETING = qDup ROCKS!!
 USER = user
 HOST = localhost.localdomain
```

Any errors will be reported:

```
Error: Role run-hello-qdup Host someOther was added without a fully qualified host representation matching user@hostName:port
 hosts:{local=${{USER}}@${{HOST}}}
  role:  stage: pending script: 
  command: 
Error: missing host for someOther
  role:  stage: pending script: 

```

## Want to try any of the other examples?
```shell script
$ jbang -DqDupScript=./docs/examples/wildfly.yaml ./docs/examples/runQDup.java
```

## [wildfly.yaml](wildfly.yaml)

A qDup script that boots the [WildFly](https://www.wildfly.org/) server.

## [iperf3.yaml](iperf3.yaml)

A qDup script that does an [iperf3](https://iperf.fr/) test run.
