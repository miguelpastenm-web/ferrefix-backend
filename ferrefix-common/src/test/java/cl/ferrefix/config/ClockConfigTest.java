package cl.ferrefix.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** "Hoy" para validar fechas es el de Chile, no el del contenedor (normalmente UTC). */
class ClockConfigTest {

    @Test
    void elRelojDeNegocioEstaEnLaZonaHorariaDeChile() {
        Clock reloj = new ClockConfig().clock();
        assertEquals(ZoneId.of("America/Santiago"), reloj.getZone());
        assertEquals(ClockConfig.ZONA_CHILE, reloj.getZone());
    }

    @Test
    void elRelojAvanzaConLaHoraReal() {
        Clock reloj = new ClockConfig().clock();
        long antes = System.currentTimeMillis();
        long leida = reloj.millis();
        assertTrue(Math.abs(leida - antes) < 5_000, "El reloj difiere de la hora del sistema");
    }
}
