package perf.qdup;

import com.sun.net.httpserver.HttpServer;
import perf.qdup.cmd.CommandDispatcher;


import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class JsonServer {

    private final int port;
    private HttpServer httpServer;
    private final CommandDispatcher dispatcher;


    public JsonServer(CommandDispatcher dispatcher){
        this(dispatcher,31337);
    }
    public JsonServer(CommandDispatcher dispatcher,int port){
        this.dispatcher = dispatcher;
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
