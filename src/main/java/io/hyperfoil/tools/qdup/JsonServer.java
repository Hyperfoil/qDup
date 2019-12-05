package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.ContextObserver;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.ScriptContext;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
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
        router.route().handler(BodyHandler.create());
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
        router.get("/session").handler(rc->{
           try {
              if (dispatcher != null) {
                 Json response = dispatcher.getContexts();
                 rc.response().end(response.toString());
              } else {
                 rc.response().setStatusCode(400).end("unable to get sessions");
              }
           }catch(Exception e){
              e.printStackTrace();
           }
        });
        router.post("/session/:cmdUid").handler(rc->{

            String cmdUid = rc.request().getParam("cmdUid");
            Context found = dispatcher.getContext(cmdUid);
            if(found != null){
                String send  = rc.getBodyAsString();
                if(send != null && !send.trim().isEmpty()){
                   if("^C".equals(send)){
                      if(found.getSession()!=null){
                         found.getSession().ctrlC();
                      }
                   }else{
                      if(found.getSession()!=null){
                         found.getSession().response(send);
                      }
                   }
                  rc.response().end("ok");
                }else{
                   rc.response().setStatusCode(400).end("missing command body"+cmdUid);
                }
            }else{
                rc.response().setStatusCode(400).end("could not find session "+cmdUid);
            }

        });
        router.post("/signal/:name").handler(rc->{
           String name = rc.request().getParam("name");

           if(name != null && !name.trim().isEmpty()){
              String body = rc.getBodyAsString();
              if(body != null && !body.trim().isEmpty()){
                 String populatedBody = Cmd.populateStateVariables(body,null,run.getConfig().getState());
                 if(populatedBody.matches("\\d{1,32}")){
                    try {
                       int amount = Integer.parseInt(populatedBody);
                       coordinator.setSignal(name,amount,true);
                       rc.response().end(""+coordinator.getSignalCount(name));
                    }catch (NumberFormatException e){
                       rc.response().setStatusCode(400).end(populatedBody+" is not an integer");
                    }
                 }else{
                    rc.response().setStatusCode(400).end(populatedBody+" is not an integer");
                 }
              }else{
                 rc.response().setStatusCode(400).end("missing body for signal");
              }
           }else{
              rc.response().setStatusCode(400).end("missing signal name");
           }
            JsonObject jsonObject = rc.getBodyAsJson();
            if(jsonObject!=null){
                if(jsonObject.containsKey("name") && jsonObject.containsKey("count")){
                    if(coordinator!=null){
                        coordinator.setSignal(jsonObject.getString("name"),jsonObject.getInteger("count"));
                        rc.response().end(""+coordinator.getWaitCount(jsonObject.getString("name")));
                        return;
                    }
                }
            }
            rc.response().setStatusCode(400).end("failed to apply "+rc.getBodyAsString());
        });

        router.route("/signal").produces("application/json").handler(rc->{
            Json json = new Json();
            Map<String,Integer> latches = coordinator.getLatches();
            Map<String,Long> latchTimes = coordinator.getLatchTimes();
            latches.forEach((key,value)->{
                Json entry = new Json();
                entry.set("name",key);
                entry.set("count",value);
                if(latchTimes.containsKey(key)){
                    entry.set("timestamp",latchTimes.get(key));
                }
                json.add(entry);
            });
            String response = json.toString(2);
            rc.response().end(response);
        });
        router.route("/waiter").produces("application/json").handler(rc->{
            String response = coordinator.getWaitJson().toString(2);
            rc.response().end(response);
        });
        router.route("/counter").produces("application/json").handler(rc->{
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

        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        sockJSHandler.bridge(options,bridgeEvent->{
            bridgeEvent.complete(true);
        });
        router.route("/observe").handler(sockJSHandler);

        server.requestHandler(router::accept).listen(port/*, InetAddress.getLocalHost().getHostName()*/);
    }

    public void stop(){
        if(server!=null){
            server.close();
        }
        vertx.close();
    }



}
