package cl.ferrefix.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Costo de despacho: gratis si el subtotal SUPERA el umbral (50.000 por defecto), si no 3.990. Ambos son propiedades. */
@Component
public class PoliticaEnvio {

    private final int costo;
    private final long gratisSobre;

    public PoliticaEnvio(@Value("${ferrefix.envio.costo}") int costo,
                         @Value("${ferrefix.envio.gratis-sobre}") long gratisSobre) {
        this.costo = costo;
        this.gratisSobre = gratisSobre;
    }

    public int costoPara(long subtotal) {
        return subtotal > gratisSobre ? 0 : costo;
    }
}
