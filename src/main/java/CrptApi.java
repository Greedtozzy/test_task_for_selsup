import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private static final int PORT = 8000;
    private static final Gson GSON = new Gson();
    private static int callCounter = 0;

    public static void main(String[] args) {
        new ServerThread(10).start();
        new TimerThread(TimeUnit.MINUTES).start();
    }

    static class ServerThread extends Thread {
        public static int requestLimit;
        public ServerThread(int requestLimit) {
            this.requestLimit = requestLimit;
        }
        @Override
        public void run() {
            HttpServer server;
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            server.createContext("/api/v3/lk/documents/create", new Handler(new DocumentService()));
            server.start();
            System.out.println("bla");
        }
    }

    static class TimerThread extends Thread {
        private final TimeUnit timeUnit;
        public TimerThread(TimeUnit timeUnit) {
            this.timeUnit = timeUnit;
        }
        @Override
        public void run() {
            while (true) {
                try {
                    callCounter = 0;
                    timeUnit.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static class Handler implements HttpHandler {
        private final DocumentService service;

        public Handler(DocumentService service) {
            this.service = service;
        }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, 0);
                return;
            }
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            service.addToQueue(body);
            byte[] responseBody = service.createDocument().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            } finally {
                exchange.close();
            }
            callCounter++;
        }
    }

    static class DocumentService {
        private final Deque<String> deque = new ArrayDeque<>();

        public synchronized String createDocument() {
            ProductDocument document = GSON.fromJson(getFromQueue(), ProductDocument.class);
            return GSON.toJson(document);
        }

        public void addToQueue(String body) {
            deque.addLast(body);
        }

        private String getFromQueue() {
            waitCounter();
            return deque.pop();
        }

        private void waitCounter() {
            while (callCounter >= ServerThread.requestLimit) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    class ProductDocument {
        Description description;
        String doc_id;
        String doc_status;
        String doc_type;
        boolean importRequest;
        String owner_inn;
        String participant_inn;
        String producer_inn;
        String production_date;
        String production_type;
        List<Product> products;
        String reg_date;
        String reg_number;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    class Description {
        String participantInn;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    class Product {
        String certificate_document;
        String certificate_document_date;
        String certificate_document_number;
        String owner_inn;
        String producer_inn;
        String production_date;
        String tnved_code;
        String uit_code;
        String uitu_code;
    }
}
