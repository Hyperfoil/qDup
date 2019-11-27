package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.ContextObserver;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.ScriptContext;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;

import java.util.Map;

public class JsonServer implements RunObserver, ContextObserver {

    private final int port;
    private Run run;
    private final Vertx vertx;
    private HttpServer server;
    private Dispatcher dispatcher;
    private Coordinator coordinator;


    public JsonServer(Run run){
        this(run,31337);
    }
    public JsonServer(Run run, int port){
        this.port = port;
        this.vertx = Vertx.vertx();
        setRun(run);
    }
    public void setRun(Run run){
        if(run!=null) {
            this.run = run;
            this.dispatcher = run.getDispatcher();
            this.coordinator = run.getCoordinator();

            this.run.addRunObserver(this);
            this.dispatcher.addContextObserver(this);
        }
    }

    @Override
    public void preStart(Context context, Cmd command){
        if(server!=null && command!=null){
            Json event = new Json();
            event.set("type","cmd.start");
            event.set("cmdUid",command.getUid());
            event.set("cmd",command.toString());
            event.set("contextUid",context.hashCode());
            vertx.eventBus().publish("observer",event.toString());
        }
    }
    @Override
    public void preStop(Context context,Cmd command,String output){
        if(server!=null && command!=null){
            Json event = new Json();
            event.set("type","cmd.stop");
            event.set("cmdUid",command.getUid());
            event.set("cmd",command.toString());
            event.set("contextUid",context.hashCode());
            event.set("output",output);
            vertx.eventBus().publish("observer",event.toString());
        }
    }
    @Override
    public void preStage(Run.Stage stage){
        if(server!=null){
            Json event = new Json();
            event.set("type","stage.start");
            event.set("stage",stage.toString());
            vertx.eventBus().publish("observer",event.toString());
        }
    }
    @Override
    public void postStage(Run.Stage stage){
        if(server!=null){
            Json event = new Json();
            event.set("type","stage.stop");
            event.set("stage",stage.toString());
            vertx.eventBus().publish("observer",event.toString());
        }
    }

    public void start(){

        server = vertx.createHttpServer();

        Router router = Router.router(vertx);

        router.route("/stage").produces("application/json").handler(rc->{
            Json rtrn = new Json();
            if(this.run!=null){

            }
            rc.response().end(rtrn.toString(2));
        });
        router.route("/active").produces("application/json").handler(rc->{
            String response = dispatcher.getActiveJson().toString(2);
            rc.response().end(response);
        });
        router.route("/latches").produces("application/json").handler(rc->{
            Json json = new Json();
            Map<String,Integer> latches = coordinator.getLatches();
            Map<String,Long> latchTimes = coordinator.getLatchTimes();
            latches.forEach((key,value)->{
                Json entry = new Json();
                entry.set("count",value);
                if(latchTimes.containsKey(key)){
                    entry.set("timestamp",latchTimes.get(key));
                }
                json.set(key,entry);
            });
            String response = json.toString(2);
            rc.response().end(response);
        });
        router.route("/waiters").produces("application/json").handler(rc->{
            String response = coordinator.getWaitJson().toString(2);
            rc.response().end(response);
        });
        router.route("/counters").produces("application/json").handler(rc->{
            Json json = new Json();
            coordinator.getCounters().forEach((key,value)->json.set(key,value));
            String response = json.toString(2);
            rc.response().end(response);
        });
        router.route("/pendingDownloads").produces("application/json").handler(rc->{
            Json json = run.pendingDownloadJson();
            String response = json.toString(2);
            rc.response().end(response);
        });
        BridgeOptions options = new BridgeOptions()
            .addInboundPermitted(new PermittedOptions().setAddress("NOTHING"))
            .addOutboundPermitted(new PermittedOptions().setAddress("observer"))
        ;


        router.route("/observe").handler(SockJSHandler.create(vertx).bridge(options,bridgeEvent->{
            bridgeEvent.complete(true);
        }));

        server.requestHandler(router::accept).listen(port/*, InetAddress.getLocalHost().getHostName()*/);
    }

    public void stop(){
        if(server!=null){
            server.close();
        }
        vertx.close();
    }



}
