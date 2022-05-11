package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.yaup.StringUtil;

public enum Stage {
    Invalid("invalid",-1,true),
    Pending("pending",0,true),
    PreSetup("pre-setup",1,true),
    Setup("setup",2,true),
    Run("run",3,false),
    PreCleanup("pre-cleanup",4,false),
    Cleanup("cleanup",5,true),
    PostCleanup("post-cleanup",6,true),
    Done("done",7,true);
    private String name;
    private int order;
    private boolean isSequential;
    Stage(String name,int order,boolean isSequential) {
        this.name = name;
        this.order = order;
        this.isSequential = isSequential;
    }
    public boolean isBefore(Stage stage){
        return this.order < stage.order;
    }
    public boolean isAfter(Stage stage){
        return this.order > stage.order;
    }

    public String getName() {
        return name;
    }
    public boolean isSequential(){return isSequential;}
}
