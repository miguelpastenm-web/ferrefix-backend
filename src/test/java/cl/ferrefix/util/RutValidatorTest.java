package cl.ferrefix.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RutValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"11111111-1", "11.111.111-1", "111111111", "15432890-4", "12.345.678-5", "6.000.000-K", "6000000-k", "10000013-K", "10000004-0"})
    void aceptaRutsValidos(String rut) {
        assertTrue(RutValidator.esValido(rut), rut);
    }

    @ParameterizedTest
    @ValueSource(strings = {"11111111-2", "15.432.890-K", "12345678-9", "abc", "1-9", "123", "", "  "})
    void rechazaRutsInvalidos(String rut) {
        assertFalse(RutValidator.esValido(rut), rut);
    }

    @Test
    void rechazaNull() {
        assertFalse(RutValidator.esValido(null));
        assertNull(RutValidator.normalizar(null));
    }

    @Test
    void normalizaPuntosGuionYMayuscula() {
        assertEquals("6000000-K", RutValidator.normalizar("6.000.000-k"));
        assertEquals("11111111-1", RutValidator.normalizar("111111111"));
    }

    @Test
    void digitoVerificadorCeroYK() {
        assertEquals('K', RutValidator.calcularDv("6000000"));
        assertEquals('3', RutValidator.calcularDv("7775215"));
        assertEquals('0', RutValidator.calcularDv("10000004"));
    }
}
