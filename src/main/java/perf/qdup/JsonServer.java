package perf.qdup;

import com.sun.net.httpserver.HttpServer;
import perf.qdup.cmd.Dispatcher;
import perf.yaup.StringUtil;
import perf.yaup.json.Json;


import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;

public class JsonServer {

    private final int port;
    private HttpServer httpServer;
    private final Run run;
    private final Dispatcher dispatcher;
    private final Coordinator coordinator;


    public JsonServer(Run run, Dispatcher dispatcher, Coordinator coordinator){
        this(run,dispatcher,coordinator,31337);
    }
    public JsonServer(Run run, Dispatcher dispatcher, Coordinator coordinator, int port){
        this.run = run;
        this.dispatcher = dispatcher;
        this.coordinator = coordinator;
        this.port = port;
    }

    public void start(){
        try {
            httpServer = HttpServer.create(
                new InetSocketAddress(
                    java.net.InetAddress.getLocalHost().getHostName(),
                    port
                ),
                100
            );
            httpServer.createContext("/active", httpExchange -> {
                String response = dispatcher.getActiveJson().toString(2);
                httpExchange.sendResponseHeaders(200,response.length());
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            });
            httpServer.createContext("/latches", httpExchange -> {
                Json json = new Json();
                long now = System.currentTimeMillis();
                coordinator.getLatchTimes().forEach((key,value)-> json.set(key,StringUtil.durationToString(now-((Long)value))));
                String response = json.toString(2);
                httpExchange.sendResponseHeaders(200,response.length());
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            });
            httpServer.createContext("/counters", httpExchange -> {
                Json json = new Json();
                coordinator.getCounters().forEach((key,value)->json.set(key,value));
                String response = json.toString(2);
                httpExchange.sendResponseHeaders(200,response.length());
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            });
            httpServer.createContext("/pendingDownloads", httpExchange -> {
                Json json = run.pendingDownloadJson();
                String response = json.toString(2);
                httpExchange.sendResponseHeaders(200,response.length());
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            });
            httpServer.start();
        } catch (IOException e) {
            if(e instanceof BindException){
                //LOG that we failed to bind to 31337
            }
            e.printStackTrace();
        }

    }

    public void stop(){
        if(httpServer!=null){
            httpServer.stop(0);
        }
    }



}
