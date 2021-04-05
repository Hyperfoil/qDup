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
3. Run qDup HelloWorld
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

Want to change the greeting?

Pass a new GREETING value to Hello World;

```shell script
$ jbang ./docs/examples/runQDup.java -S GREETING='qDup ROCKS!!'
...
20:26:44.771 hello-world@localhost:hello-world
20:26:45.086 hello-world@localhost:echo qDup ROCKS!!
qDup ROCKS!!
...
```

Want to try any of the other examples?
```shell script
$ jbang -DqDupScript=./docs/examples/wildfly.yaml ./docs/examples/runQDup.java
```

## [wildfly.yaml](wildfly.yaml)

A qDup script that boots the [WildFly](https://www.wildfly.org/) server.

