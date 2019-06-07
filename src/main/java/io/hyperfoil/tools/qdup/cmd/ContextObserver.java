package io.hyperfoil.tools.qdup.cmd;

public interface ContextObserver {
    default void preStart(ScriptContext context,Cmd command){}
    default void preStop(ScriptContext context,Cmd command,String output){}
    default void preNext(ScriptContext context, Cmd command, String output){}
    default void preSkip(ScriptContext context, Cmd command, String output){}
    default void onUpdate(ScriptContext context, Cmd command, String output){}
    default void onDone(ScriptContext context){}
}
