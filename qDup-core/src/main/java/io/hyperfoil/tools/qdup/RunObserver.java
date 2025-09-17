package io.hyperfoil.tools.qdup;

public interface RunObserver {

    default void preStage(Stage stage){};
    default void postStage(Stage stage){};
}
