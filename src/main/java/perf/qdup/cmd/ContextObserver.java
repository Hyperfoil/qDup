package perf.qdup.cmd;

public interface ContextObserver {
    default void preNext(ScriptContext context, Cmd command, String output){}
    default void preSkip(ScriptContext context, Cmd command, String output){}
    default void onUpdate(ScriptContext context, Cmd command, String output){}
    default void onDone(ScriptContext context){}
}
