package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.*;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.json.Json;
import io.vertx.core.Vertx;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ContainerShellTest extends SshTestBase {
    /**
     * registry.access.redhat.com/ubi8/ubi container exits?
     * also test an invalid container (response is not a valid containerId)
     */
    @Test(timeout = 5_000)
    public void failure_cannot_connect_remote(){
        Host host = new Host("idk","doesnotexist.localhost",null,22,null,false,"podman","quay.io/wreicher/omb");
        ContainerShell shell = new ContainerShell(
            "failure_cannot_connect_remote",
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        boolean connected = shell.connect();
        try{
            assertFalse("shell should not be connected",connected);
        }finally{
            shell.stopContainerIfStarted();
        }
    }

    //@Test(timeout = 10_000)
    @Test
    public void failure_container_stops_before_connect(){
        Host host = Host.parse("registry.access.redhat.com/ubi8/ubi");
        host.setStartConnectedContainer(Collections.EMPTY_LIST);
        host.setCreateConnectedContainer(Collections.EMPTY_LIST);
        ContainerShell shell = new ContainerShell(
            "failure_container_stops_before_connect",
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        boolean connected = shell.connect();
        try{
            String output = shell.shSync("date +%s");
            assertFalse("shell should not be connected",connected);
            assertNotNull("shSync should not return null even if not connected",output);
            assertEquals("shSync should return empty string when not connected","",output);
        }finally{
            shell.stopContainerIfStarted();
        }    
    }

    @Test
    public void failure_missing_image_registry(){
        Host host = Host.parse(Host.LOCAL+Host.CONTAINER_SEPARATOR+"redhat/ubi10");
        host.setStartConnectedContainer(Collections.EMPTY_LIST);
        host.setCreateConnectedContainer(Collections.EMPTY_LIST);
        ContainerShell shell = new ContainerShell(
                "failure_missing_image_registry",
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        shell.connect();
        assertFalse("should see an error connecting to image without registry",shell.isReady());

        String hostname = shell.shSync("hostname");
        assertEquals("shSync should return empty string for a shell that is not ready","",hostname);
        shell.stopContainerIfStarted();
    }
    @Test
    public void failure_missing_image_registry_credentials(){
        Host host = Host.parse(Host.LOCAL+Host.CONTAINER_SEPARATOR+"quay.io/redhat/ubi10");
        host.setStartConnectedContainer(Collections.EMPTY_LIST);
        host.setCreateConnectedContainer(Collections.EMPTY_LIST);
        ContainerShell shell = new ContainerShell(
                "failure_missing_image_registry_credentials",
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        shell.connect();
        assertFalse("should see an error connecting to password protected container",shell.isReady());

        String hostname = shell.shSync("hostname");
        assertEquals("shSync should return empty string for a shell that is not ready","",hostname);
        shell.stopContainerIfStarted();
    }
    @Test
    public void container_start_also_connects(){
        Host host = Host.parse("quay.io/fedora/fedora");
        host.setStartContainer(Host.PODMAN_CREATE_CONNECTED_CONTAINER);
        ContainerShell shell = new ContainerShell(
                "container_start_also_connects",
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        boolean connected = shell.connect();
        try{
            String output = shell.shSync("whoami");
            String systemUser = System.getProperty("user.name");
            assertTrue("shell should be connected",connected);
            assertTrue("host should have a containerId",host.hasContainerId());
            assertNotEquals("shell should be using a different userId",output,systemUser);
        }finally{
            shell.stopContainerIfStarted();
        }
    }

    @Test
    public void remote_container_connect(){
        AbstractShell remoteShell = AbstractShell.getShell(
                "remote_container_connect",
                getHost(),
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        remoteShell.setName("remote_container_connect");
        boolean remoteConnected = remoteShell.connect();
        assertTrue("remote shell should be connected",remoteConnected);
        String remoteHostname = remoteShell.shSync("uname -a");
        Host host = new Host(
            getHost().getUserName(),
            getHost().getHostName(),
            getHost().getPassword(),
            getHost().getPort(),
            getHost().getPrompt(),
            getHost().isLocal(),
            "podman",
            "registry.access.redhat.com/ubi8/ubi");
        host.setPassphrase(getHost().getPassphrase());
        host.setIdentity(getHost().getIdentity());
        //why were we setting start connected for docker?
        //host.setStartContainer(Host.DOCKER_START_CONNECTED_CONTAINER);
        ContainerShell shell = new ContainerShell(
            "remote_container_connect",
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        boolean connected = shell.connect();
        try{
            assertTrue("shell should be connected",connected);
            String containerHostname = shell.shSync("uname -a");
            assertNotEquals("container and remote shell should have different uname",remoteHostname,containerHostname);
            assertTrue("host should have a containerId",host.hasContainerId());
        }finally{
            shell.stopContainerIfStarted();
        }
    }
    @Test
    public void container_connect_also_performs_start(){
        Host host = Host.parse("registry.access.redhat.com/ubi8/ubi");
        host.setConnectShell(Host.PODMAN_CREATE_CONNECTED_CONTAINER);
        ContainerShell shell = new ContainerShell(
                "container_connect_also_performs_start",
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        boolean connected = shell.connect();
        try{
        String output = shell.shSync("whoami");
        String systemUser = System.getProperty("user.name");
        assertTrue("shell should be connected",connected);
        assertTrue("host should have a containerId",host.hasContainerId());
        assertNotEquals("shell should be using a different userId",output,systemUser);
        }finally{
            shell.stopContainerIfStarted();
        }
    }


    @Test
    public void container_stops_before_connect_then_starts_connected(){
        Host host = Host.parse("registry.access.redhat.com/ubi8/ubi");
        ContainerShell shell = new ContainerShell(
                "container_stops_before_connect_then_starts_connected",
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        boolean connected = shell.connect();
        try{
            String output = shell.shSync("whoami");
            String systemUser = System.getProperty("user.name");
            assertTrue("shell should be connected",connected);
            assertTrue("host should have a containerId",host.hasContainerId());
            assertNotEquals("shell should be using a different userId",output,systemUser);
        }finally{
            shell.stopContainerIfStarted();
        }
    }

    //This ensures we can connect to the same container for setup, run, cleanup
    @Test
    public void connect_to_containerId_after_first_connect(){
        Host host = new Host("","",null,22,null,true,"podman","quay.io/wreicher/omb");

        ContainerShell shell = new ContainerShell(
                "connect_to_containerId_after_first_connect",
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        boolean connected = shell.connect();
        try{
            assertTrue("shell should be connected",connected);
            assertTrue("host should have a containerId",host.hasContainerId());
            String containerId = host.getContainerId();
            shell.close();
            shell = new ContainerShell(
                    "connect_to_containerId_after_first_connect",
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
            );
            connected = shell.connect();
            assertTrue("shell should be connected",connected);
            assertTrue("host should have a containerId",host.hasContainerId());
            String newContainerId = host.getContainerId();
            assertEquals("should have same containerId",containerId,newContainerId);
            String response = shell.shSync("mktemp");
            File f = new File(response);
            assertFalse("File should not exist on local filesystem",f.exists());
        }finally{
            shell.stopContainerIfStarted();
        }
    }

    @Test
    public void connect_sets_containerId(){
        Host host = new Host("","",null,22,null,true,"podman","quay.io/wreicher/omb");
        ContainerShell shell = new ContainerShell(
                "connect_sets_containerId",
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
            assertFalse("host should not have a containerId",host.hasContainerId());
            boolean connected = shell.connect();
        try{
            assertTrue("host should have a containerId",host.hasContainerId());
            assertTrue("shell should be connected",connected);
            assertTrue("shell should be open",shell.isOpen());
            assertTrue("shell should be ready",shell.isReady());
        }finally{
            shell.stopContainerIfStarted();
        }
    }
    @Test
    public void start_that_connects_still_sets_containerId(){
        Host host = new Host("","",null,22,null,true,"podman","quay.io/fedora/fedora");
        host.setStartContainer(Host.PODMAN_CREATE_CONNECTED_CONTAINER);
        ContainerShell shell = new ContainerShell(
                "start_that_connects_still_sets_containerId",
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
            assertFalse("host should not have a containerId",host.hasContainerId());
            boolean connected = shell.connect();
        try{
            assertTrue("host should have a containerId",host.hasContainerId());
            String containerId = host.getContainerId();
            assertTrue("shell should be connected",connected);
            assertTrue("shell should be open",shell.isOpen());
            assertTrue("shell should be ready",shell.isReady());
        }finally{
            shell.stopContainerIfStarted();
        }
    }
    @Test
    public void connect_replaces_sub_shell(){
        Host host = new Host("","",null,22,null,true,"podman","quay.io/wreicher/omb");
        ContainerShell shell = new ContainerShell(
                "start_that_connects_still_sets_containerId",
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        assertFalse("host should not have a containerId",host.hasContainerId());
        boolean connected = shell.connect();        
        try{
        assertTrue("host should have a containerId",host.hasContainerId());
        assertTrue("shell should be connected",connected);
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
        String response = shell.shSync("mktemp");
        File f = new File(response);
        assertFalse("File should not exist on local filesystem",f.exists());
        }finally{
            shell.stopContainerIfStarted();
        }
    }

    @Test
    public void local_container_multiple_scripts_same_host(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                 scripts:
                   captureName:
                   - sh: uname -a
                     then:
                     - regex: Linux (?<hash>[a-z0-9]{12})
                       then:
                       - set-state: RUN.found ${{= [ ...${{RUN.found:[]}}, "${{hash}}" ] }}
                 hosts:
                   uno: TARGET_HOST
                   dos: TARGET_HOST
                 roles:
                   one:
                     hosts:
                       - uno
                     setup-scripts:
                       - captureName
                     run-scripts:
                       - captureName
                       - captureName
                       - captureName
                     cleanup-scripts:
                       - captureName
                 states:
                   found: []
                 """.replaceAll("TARGET_HOST",Host.LOCAL+Host.CONTAINER_SEPARATOR+"quay.io/fedora/fedora")
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Dispatcher dispatcher = new Dispatcher(10,10,10);
        Run doit = new Run(tmpDir.toString(), config, dispatcher);

        JsonServer jsonServer = new JsonServer(Vertx.vertx(), doit, 31337);
        jsonServer.start();

        doit.run();

        jsonServer.stop();

        State state = config.getState();
        Object found = state.get("found");
        assertTrue(found instanceof Json);
        Json json = (Json)found;
        assertTrue(json.isArray());
        //assertEquals(6,json.size());
        Set<Object> unique = new HashSet<>(json.values());
        assertEquals(1,unique.size());
    }

    @Test
    public void local_container_two_roles_same_host(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                 scripts:
                   captureName:
                   - sh: uname -a
                     then:
                     - regex: Linux (?<hash>[a-z0-9]{12})
                       then:
                       - set-state: RUN.found ${{= [ ...${{RUN.found:[]}}, "${{hash}}" ] }}
                 hosts:
                   uno: TARGET_HOST
                   dos: TARGET_HOST
                 roles:
                   one:
                     hosts:
                       - uno
                     setup-scripts:
                       - captureName
                     run-scripts:
                       - captureName
                     cleanup-scripts:
                       - captureName
                   two:
                     hosts:
                       - uno
                     setup-scripts:
                       - captureName
                     run-scripts:
                       - captureName
                     cleanup-scripts:
                       - captureName
                 states:
                   found: []
                 """.replaceAll("TARGET_HOST",Host.LOCAL+Host.CONTAINER_SEPARATOR+"quay.io/fedora/fedora")
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

        State state = config.getState();
        Object found = state.get("found");
        assertTrue(found instanceof Json);
        Json json = (Json)found;
        assertTrue(json.isArray());
        assertEquals(6,json.size());
        Set<Object> unique = new HashSet<>(json.values());
        assertEquals(1,unique.size());
    }

    @Test
    public void local_container_start_connected_uses_same_image_all_stages(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal",
            """
             scripts:
               captureName:
               - sh: uname -a
                 then:
                 - regex: Linux (?<hash>[a-z0-9]{12})
                   then:
                   - set-state: RUN.found ${{= [ ...${{RUN.found:[]}}, "${{hash}}" ] }}
             hosts:
               uno: TARGET_HOST
               dos: TARGET_HOST
             roles:
               one:
                 hosts:
                   - uno
                 setup-scripts:
                   - captureName
                 run-scripts:
                   - captureName
                 cleanup-scripts:
                   - captureName
               two:
                 hosts:
                   - dos
                 setup-scripts:
                   - captureName
                 run-scripts:
                   - captureName
                 cleanup-scripts:
                   - captureName
             states:
               found: []
             """.replaceAll("TARGET_HOST",Host.LOCAL+Host.CONTAINER_SEPARATOR+"quay.io/fedora/fedora")
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

        State state = config.getState();
        Object found = state.get("found");
        assertTrue(found instanceof Json);
        Json json = (Json)found;
        assertTrue(json.isArray());
        assertEquals(6,json.size());
        Set<Object> unique = new HashSet<>(json.values());
        assertEquals(2,unique.size());
    }

    @Test
    public void local_container_single_start_connected_uses_same_image_all_stages(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                 scripts:
                   captureName:
                   - sh: uname -a
                     then:
                     - regex: Linux (?<hash>[a-z0-9]{12})
                       then:
                       - set-state: RUN.found ${{= [ ...${{RUN.found:[]}}, "${{hash}}" ] }}
                 hosts:
                   uno: TARGET_HOST
                 roles:
                   one:
                     hosts:
                       - uno
                     setup-scripts:
                       - captureName
                     run-scripts:
                       - captureName
                     cleanup-scripts:
                       - captureName
                 states:
                   found: []
                 """.replaceAll("TARGET_HOST",Host.LOCAL+Host.CONTAINER_SEPARATOR+"quay.io/fedora/fedora")
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

        State state = config.getState();
        Object found = state.get("found");
        assertTrue(found instanceof Json);
        Json json = (Json)found;
        assertTrue(json.isArray());
        assertEquals(3,json.size());
        Set<Object> unique = new HashSet<>(json.values());
        assertEquals(1,unique.size());
    }

    @Test
    public void local_container_state_in_create_connected(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                 scripts:
                   captureName:
                   - sh: uname -a
                     then:
                     - regex: Linux (?<RUN.hash>[a-z0-9]{12})
                 hosts:
                    uno:
                        local: true
                        platform: podman
                        container: quay.io/fedora/fedora
                        create-connected-container: podman run --cpus=${{cpuCount}} --interactive --tty ${{image}} /bin/bash
                 roles:
                   one:
                     hosts:
                       - uno
                     setup-scripts:
                       - captureName
                 states:
                   cpuCount: 2
                 """
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

        State state = config.getState();
        assertTrue(state.has("hash"));
    }
}
