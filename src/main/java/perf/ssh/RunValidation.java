package perf.ssh;

import perf.ssh.config.StageValidation;

public class RunValidation {

    private StageValidation setupValidation;
    private StageValidation runValidation;
    private StageValidation cleanupValidation;

    public RunValidation(StageValidation setupValidation,
                         StageValidation runValidation,
                         StageValidation cleanupValidation){
        this.setupValidation = setupValidation;
        this.runValidation = runValidation;
        this.cleanupValidation = cleanupValidation;
    }

    public StageValidation getSetupValidation() {
        return setupValidation;
    }

    public StageValidation getRunValidation() {
        return runValidation;
    }
    public StageValidation getCleanupValidation() {
        return cleanupValidation;
    }

    public boolean isValid(){
        return !(setupValidation.hasErrors() || runValidation.hasErrors() || cleanupValidation.hasErrors());
    }
}
