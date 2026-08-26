package com.raven.interfaces.APP.core;

import com.raven.interfaces.APP.shared.HttpHelper;
import com.raven.interfaces.APP.shared.PathResolver;
import com.raven.utils.ServerConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.nio.charset.StandardCharsets;

public final class HttpRouter {

    @FunctionalInterface
    public interface RouteHandler {
        String Handle(HttpExchange Exchange) throws Exception;
    }

    private final HttpServer Server;
    private final ServerConfig Config;
    private final PathResolver PathResolver;

    public HttpRouter(HttpServer Server, ServerConfig Config, PathResolver PathResolver) {
        this.Server = Server;
        this.Config = Config;
        this.PathResolver = PathResolver;
    }

    public void Register(String Path, RouteHandler Handler) {
        Server.createContext(Path, Exchange -> Route(Exchange, Handler));
    }

    public void RegisterStatic() {
        Server.createContext("/static/", new StaticHandler());
        Server.createContext("/", new IndexHandler());
    }

    private void Route(HttpExchange Exchange, RouteHandler Handler) {
        try {
            Exchange.getResponseHeaders().add("Content-Type", "application/json");
            Exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            Exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
            Exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            if (Exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                Exchange.sendResponseHeaders(200, -1);
                return;
            }
            byte[] Body = Handler.Handle(Exchange).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Exchange.sendResponseHeaders(200, Body.length);
            try (OutputStream Output = Exchange.getResponseBody()) {
                Output.write(Body);
            }
        } catch (Exception Exception) {
            try {
                byte[] Body = HttpHelper.Json(Map.of("Error", String.valueOf(Exception.getMessage()))).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Exchange.sendResponseHeaders(500, Body.length);
                try (OutputStream Output = Exchange.getResponseBody()) {
                    Output.write(Body);
                }
            } catch (IOException Ignored) {}
        }
    }

    class IndexHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange Exchange) throws IOException {
            Path Template = PathResolver.ResolvePath(Config.GetTemplateDir() + "/index.html");
            if (!Files.exists(Template)) {
                Write(Exchange, 404, "404 index.html not found".getBytes());
                return;
            }
            Exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            Write(Exchange, 200, Files.readAllBytes(Template));
        }
    }

    class StaticHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange Exchange) throws IOException {
            String RequestPath = Exchange.getRequestURI().getPath();
            String Relative = RequestPath.startsWith("/static/") ? RequestPath.substring(7) : RequestPath;
            Path Target = PathResolver.ResolvePath(Config.GetStaticDir() + Relative);
            if (!Files.exists(Target) || Files.isDirectory(Target)) {
                Write(Exchange, 404, ("404 Not Found: " + RequestPath).getBytes());
                return;
            }
            Exchange.getResponseHeaders().add("Content-Type", ContentType(Target.toString()));
            Write(Exchange, 200, Files.readAllBytes(Target));
        }

        private String ContentType(String Path) {
            if (Path.endsWith(".html")) return "text/html; charset=UTF-8";
            if (Path.endsWith(".css")) return "text/css";
            if (Path.endsWith(".js")) return "application/javascript";
            if (Path.endsWith(".json")) return "application/json";
            if (Path.endsWith(".png")) return "image/png";
            if (Path.endsWith(".svg")) return "image/svg+xml";
            if (Path.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }

    private static void Write(HttpExchange Exchange, int Status, byte[] Body) throws IOException {
        Exchange.sendResponseHeaders(Status, Body.length);
        try (OutputStream Output = Exchange.getResponseBody()) {
            Output.write(Body);
        }
    }
}
