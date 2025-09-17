package io.hyperfoil.tools.qdup;

import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnvTest {


    @Test
    public void merge(){
        Env from = new Env();
        Env into = new Env();

        from.loadBefore("A=a\nB=b\nC=c\nD=d\nE=e\nF=f");
        from.loadAfter("A=alpha\nB=bravo\nC=charlie\nD=delta");

        into.loadBefore("A=a\nC=c\nD=d\nE=e\nF=f");
        into.loadAfter("A=ant\nC=c\nE=e\nF=foo");

        into.merge(from);

        Env.Diff diff = into.getDiff();

        assertEquals("Changes to local env should not override: A\n"+diff.debug(),"ant",diff.get("A"));
        assertEquals("Missing keys should be merged: B\n"+diff.debug(),"bravo",diff.get("B"));
        assertEquals("Unchanged keys should update from merge: C\n"+diff.debug(),"charlie",diff.get(("C")));
        assertTrue("Removed keys should not be added by merge: D\n"+diff.debug(),!diff.keys().contains("D"));
        assertTrue("Unset should override an unchanged key: E\n"+diff.debug(),diff.unset().contains("E"));
        assertEquals("Unset should not override a changed key: F\n"+diff.debug(),"foo",diff.get("F"));
    }

    @Test
    public void merge_conflict_no_force(){
        Env from = new Env();
        from.loadAfter("FOO=foo");

        Env into = new Env();
        into.loadAfter("FOO=bar");

        into.merge(from);
        Env.Diff diff = into.getDiff();

        assertTrue(diff.keys().contains("FOO"));
        assertEquals("bar",diff.get("FOO"));
        assertFalse(diff.hasUnset());

    }

    @Test
    public void merge_unset_from(){

    }
    @Test
    public void merge_unset_into(){
        
    }

    @Test
    public void parse(){
        String input = "KEY=/value\n" +
                "NOVALUE=\n" +
                "MULTI_LINE=first\n"+
                "second\n"+
                "  third\n"+
                "EQUAL_IN_VALUE=foo=bar\n";


        Map<String,String> map = new LinkedHashMap<>();
        Env.parse(input,map);
        assertEquals("Expect 4 entires including the env variables without a value",4,map.size());
        assertTrue("should contain entries for keys without values",map.containsKey("NOVALUE"));
        assertEquals("MULTI_LINE should have 3 lines",3,map.get("MULTI_LINE").split("\n").length);
        assertEquals("EQUAL_IN_VALUE should split on first equal sign","foo=bar",map.get("EQUAL_IN_VALUE"));
    }

    @Test @Ignore
    public void parseJavaOpts(){
        String input="JAVA_OPTS=\"${JAVA_OPTS} -Djava.net.preferIPv4Stack=true -Djboss.modules.system.pkgs=org.jboss.byteman -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true -Dorg.jboss.resolver.warning=true -Dsun.rmi.dgc.client.gcInterval=3600000 -Dsun.rmi.dgc.server.gcInterval=3600000 -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.asyncPrepare=true -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.maxTwoPhaseCommitThreads=4 -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.asyncCommit=true -Djboss.server.default.config=standalone-full.xml -Dinfinispan.unsafe.allow_jdk8_chm=true -Dorg.apache.jasper.compiler.Parser.OPTIMIZE_SCRIPTLETS=true -Dorg.apache.cxf.io.CachedOutputStream.Threshold=4096000 -XX:+UseParallelOldGC -XX:ParallelGCThreads=32 -XX:+ParallelRefProcEnabled -Xmx12g -Xms12g -XX:MaxNewSize=5g -XX:NewSize=5g -XX:MetaspaceSize=256m -Xloggc:/tmp/gclogs/server_`date +%Y%m%d_%H%M%S`.gclog -Dactivemq.artemis.client.global.thread.pool.max.size=120 -XX:+UnlockCommercialFeatures -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:+FlightRecorder -XX:StartFlightRecording=compress=false,delay=0s,duration=24h,filename=/perf1/hprof/flight_record_`date +%Y%m%d_%H%M%S`.jfr,settings=lowOverhead\"";
        Map<String,String> map = new LinkedHashMap<>();
        Env.parse(input,map);


    }
    @Test @Ignore
    public void diffJavaOpts(){
        String input="JAVA_OPTS=\"${JAVA_OPTS} -Djava.net.preferIPv4Stack=true -Djboss.modules.system.pkgs=org.jboss.byteman -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true -Dorg.jboss.resolver.warning=true -Dsun.rmi.dgc.client.gcInterval=3600000 -Dsun.rmi.dgc.server.gcInterval=3600000 -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.asyncPrepare=true -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.maxTwoPhaseCommitThreads=4 -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.asyncCommit=true -Djboss.server.default.config=standalone-full.xml -Dinfinispan.unsafe.allow_jdk8_chm=true -Dorg.apache.jasper.compiler.Parser.OPTIMIZE_SCRIPTLETS=true -Dorg.apache.cxf.io.CachedOutputStream.Threshold=4096000 -XX:+UseParallelOldGC -XX:ParallelGCThreads=32 -XX:+ParallelRefProcEnabled -Xmx12g -Xms12g -XX:MaxNewSize=5g -XX:NewSize=5g -XX:MetaspaceSize=256m -Xloggc:/tmp/gclogs/server_`date +%Y%m%d_%H%M%S`.gclog -Dactivemq.artemis.client.global.thread.pool.max.size=120 -XX:+UnlockCommercialFeatures -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:+FlightRecorder -XX:StartFlightRecording=compress=false,delay=0s,duration=24h,filename=/perf1/hprof/flight_record_`date +%Y%m%d_%H%M%S`.jfr,settings=lowOverhead\"";
        Map<String,String> map = new LinkedHashMap<>();
        Env.parse(input,map);

        Map<String,String> empty = new HashMap<>();

        Env env = new Env();
        env.loadBefore(input);
        env.loadAfter("");

        Env.Diff diff = env.getDiff();

    }

    @Test
    public void diffTo(){
        Map<String,String> from = new LinkedHashMap<>();
        from.put("ONE","alpha");
        from.put("TWO","bravo");
        from.put("THREE","");

        Map<String,String> to = new LinkedHashMap<>();
        to.put("ONE","alpha");
        to.put("TWO","baker");
        to.put("FOUR","delta");

        Env env = new Env(from,to);

        Env.Diff diff = env.getDiff();

        assertFalse("diff should not be empty",diff.isEmpty());
        assertEquals("diff should have 1 unset",1,diff.unset().size());
        assertEquals("diff TWO should be baker","baker",diff.get("TWO"));
        assertFalse("diff should not contain ONE",diff.keys().contains("ONE") || diff.unset().contains("ONE"));
        assertTrue("diff shoudl have FOUR",diff.keys().contains("FOUR"));
    }
}
