package perf.ssh;

public class RunValidation {

    private ConfigValidation setupValidation;
    private ConfigValidation runValidation;
    private ConfigValidation cleanupValidation;

    public RunValidation(ConfigValidation setupValidation,
                         ConfigValidation runValidation,
                         ConfigValidation cleanupValidation){
        this.setupValidation = setupValidation;
        this.runValidation = runValidation;
        this.cleanupValidation = cleanupValidation;
    }

    public ConfigValidation getSetupValidation() {
        return setupValidation;
    }

    public ConfigValidation getRunValidation() {
        return runValidation;
    }
    public ConfigValidation getCleanupValidation() {
        return cleanupValidation;
    }
}
