package io.hyperfoil.tools.qdup.cmd;

public interface DispatchObserver {
    default void preStart(){}
    default void postStop(){}
}
