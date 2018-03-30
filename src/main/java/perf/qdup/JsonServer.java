package perf.qdup;

import com.sun.net.httpserver.HttpServer;
import perf.qdup.cmd.CommandDispatcher;
import perf.yaup.json.Json;


import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class JsonServer {

    private final int port;
    private HttpServer httpServer;
    private final CommandDispatcher dispatcher;
    private final Coordinator coordinator;


    public JsonServer(CommandDispatcher dispatcher,Coordinator coordinator){
        this(dispatcher,coordinator,31337);
    }
    public JsonServer(CommandDispatcher dispatcher,Coordinator coordinator,int port){
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
                coordinator.getLatchTimes().forEach((key,value)-> json.set(key,value));
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

            httpServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void stop(){
        if(httpServer!=null){
            httpServer.stop(0);
        }
    }



}
