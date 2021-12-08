package io.hyperfoil.tools.qdup;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QDupTest {


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
