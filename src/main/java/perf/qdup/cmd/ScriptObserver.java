package perf.qdup.cmd;

public interface ScriptObserver {
    default void onStart(ScriptContext context){}
    default void onStop(ScriptContext context){}
}
