# qDup 

qDup allows shell commands to be *queued up* across multiple servers to coordinate performance tests.

It is designed to follow the same workflow as a user at a terminal so that commands can be performed with or without qDup.
Commands are grouped into re-usable *scripts* that are mapped to different *hosts* by *roles*. 

qDup has 3 pre-defined phases for script execution to follow the usual performance test workflow: setup, run and cleanup.   

## Overview

* Queue shell commands using the qDup language
* Define the servers where the commands should be run
* Apply roles for the commands

See [User Guide](./docs/userguide.adoc) for the qDup user guide.

See [Examples](./docs/examples/) for various qDup examples.

See [Commands](./docs/reference/commands.adoc) for available commands.

See [FAQ](./docs/FAQ.md) for frequently asked questions.

## Building

qDup builds to a single executable jar that includes all its dependencies using [Apache Maven](http://maven.apache.org/).

qDup requires [Java 17](https://adoptopenjdk.net/) or higher.

```shell
mvn clean package
```

The qDup test suite requires [Podman](https://podman.io/) with a running instance of podman-socket for testcontainers. 

```shell
systemctl --user status podman.socket
```
We saw intermittent test failures where TestContainers would throw exceptions related to a "broken pipe" or failure to pull an image. 
Changing the default service timeout resolved the issue
`/usr/share/containers/containers.conf`
```shell
service_timeout=0
```
The default is 5 but changing it to 0 disables the timeouts entirely.

The test suite can also be skipped using

```shell
mvn -DskipTests -DskipITs clean package
```

### Running qDup

Execute your qDup script using

```shell
java -jar qDup-uber.jar test.yaml
```

There are no required options for qDup, but you can specify the base folder where qDup should create the run folder
(`-b /tmp`) or specify the full path where qDup should save the run files (`-B /tmp/myRun`). The qDup YAML file
should be the last argument on the command line.

The above example shows only 1 YAML file but you can also load helper YAML files with
shared definitions (e.g. `scripts` or `hosts`)

```shell
java -jar qDup-uber.jar test.yaml hosts.yaml scripts.yaml
```

Remember to put your main YAML file first because it will take naming precedence
over any `scripts`, `state`, or `hosts` that are defined in subsequent YAML files.
Roles are merged across all YAML files based on name.

Running `java -jar qDup-uber.jar` without any arguments will list
the supported options for the jar. 

## Contributing

Contributions to qDup are managed on [GitHub.com](https://github.com/Hyperfoil/qDup/)

* [Ask a question](https://github.com/Hyperfoil/qDup/discussions)
* [Raise an issue](https://github.com/Hyperfoil/qDup/issues)
* [Feature request](https://github.com/Hyperfoil/qDup/issues)
* [Code submission](https://github.com/Hyperfoil/qDup/pulls)

Contributions are most welcome !

Please, consult our [Code of Conduct](./CODE_OF_CONDUCT.md) policies for interacting in our
community.

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)
