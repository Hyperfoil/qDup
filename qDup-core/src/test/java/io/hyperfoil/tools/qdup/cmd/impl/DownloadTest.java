package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.*;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.ScriptContext;
import io.hyperfoil.tools.qdup.cmd.SpyContext;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.shell.AbstractShell;
import io.hyperfoil.tools.qdup.shell.LocalShell;
import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.Assert.*;

public class DownloadTest {

    @Test
    public void return_local_file_path_when_defined_without_destination() throws IOException {
        String wrote = "bizbuz";
        File source = Files.createTempFile("qdup","txt").toFile();
        Files.write(source.toPath(),wrote.getBytes());
        File destinationFolder = Files.createTempDirectory("qdup").toFile();
        Download d = new Download(source.getPath());

        AbstractShell localShell = AbstractShell.getShell(
                "return_local_file_path_when_defined_without_destination",
                Host.parse(Host.LOCAL),
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        Run run = new Run(destinationFolder.getPath(),new RunConfigBuilder().buildConfig(),new Dispatcher());
        ScriptContext scriptContext = new ScriptContext(localShell,run.getConfig().getState(),run,new SystemTimer("download"),d,true);
        SpyContext spyContext = new SpyContext(scriptContext,run.getConfig().getState(), run.getCoordinator());
        d.run("",spyContext);

        assertNotNull("download should call next when download succeeds",spyContext.getNext());

        String result = spyContext.getNext();
        File resultFile = new File(result);

        assertTrue("result file should exist",resultFile.exists());
        assertTrue("download should return path starting with destinationFolder",spyContext.getNext().startsWith(destinationFolder.getPath()));
        assertTrue("download return should end with source name\nreturned "+spyContext.getNext()+"\nsource "+source.getName(),spyContext.getNext().endsWith(source.getName()));

        String read = Files.readString(resultFile.toPath());
        assertEquals(read,wrote);

    }
}
