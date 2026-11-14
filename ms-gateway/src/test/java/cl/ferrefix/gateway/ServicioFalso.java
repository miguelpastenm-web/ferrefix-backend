package cl.ferrefix.gateway;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor HTTP real (Reactor Netty, puerto libre) que hace de ms-tienda en los tests del gateway.
 * Guarda cada petición que recibe, para comprobar qué reenvió el gateway, y responde según la ruta:
 *  - /api/v1/estado/{n}  -> responde con el código n y un cuerpo JSON propio
 *  - /api/v1/lento       -> tarda 5 s (más que el response-timeout de los tests)
 *  - /api/v1/con-cors    -> añade su propia Access-Control-Allow-Origin (un servicio mal configurado)
 *  - /api/v1/creado      -> 201 con cabecera Location
 *  - cualquier otra      -> 200 con {"servicio":"ms-tienda","ruta":"..."}
 */
final class ServicioFalso {

    record Peticion(String metodo, String uri, HttpHeaders cabeceras, byte[] cuerpo) {
        String cuerpoTexto() {
            return new String(cuerpo, StandardCharsets.UTF_8);
        }
    }

    static final String ORIGEN_AJENO = "http://origen-del-servicio.example";

    private final List<Peticion> recibidas = new CopyOnWriteArrayList<>();
    private final DisposableServer servidor;

    ServicioFalso() {
        servidor = HttpServer.create().host("127.0.0.1").port(0)
                .handle((req, res) -> req.receive().aggregate().asByteArray().defaultIfEmpty(new byte[0])
                        .flatMap(cuerpo -> {
                            recibidas.add(new Peticion(req.method().name(), req.uri(), req.requestHeaders().copy(), cuerpo));
                            String ruta = req.uri().contains("?") ? req.uri().substring(0, req.uri().indexOf('?')) : req.uri();
                            res.header("Content-Type", "application/json");
                            if (ruta.startsWith("/api/v1/estado/")) {
                                int codigo = Integer.parseInt(ruta.substring("/api/v1/estado/".length()));
                                res.status(HttpResponseStatus.valueOf(codigo));
                                return res.sendString(Mono.just(
                                        "{\"status\":" + codigo + ",\"message\":\"respuesta de ms-tienda\"}")).then();
                            }
                            if (ruta.equals("/api/v1/lento")) {
                                return Mono.delay(Duration.ofSeconds(5))
                                        .then(res.sendString(Mono.just("{\"tarde\":true}")).then());
                            }
                            if (ruta.equals("/api/v1/con-cors")) {
                                res.header("Access-Control-Allow-Origin", ORIGEN_AJENO);
                            }
                            if (ruta.equals("/api/v1/creado")) {
                                res.status(HttpResponseStatus.CREATED).header("Location", "/api/v1/creado/15");
                            }
                            res.header("X-Bytes-Recibidos", String.valueOf(cuerpo.length));
                            return res.sendString(Mono.just("{\"servicio\":\"ms-tienda\",\"ruta\":\"" + ruta + "\"}")).then();
                        }))
                .bindNow();
    }

    String url() {
        return "http://127.0.0.1:" + servidor.port();
    }

    List<Peticion> recibidas() {
        return recibidas;
    }

    Peticion ultima() {
        if (recibidas.isEmpty()) {
            throw new AssertionError("El servicio no recibió ninguna petición: el gateway no la reenvió");
        }
        return recibidas.get(recibidas.size() - 1);
    }

    void limpiar() {
        recibidas.clear();
    }
}
