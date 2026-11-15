package cl.ferrefix.util;

import cl.ferrefix.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas comunes de las especificaciones (clave-valor ordenadas) de productos y de máquinas. */
class EspecificacionesUtilTest {

    private static Map<String, String> mapa(String... paresClaveValor) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < paresClaveValor.length; i += 2) {
            m.put(paresClaveValor[i], paresClaveValor[i + 1]);
        }
        return m;
    }

    @Test
    void nuloOVacioDevuelveUnMapaVacio() {
        assertTrue(EspecificacionesUtil.normalizar(null).isEmpty());
        assertTrue(EspecificacionesUtil.normalizar(Map.of()).isEmpty());
    }

    @Test
    void recortaEspaciosYConservaElOrdenDeLlegada() {
        Map<String, String> r = EspecificacionesUtil.normalizar(mapa(" Potencia ", " 1750 W ", "Peso", "5 kg", "Color", "Azul"));
        assertEquals(List.of("Potencia", "Peso", "Color"), new ArrayList<>(r.keySet()));
        assertEquals("1750 W", r.get("Potencia"));
    }

    @Test
    void claveOValorVaciosOEnBlancoSeRechazan() {
        for (Map<String, String> malo : List.of(mapa("", "x"), mapa("  ", "x"), mapa("clave", ""), mapa("clave", "   "))) {
            assertThrows(BadRequestException.class, () -> EspecificacionesUtil.normalizar(malo), malo.toString());
        }
        Map<String, String> conNulo = new LinkedHashMap<>();
        conNulo.put("clave", null);
        assertThrows(BadRequestException.class, () -> EspecificacionesUtil.normalizar(conNulo));
        Map<String, String> claveNula = new LinkedHashMap<>();
        claveNula.put(null, "valor");
        assertThrows(BadRequestException.class, () -> EspecificacionesUtil.normalizar(claveNula));
    }

    @Test
    void laClaveAdmiteHasta80CaracteresYElValorHasta300() {
        assertDoesNotThrow(() -> EspecificacionesUtil.normalizar(mapa("k".repeat(80), "v".repeat(300))));
        BadRequestException clave = assertThrows(BadRequestException.class,
                () -> EspecificacionesUtil.normalizar(mapa("k".repeat(81), "v")));
        assertTrue(clave.getMessage().contains("80"), clave.getMessage());
        BadRequestException valor = assertThrows(BadRequestException.class,
                () -> EspecificacionesUtil.normalizar(mapa("Potencia", "v".repeat(301))));
        assertTrue(valor.getMessage().contains("Potencia") && valor.getMessage().contains("300"), valor.getMessage());
    }

    @Test
    void lasClavesRepetidasTrasRecortarSeRechazan() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> EspecificacionesUtil.normalizar(mapa("Peso", "5 kg", " Peso ", "6 kg")));
        assertTrue(e.getMessage().contains("Peso"), e.getMessage());
    }

    @Test
    void noModificaElMapaDeEntrada() {
        Map<String, String> entrada = mapa(" Peso ", " 5 kg ");
        EspecificacionesUtil.normalizar(entrada);
        assertEquals(" 5 kg ", entrada.get(" Peso "));
    }
}
