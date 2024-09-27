package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.ContextObserver;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.ScriptContext;
import io.hyperfoil.tools.qdup.cmd.SpyContext;
import io.hyperfoil.tools.qdup.cmd.impl.JsCmd;
import io.hyperfoil.tools.qdup.cmd.impl.ParseCmd;
import io.hyperfoil.tools.qdup.cmd.impl.Regex;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.json.vertx.JsonMessageCodec;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicReference;

public class JsonServer implements RunObserver, ContextObserver {
    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());
    public static int DEFAULT_PORT = 31337;
    private int port;
    private Run run;
    private final Vertx vertx;
    private HttpServer server;
    private Dispatcher dispatcher;
    private Coordinator coordinator;
    private Set<String> breakpoints;
    private final Phaser resumePhaser = new Phaser(1);
    private final Map<Integer, AtomicReference<Context>> contextAtomicReference = new ConcurrentHashMap<>();
    private String hostname = "localhost";

    public JsonServer(Run run){
        this(run,DEFAULT_PORT);
    }
    public JsonServer(Run run, int port){
        this.port = port;
        this.vertx = Vertx.vertx();
        this.breakpoints = new HashSet<>();
        vertx.eventBus().registerDefaultCodec(Json.class,new JsonMessageCodec());
        //vertx.eventBus().registerCodec(new JsonMessageCodec());
        setRun(run);
    }

    public boolean addBreakpoint(String breakpoint){
        return breakpoints.add(breakpoint);
    }
    public Set<String> getBreakpoints(){return breakpoints;}
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
           if(o instanceof Json){
               return run.getConfig().getState().getSecretFilter().filter((Json)o).toString();
           } else {
               String rtrn = run.getConfig().getState().getSecretFilter().filter(o.toString());
               return rtrn;
           }
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
            event.set("contextId",context instanceof ScriptContext ?  ((ScriptContext)context).getContextId():false);
            vertx.eventBus().publish("observer",new JsonObject(event.toString()));
            checkBreakpoint(context,command,"pre");
        }
    }

    private void checkBreakpoint(Context context,Cmd command,String when){
        String commandString = command.toString();
        boolean matches =  breakpoints.stream().anyMatch(breakpoint-> commandString.contains(breakpoint) || commandString.matches(breakpoint));
        if(matches){
            logger.info("Breakpoint {} run\n command: {}\n script: {}\n host: {}\n context: {}\n{}{}",
                    when,
                    commandString,
                    command.getHead().toString(),
                    context.getHost().getSafeString(),
                    context instanceof ScriptContext ? ((ScriptContext)context).getContextId() : "",
                    System.console()==null ? "" : "press Enter or",
                    " post to "+hostname+":"+port+"/breakpoint/resume"
            );
            resumePhaser.register();
            contextAtomicReference.put(command.getUid(), new AtomicReference<>(context));
            resumePhaser.arriveAndAwaitAdvance();
            contextAtomicReference.remove(command.getUid());
            resumePhaser.arriveAndDeregister();
        }
    }

    @Override
    public void preStop(Context context,Cmd command,String output){
        if(server!=null && command!=null){
            Json event = new Json();
            event.set("type","cmd.stop");
            event.set("cmdUid",command.getUid());
            event.set("cmd",filter(command.toString()));
            event.set("contextId",context instanceof ScriptContext ?  ((ScriptContext)context).getContextId():false);
            event.set("output", StringUtil.escapeBash( filter(output)) );
            vertx.eventBus().publish("observer", new JsonObject(event.toString()));
            checkBreakpoint(context,command,"post");
        }
    }

    @Override
    public void onUpdate(Context context, Cmd command, String output) {
        if(server!=null) {
            Json event = new Json();
            event.set("timestamp", System.currentTimeMillis());
            event.set("type", "cmd.update");
            event.set("cmd", command.toString());
            event.set("cmdUid", command.getUid());
            event.set("script", command.getHead().toString());
            event.set("update",   StringUtil.escapeBash( filter(output)) );
            event.set("contextId", context instanceof ScriptContext ? ((ScriptContext) context).getContextId() : false);
            vertx.eventBus().publish("observer", new JsonObject(event.toString()));
        }
    }

    @Override
    public void preStage(Stage stage){
        if(server!=null){
            Json event = new Json();
            event.set("type","stage.start");
            event.set("stage",stage.toString());
            vertx.eventBus().publish("observer",new JsonObject(event.toString()));
        }
    }
    @Override
    public void postStage(Stage stage){
        if(server!=null){
            Json event = new Json();
            event.set("type","stage.stop");
            event.set("stage",stage.toString());
            vertx.eventBus().publish("observer",new JsonObject(event.toString()));
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
        Thread scannerThread = new Thread(()->{
            if(System.console()!=null){
                Scanner scanner = new Scanner(System.in);
                while(true) {
                    if (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        resumePhaser.arriveAndAwaitAdvance();
                    }
                }
            }
        });
        scannerThread.setDaemon(true);//so it does not prevent the jvm from exiting
        scannerThread.start();
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route("/").produces("application/json").handler(rc->{
            Json rtrn = new Json();
            rtrn.set("GET /config","the loaded run config");
            rtrn.set("GET /state","current qDup state");
            rtrn.set("GET /stage","the current run stage");
            rtrn.set("GET /active","list of active commands and context");
            rtrn.set("GET /session","list of active ssh terminal sessions");
            rtrn.set("POST /session/:sessionId","send text to the ssh terminal session. ^C sends ctrl+C, ^next forces next command, ^skip forces skip command");
            rtrn.set("POST /session/:sessionId/regex","test regex on the current output of session ssh buffer");
            rtrn.set("GET /signal","get the signal and their remaining signal counts");
            rtrn.set("POST /signal/:name","set the remaining signal count for the target signal");
            rtrn.set("GET /timer","get the current command times");
            rtrn.set("GET /waiter","get the current waiters");
            rtrn.set("GET /counter","get the current counter counts");
            rtrn.set("GET /pendingDownloads","get the list of pending downloads");
            rtrn.set("GET /breakpoint","list breakpoint patterns");
            rtrn.set("POST /breakpoint","add a breakpoint pattern");
            rtrn.set("POST /breakpoint/resume","resume execution after a breakpoint");
            rc.response().end(rtrn.toString(2));
        });

        router.route("/config").produces("application/json").handler(rc->{
            rc.response().end(filter(run.getConfig().toJson()));
        });
        router.get("/breakpoint").handler(rc->{
            Json response = new Json();
            breakpoints.forEach((pattern)->{
                response.add(pattern);
            });
            rc.response().setStatusCode(200).end(response.toString());
        });
        router.post("/breakpoint").handler(rc->{
            String pattern = rc.request().getParam("pattern");
            pattern = rc.getBodyAsString();
            if(pattern!=null) {
                breakpoints.add(pattern);
                rc.response().setStatusCode(200).end(pattern);
            }else{
                rc.response().setStatusCode(401).end("missing pattern");
            }
        });
        router.post("/breakpoint/resume").handler(rc->{
            resumePhaser.arriveAndAwaitAdvance();
            if(System.console()!=null){

            }
            rc.response().end("{\"result\":\"ok\"}");
        });
        router.delete("/breakpoint").handler(rc->{
            String pattern = rc.request().getParam("pattern");
            if(pattern!=null && !pattern.isBlank()){
                breakpoints.remove(pattern);
                rc.response().setStatusCode(200).end(pattern);
            }else{
                rc.response().setStatusCode(404).end(pattern);
            }
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
        router.get("/session/:sessionId/buffer").handler(rc->{
            String cmdUid = rc.request().getParam("sessionId");
            Context found = dispatcher.getContext(cmdUid);
            if(found != null){
                rc.response().setStatusCode(200).end(found.getShell().bufferJson());
            }else{
                rc.response().setStatusCode(400).end("could not find session "+cmdUid);
            }
        });
        router.post("/session/:sessionId/parse").handler(rc->{
            String cmdUid = rc.request().getParam("sessionId");
            Context found = dispatcher.getContext(cmdUid);
            if(found != null) {
                String body = rc.getBodyAsString();
                ParseCmd toRun = new ParseCmd(body);

                State state = new State("");
                state.addChild("clone","");
                state.getChild("clone").load(found.getState().toJson());

                Json response = new Json();
                response.set("parse",Json.isJsonLike(body) ? Json.fromString(body) : body);

                SpyContext spyContext = new SpyContext(null,state,null);
                String currentOutput = found.getShell().peekOutput();
                toRun.run(currentOutput,spyContext);

                if(spyContext.getErrors().size()>0){
                    spyContext.getErrors().forEach(err->response.add("errors",err));
                    rc.response().end(response.toString());
                } else if(spyContext.hasSkip()){
                    response.set("skip",spyContext.getSkip());
                    rc.response().end(response.toString());
                } else if(spyContext.hasNext()){
                    if(Json.isJsonLike(spyContext.getNext())){
                        response.set("next", Json.fromString(spyContext.getNext()));
                    }else {
                        response.set("next", spyContext.getNext());
                    }
                    rc.response().end(response.toString());
                } else{
                    rc.response().setStatusCode(400).end("unexpected error trying to test parse:\n"+body);
                }


            } else {
                rc.response().setStatusCode(400).end("could not find session "+cmdUid);
            }
        });
        router.post("/session/:sessionId/js").handler(rc->{
            String cmdUid = rc.request().getParam("sessionId");
            Context found = dispatcher.getContext(cmdUid);
            if(found != null) {
                String code = rc.getBodyAsString();
                if(code != null && !code.trim().isEmpty()){
                    JsCmd toRun = new JsCmd(code);

                    State state = new State("");
                    state.addChild("clone","");
                    state.getChild("clone").load(found.getState().toJson());


                    SpyContext spyContext = new SpyContext(null,state,null);
                    String currentOutput = found.getShell().peekOutput();
                    toRun.run(currentOutput,spyContext);
                    Json response = new Json();
                    if(!state.toOwnJson().isEmpty()){
                        response.set("state",state.toOwnJson());
                    }
                    if(spyContext.getErrors().size()>0){
                        spyContext.getErrors().forEach(err->response.add("errors",err));
                        rc.response().end(response.toString());
                    } else if(spyContext.hasSkip()){
                        response.set("skip",spyContext.getSkip());
                        rc.response().end(response.toString());
                    } else if(spyContext.hasNext()){
                        response.set("next",spyContext.getNext());
                        rc.response().end(response.toString());
                    } else{
                        rc.response().setStatusCode(400).end("unexpected error trying to test js\n"+code);
                    }

                }else{
                    rc.response().setStatusCode(400).end("missing code for "+cmdUid);
                }
            }else{
                rc.response().setStatusCode(400).end("could not find session "+cmdUid);
            }
        });
        router.post("/session/:sessionId/regex").handler(rc->{
            String cmdUid = rc.request().getParam("sessionId");
            Context found = dispatcher.getContext(cmdUid);
            if(found != null){
                String regex = rc.getBodyAsString();
                if(regex == null || regex.isEmpty()){
                    rc.response().setStatusCode(400).end("missing regex "+cmdUid);
                }else{
                    Regex toRun = new Regex(regex);
                    State state = new State("");
                    state.addChild("clone","");
                    state.getChild("clone").load(found.getState().toJson());
                    SpyContext spyContext = new SpyContext(null,state,null);
                    String currentOutput = found.getShell().peekOutput();
                    if(currentOutput == null || currentOutput.isEmpty()){
                        Cmd previous = found.getCurrentCmd() != null ? found.getCurrentCmd().getPrevious() : null;
                        String input = previous != null ? previous.getOutput() : "";
                        currentOutput = input;
                    }
                    toRun.run(currentOutput,spyContext);
                    Json response = new Json();
                    if(!state.toOwnJson().isEmpty()){
                        response.set("state",state.toOwnJson());
                    }
                    if(spyContext.getErrors().size()>0) {
                        spyContext.getErrors().forEach(err -> response.add("errors", err));
                        rc.response().end(response.toString());
                    }else if(spyContext.hasSkip()){
                        response.set("skip",spyContext.getSkip());
                        rc.response().end(response.toString());
                    } else if(spyContext.hasNext()){
                        response.set("next",spyContext.getNext());
                        rc.response().end(response.toString());
                    } else{
                        rc.response().setStatusCode(400).end("unexpected error trying to test regex: "+regex);
                    }
                }
            }else{
                rc.response().setStatusCode(400).end("could not find session "+cmdUid);
            }
        });
        router.post("/session/:sessionId").handler(rc->{

            String cmdUid = rc.request().getParam("sessionId");
            Context found = dispatcher.getContext(cmdUid);
            if(found != null){
                String send  = rc.getBodyAsString();
                if(send != null && !send.trim().isEmpty()){
                   if("^C".equals(send.toUpperCase())) {
                       if (found.getShell() != null) {
                           found.getShell().ctrlC();
                           rc.response().end("ok ^C");
                       }
                   }else if ("^\\".equals(send)) {
                       if (found.getShell() != null) {
                           found.getShell().ctrl('\\');
                           rc.response().end("ok ^\\");
                       }
                   }else if (send.startsWith("^")){

                       if(send.equals("^next")){
                           found.next(found.getCurrentCmd().getOutput());
                       }else if (send.equals("^skip")){
                           found.skip(found.getCurrentCmd().getOutput());
                       }else if(send.length()==2){
                           if(found.getShell() != null) {
                               found.getShell().ctrl(send.charAt(1));
                               rc.response().end("ok ^"+send.charAt(1));
                           }
                       }

                   }else{
                       //does this enable remote code injection before the next cmd?
                      if(found.getShell()!=null){
                         found.getShell().response(send);
                          rc.response().end("ok");
                      }
                   }
                   if(!rc.response().ended()){
                      rc.response().setStatusCode(400).end("missing ssh session");

                   }
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
                 String populatedBody = Cmd.populateStateVariables(body,null,run.getConfig().getState(),run.getCoordinator(),Json.fromMap(run.getTimestamps()));
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
        SockJSHandlerOptions sockJSHandlerOptions = new SockJSHandlerOptions()
                .setHeartbeatInterval(1000*60);
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx,sockJSHandlerOptions);

        SockJSBridgeOptions options = new SockJSBridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddressRegex(".*"))
                .addOutboundPermitted(new PermittedOptions().setAddressRegex(".*"))
                ;
        Router sockJSRouter = sockJSHandler.bridge(options);

        router.route("/observe/*").handler(sockJSHandler);
        router.route("/ui/*").handler(StaticHandler.create().setWebRoot("webapp"));

        port = getPort(port);

        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {

        }
        logger.info("json server listening at {}:{}", hostname, port);

        server = vertx.createHttpServer();
        server.requestHandler(router).listen(port/*, InetAddress.getLocalHost().getHostName()*/);
    }

    public void stop(){
        if(server!=null){
            server.close();
        }
        vertx.close();
    }



}
