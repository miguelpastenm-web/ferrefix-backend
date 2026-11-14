package cl.ferrefix.model;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static cl.ferrefix.model.EstadoOrden.*;
import static org.junit.jupiter.api.Assertions.*;

class EstadoOrdenTest {

    @Test
    void transicionesPermitidasSonExactamenteLasDelDiseno() {
        Map<EstadoOrden, Set<EstadoOrden>> permitidas = new EnumMap<>(EstadoOrden.class);
        permitidas.put(PAGADA, Set.of(EN_PREPARACION, CANCELADA));
        permitidas.put(EN_PREPARACION, Set.of(DESPACHADA, CANCELADA));
        permitidas.put(DESPACHADA, Set.of(ENTREGADA));
        permitidas.put(ENTREGADA, Set.of());
        permitidas.put(CANCELADA, Set.of());

        for (EstadoOrden origen : values()) {
            for (EstadoOrden destino : values()) {
                assertEquals(permitidas.get(origen).contains(destino), origen.puedePasarA(destino), origen + " -> " + destino);
            }
        }
    }

    @Test
    void soloEntregadaYCanceladaSonFinales() {
        assertTrue(ENTREGADA.esFinal());
        assertTrue(CANCELADA.esFinal());
        assertFalse(PAGADA.esFinal());
        assertFalse(EN_PREPARACION.esFinal());
        assertFalse(DESPACHADA.esFinal());
    }

    @Test
    void noSePuedeCancelarUnaOrdenYaDespachada() {
        assertFalse(DESPACHADA.puedePasarA(CANCELADA));
    }
}
