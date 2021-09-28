package io.hyperfoil.tools.qdup;

public enum Stage  implements Comparable<Stage>{
    Undefined("undefined",-1,true),
    Pending("pending",0,true),
    PreSetup("pre-setup",1,true),
    Setup("setup",2,true),
    Run("run",3,false),
    Cleanup("cleanup",4,true),
    PostCleanup("post-cleanup",5,true),
    Done("done",6,true);
    private String name;
    private int order;
    private boolean isSequential;
    Stage(String name,int order,boolean isSequential) {
        this.name = name;
        this.order = order;
        this.isSequential = isSequential;
    }

    private Stage getStage(int order){
        switch (order){
            case 0: return Pending;
            case 1: return PreSetup;
            case 2: return Setup;
            case 3: return Run;
            case 4: return Cleanup;
            case 5: return PostCleanup;
            case 6: return Done;
        }
        return Undefined;
    }
    public Stage getNext(){
        return getStage(order+1);
    }
    public Stage getPrevious(){return getStage(order-1);}
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
