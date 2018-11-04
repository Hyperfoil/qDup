package perf.qdup.cmd;

public interface DispatchObserver {
    default void preStart(){}
    default void postStop(){}
}
