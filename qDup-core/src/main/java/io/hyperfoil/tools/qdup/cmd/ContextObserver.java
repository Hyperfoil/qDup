package io.hyperfoil.tools.qdup.cmd;

public interface ContextObserver {
    default void preStart(Context context,Cmd command){}
    default void preStop(Context context,Cmd command,String output){}
    default void preNext(Context context, Cmd command, String output){}
    default void preSkip(Context context, Cmd command, String output){}
    default void onUpdate(Context context, Cmd command, String output){}
    default void onDone(Context context){}
}
