package com.vsk;

import com.sun.net.httpserver.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;


public class PapiSynchronizer {

    private static Map<String, HttpExchange> exchanges = new HashMap<String, HttpExchange>();
    private static int myPort = 8765;
    private static String myURL = "localhost:8765/policy/";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create();
        server.bind(new InetSocketAddress(myPort), 0);

        HttpContext context = server.createContext("/", new EchoHandler());

        server.createContext("/policy/calculatePolicy", new DirectRequest("http://esb-stage:8501/cxf/rest/partners/api/Policy/CalculatePolicy"));
        server.createContext("/policy/calculatePolicyAnswer.xml", new CallbackRequest());

        server.createContext("/policy/savePolicy", new DirectRequest("http://esb-stage:8501/cxf/rest/partners/api/Policy/SavePolicy"));
        server.createContext("/policy/savePolicyAnswer.xml", new CallbackRequest());

        server.setExecutor(null);
        server.start();
    }


    static class EchoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            System.out.println( exchange.getRequestURI());

            StringBuilder builder = new StringBuilder();

            builder.append("<h1>URI: ").append(exchange.getRequestURI()).append("</h1>");

            Headers headers = exchange.getRequestHeaders();
            for (String header : headers.keySet()) {
                builder.append("<p>").append(header).append("=")
                        .append(headers.getFirst(header)).append("</p>");
            }

            byte[] bytes = builder.toString().getBytes();
            exchange.sendResponseHeaders(200, bytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class DirectRequest implements HttpHandler {

        private String serverURL;

        public DirectRequest(String toURLa) {
            serverURL = toURLa;
        }

        private String makePost(Headers headers, InputStream body ) {
            try {
                URL url = new URL(serverURL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");

                for (String header : headers.keySet()) {
                    conn.setRequestProperty( header, headers.getFirst(header));
                }

                conn.setRequestProperty("X-VSK-CorrelationId","");
                conn.setRequestProperty("ReplyTo", myURL);

                byte[] sBody = body.readAllBytes();
                conn.setRequestProperty("Content-Length", String.valueOf(sBody.length));

                //System.out.write(sBody,0,sBody.length);

                conn.getOutputStream().write(sBody,0,sBody.length);
                conn.getOutputStream().flush();

                String corr_id = conn.getHeaderField("X-VSK-CorrelationId");

                conn.disconnect();

                return corr_id;

            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            System.out.println( exchange.getRequestURI());

            String corr_id;
            corr_id = makePost(exchange.getRequestHeaders(),exchange.getRequestBody() );

            System.out.println("CORR_ID=" + corr_id);

            if ( corr_id == "") {
                byte[] bytes = "Error. corr_id not found".getBytes();
                exchange.sendResponseHeaders(400, bytes.length);

                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            } else {
                if ( exchanges.size() > 1000 ) { exchanges.clear(); }
                exchanges.put(corr_id, exchange);
            }
        }
    }

    static class CallbackRequest implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            System.out.println( exchange.getRequestURI());

            String corr_id;
            Headers headers = exchange.getRequestHeaders();
            corr_id = headers.getFirst("X-VSK-CorrelationId");

            if ( corr_id == "") {
                byte[] bytes = "Error. corr_id not found".getBytes();
                exchange.sendResponseHeaders(400, bytes.length);

                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            } else {
                HttpExchange exchange2user = exchanges.get(corr_id);

                exchange2user.getResponseHeaders().putAll( exchange.getRequestHeaders());
                byte[] bytes2 = exchange.getRequestBody().readAllBytes();

                exchange2user.sendResponseHeaders(200, bytes2.length);
                OutputStream os2 = exchange2user.getResponseBody();
                os2.write(bytes2);
                os2.close();

                byte[] bytes1 = "OK".getBytes();
                exchange.sendResponseHeaders(200, bytes1.length);
                OutputStream os1 = exchange.getResponseBody();
                os1.write(bytes1);
                os1.close();

            }
        }
    }

}
