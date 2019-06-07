package io.hyperfoil.tools.qdup;

public interface RunObserver {

    default void preStage(Run.Stage stage){};
    default void postStage(Run.Stage stage){};
}
