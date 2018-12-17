package perf.qdup;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.ContextObserver;
import perf.qdup.cmd.Dispatcher;
import perf.qdup.cmd.ScriptContext;
import perf.yaup.StringUtil;
import perf.yaup.json.Json;

import java.net.InetAddress;
import java.net.UnknownHostException;

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
    public void preStart(ScriptContext context, Cmd command){
        if(server!=null){
            Json event = new Json();
            event.set("type","cmd.start");
            event.set("cmdUid",command.getUid());
            event.set("cmd",command.toString());
            event.set("contextUid",context.hashCode());
            vertx.eventBus().publish("observer",event.toString());
        }
    }
    @Override
    public void preStop(ScriptContext context,Cmd command,String output){
        if(server!=null){
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
            long now = System.currentTimeMillis();
            coordinator.getLatchTimes().forEach((key,value)-> json.set(key,StringUtil.durationToString(now-((Long)value))));
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

        try {
            server.requestHandler(router::accept).listen(port, InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void stop(){
        if(server!=null){
            server.close();
        }
        vertx.close();
    }



}
