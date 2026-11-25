package pe.sevens.sigro.movil;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Hace de servicios de dominio en las pruebas: registra cada pedido y responde lo que se le programe. */
class ServidorFalso {
    record Pedido(String metodo, String ruta, String autorizacion, String cuerpo) {}

    final List<Pedido> pedidos = new CopyOnWriteArrayList<>();
    volatile int estado = 200;
    volatile String respuesta = "{}";
    private final HttpServer http;

    ServidorFalso() throws IOException {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/", ex -> {
            String cuerpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String ruta = ex.getRequestURI().getRawPath() + (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery());
            pedidos.add(new Pedido(ex.getRequestMethod(), ruta, ex.getRequestHeaders().getFirst("Authorization"), cuerpo));
            byte[] b = respuesta.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(estado, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        http.start();
    }

    String url() { return "http://127.0.0.1:" + http.getAddress().getPort(); }
    void parar() { http.stop(0); }
    void reiniciar(int estado, String respuesta) { pedidos.clear(); this.estado = estado; this.respuesta = respuesta; }
}
