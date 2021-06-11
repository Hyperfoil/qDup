package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.ContextObserver;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.hyperfoil.tools.yaup.json.Json;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Map;

public class JsonServer implements RunObserver, ContextObserver {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    public static int DEFAULT_PORT = 31337;

    private final int port;
    private Run run;
    private final Vertx vertx;
    private HttpServer server;
    private Dispatcher dispatcher;
    private Coordinator coordinator;

    public JsonServer(Run run){
        this(run,DEFAULT_PORT);
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

    private String filter(Object o){
       if(o!=null && run!=null){
          String rtrn = run.getConfig().getState().getSecretFilter().filter(o.toString());
          return rtrn;
       }
       return "";
    }

    @Override
    public void preStart(Context context, Cmd command){
        if(server!=null && command!=null){
            Json event = new Json();
            event.set("type","cmd.start");
            event.set("cmdUid",command.getUid());
            event.set("cmd",filter(command.toString()));
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
            event.set("cmd",filter(command.toString()));
            event.set("contextUid",context.hashCode());
            event.set("output",filter(output));
            vertx.eventBus().publish("observer",event.toString());
        }
    }
    @Override
    public void preStage(Stage stage){
        if(server!=null){
            Json event = new Json();
            event.set("type","stage.start");
            event.set("stage",stage.toString());
            vertx.eventBus().publish("observer",event.toString());
        }
    }
    @Override
    public void postStage(Stage stage){
        if(server!=null){
            Json event = new Json();
            event.set("type","stage.stop");
            event.set("stage",stage.toString());
            vertx.eventBus().publish("observer",event.toString());
        }
    }

    public int getPort(int startingPort){
       int currentPort = startingPort > 0 && startingPort < 65535 ? startingPort : 31337;
       boolean available = true;
       do {
          try (ServerSocket ss = new ServerSocket(currentPort) ; DatagramSocket ds = new DatagramSocket(currentPort)){
             ss.setReuseAddress(true);
             ds.setReuseAddress(true);
             available = true;
          } catch (IOException e) {
             available = false;
             currentPort++;
          }
       }while (!available && currentPort < 65535);
       return currentPort;
    }

    public void start(){

        server = vertx.createHttpServer();

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route("/").produces("application/json").handler(rc->{
            Json rtrn = new Json();
            rtrn.set("GET /state","current qDup state");
            rtrn.set("GET /stage","the current run stage");
            rtrn.set("GET /active","list of active commands and context");
            rtrn.set("GET /session","list of active ssh terminal sessions");
            rtrn.set("POST /session/:sessionId","send text to the ssh terminal session. ^C sends ctrl+C");
            rtrn.set("GET /signal","get the signal and their remaining signal counts");
            rtrn.set("POST /signal/:name","set the remaining signal count for the target signal");
            rtrn.set("GET /timer","get the current command times");
            rtrn.set("GET /waiter","get the current waiters");
            rtrn.set("GET /counter","get the current counter counts");
            rtrn.set("GET /pendingDownloads","get the list of pending downloads");
            rc.response().end(rtrn.toString(2));
        });
        router.route("/state").produces("application/json").handler(rc->{
           rc.response().end(filter(run.getConfig().getState().toJson().toString()));
        });
        router.route("/stage").produces("application/json").handler(rc->{
            Json rtrn = new Json();
            if(this.run!=null){
                rtrn.set("stage",this.run.getStage().getName());
            }
            rc.response().end(rtrn.toString(2));
        });
        router.route("/active").produces("application/json").handler(rc->{
            String response = dispatcher.getActiveJson().toString(2);
            rc.response().end(filter(response));
        });
        router.get("/session").handler(rc->{
           try {
              if (dispatcher != null) {
                 Json response = dispatcher.getContexts();
                 rc.response().end(filter(response.toString()));
              } else {
                 rc.response().setStatusCode(400).end("unable to get sessions");
              }
           }catch(Exception e){
              e.printStackTrace();
           }
        });
        router.post("/session/:sessionId").handler(rc->{

            String cmdUid = rc.request().getParam("sessionId");
            Context found = dispatcher.getContext(cmdUid);
            if(found != null){
                String send  = rc.getBodyAsString();
                if(send != null && !send.trim().isEmpty()){
                   if("^C".equals(send.toUpperCase())) {
                       if (found.getSession() != null) {
                           found.getSession().ctrlC();
                       }
                   }else if ("^\\".equals(send)) {
                       if (found.getSession() != null) {
                           found.getSession().ctrl('\\');
                       }
                   }else if (send.startsWith("^") && send.length()==2){
                       if(found.getSession() != null) {
                           found.getSession().ctrl(send.charAt(1));
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
                 String populatedBody = Cmd.populateStateVariables(body,null,run.getConfig().getState(),run.getCoordinator());
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
        router.route("/signal/:name").produces("application/json").handler(rc->{
           String name = rc.request().getParam("name");
           if(name != null && !name.trim().isEmpty()){
              if(coordinator.hasSignal(name)){
                 Json json = new Json();
                 json.set("name",name);
                 json.set("count",coordinator.getSignalCount(name));
                 if(coordinator.getLatchTimes().containsKey(name)){
                    json.set("timestamp",coordinator.getLatchTimes().get(name));
                 }
                 rc.response().end(json.toString());
              }else{
                 rc.response().setStatusCode(400).end(name+ "does not exist");
              }
           }else{
              rc.response().setStatusCode(400).end("missing signal name");
           }
        });
        router.route("/timer").produces("application/json").handler(rc->{
           String response = run != null ? run.getProfiles().toString() : "{}";
           rc.response().end(filter(response));
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

        int foundPort = getPort(port);
        try {
            logger.info("listening at {}:{}", InetAddress.getLocalHost().getHostName()
                    ,foundPort);
        } catch (UnknownHostException e) {
            logger.info("listening at localhost:{}", foundPort);
        }

        server.requestHandler(router::accept).listen(foundPort/*, InetAddress.getLocalHost().getHostName()*/);
    }

    public void stop(){
        if(server!=null){
            server.close();
        }
        vertx.close();
    }



}
