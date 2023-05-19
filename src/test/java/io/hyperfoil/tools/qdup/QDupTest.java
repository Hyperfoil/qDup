package io.hyperfoil.tools.qdup;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class QDupTest {


    @Test
    public void exit_code_checking_by_default(){
        QDup qdup = new QDup("-T","fake.yaml");
        assertTrue("exit checking by default",qdup.checkExitCode());
    }
    @Test
    public void disable_exit_checking(){
        QDup qdup = new QDup("-ix","-T","fake.yaml");
        assertFalse("exit checking by default",qdup.checkExitCode());
    }
    @Test
    public void skip_stages_valid_lowercase(){
        QDup qdup = new QDup("--skip-stages","setup,cleanup","-T","fake.yaml");
        assertTrue("expect targetStages",qdup.hasSkipStages());
        assertEquals("expect 2 targetStages: "+qdup.getSkipStages(),2,qdup.getSkipStages().size());
        List<Stage> targetStages = qdup.getSkipStages();
        assertTrue("should contain setup "+targetStages,targetStages.contains(Stage.Setup));
        assertTrue("should contain cleanup "+targetStages,targetStages.contains(Stage.Cleanup));
    }
    @Test
    public void skip_stages_valid_mixed_case(){
        QDup qdup = new QDup("--skip-stages","Setup,Cleanup","-T","fake.yaml");
        assertTrue("expect targetStages",qdup.hasSkipStages());
        assertEquals("expect 2 targetStages: "+qdup.getSkipStages(),2,qdup.getSkipStages().size());
        List<Stage> targetStages = qdup.getSkipStages();
        assertTrue("should contain setup "+targetStages,targetStages.contains(Stage.Setup));
        assertTrue("should contain cleanup "+targetStages,targetStages.contains(Stage.Cleanup));
    }
}
