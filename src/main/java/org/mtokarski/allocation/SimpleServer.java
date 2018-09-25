package org.mtokarski.allocation;

import com.google.gson.Gson;
import fi.iki.elonen.NanoHTTPD;
import org.mtokarski.allocation.service.GCCollector;
import org.mtokarski.allocation.service.JavaMonitor;
import org.mtokarski.allocation.model.ClassInfo;
import org.mtokarski.allocation.model.GCInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

public class SimpleServer extends NanoHTTPD {

    private final GCCollector gcCollector = new GCCollector();
    private final JavaMonitor monitor;

    public SimpleServer() throws IOException {
        this(null, false);
    }

    public SimpleServer(List<String> packages, boolean stacktrace) throws IOException {
        super(8080);

        if (packages == null) {
            monitor = new JavaMonitor(gcCollector);
        } else {
            JavaMonitor.Settings settings = new JavaMonitor.Settings(packages, stacktrace);
            monitor = new JavaMonitor(gcCollector, settings);
        }

        start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
        System.out.println("Starting new server on http://localhost:8080");
    }

    @Override
    public Response serve(IHTTPSession session) {
        switch (session.getMethod()) {
            case GET: {
                return resolveGetMethod(session);
            }
            case POST: {
                return resolvePostMethod(session);
            }
            default: {
                return newNotFoundResponse();
            }
        }
    }

    private Response resolveGetMethod(IHTTPSession session) {
        switch (session.getUri()) {
            case "/": {
                InputStream resource = SimpleServer.class.getClassLoader().getResourceAsStream("static/index.html");
                return newChunkedResponse(Response.Status.OK, "text/html", resource);
            }
            case "/static/main": {
                InputStream resource = SimpleServer.class.getClassLoader().getResourceAsStream("static/main.js");
                return newChunkedResponse(Response.Status.OK, "application/javascript", resource);
            }
            case "/static/style": {
                InputStream resource = SimpleServer.class.getClassLoader().getResourceAsStream("static/style.css");
                return newChunkedResponse(Response.Status.OK, "text/css", resource);
            }
            case "/status": {
                return newResponse(Response.Status.OK, "{\"working\": " + monitor.isWorking() + "}");
            }
            case "/getgc": {
                long timeFilter = extractLastTimeParam(session);

                List<GCInfo> filterGC = gcCollector.filterGC(timeFilter);
                String resultJson = new Gson().toJson(filterGC);
                return newResponse(Response.Status.OK, resultJson);
            }
            case "/monitoredclasses": {
                List<String> monitoredClasses = monitor.getIncludedClasses();
                String resultJson = new Gson().toJson(monitoredClasses);
                return newResponse(Response.Status.OK, resultJson);
            }
            case "/trackerupdate": {
                long timeFilter = extractLastTimeParam(session);
                List<ClassInfo> state = monitor.getState(timeFilter);
                String resultJson = new Gson().toJson(state);
                return newResponse(Response.Status.OK, resultJson);
            }
            default: {
                return newNotFoundResponse();
            }
        }
    }

    private long extractLastTimeParam(IHTTPSession session) {
        List<String> lastTimeParam = session.getParameters().get("lastTime");
        if (lastTimeParam != null) {
            String timeString = lastTimeParam.get(0);
            return Long.parseLong(timeString);
        }
        return 0;
    }

    private Response resolvePostMethod(IHTTPSession session) {
        try {
            //apparently it is required; see: https://github.com/NanoHttpd/nanohttpd/issues/356
            session.parseBody(new HashMap<>());
        } catch (IOException | ResponseException e) {
            e.printStackTrace();
        }
        switch (session.getUri()) {
            case "/start": {
                JavaMonitor.Settings transformed = new Gson().fromJson(session.getQueryParameterString(), JavaMonitor.Settings.class);
                monitor.start(transformed);
                return newResponse(Response.Status.OK, "{\"working\": true}");
            }
            case "/stop": {
                monitor.stop();
                return newResponse(Response.Status.OK, "{\"working\": false}");
            }
            case "/gc": {
                System.gc();
                return newResponse(Response.Status.OK, "");
            }
            default: {
                return newNotFoundResponse();
            }
        }
    }

    private static Response newResponse(Response.Status status, String json) {
        Response response = newFixedLengthResponse(status, "application/json", json);
        response.addHeader("Access-Control-Allow-Origin", "*");
        return response;
    }

    private static Response newNotFoundResponse() {
        return newResponse(Response.Status.NOT_FOUND, "{\"error\": \"bad request\"}");
    }
}
