# Examples

This directory contains various qDup examples.

The examples are run using;

```sh
java -jar ../../target/qDup-*-uber.jar -b wildfly.yaml
```

or if you have [JBang](https://jbang.dev) installed

```shell script
jbang -DqDupScript=wildfly.yaml runQDup.java 
```

## [helloWorld.yaml](helloWorld.yaml)

A simple Hello World example that will echo "Hello qDup!" to the terminal

## [wildfly.yaml](wildfly.yaml)

A qDup script that boots the [WildFly](https://www.wildfly.org/) server.

