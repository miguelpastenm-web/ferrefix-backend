package cl.ferrefix.util;

import cl.ferrefix.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** El parámetro "sort" solo admite campos de una lista cerrada: nunca se arma un orden con texto del cliente. */
class SortUtilTest {

    private static final Map<String, String> CAMPOS = Map.of("nombre", "nombre", "tarifa", "tarifaDia", "fecha", "createdAt");

    @Test
    void sinParametroOrdenaPorNombreYLuegoPorId() {
        for (String vacio : new String[]{null, "", "   "}) {
            Sort s = SortUtil.parsear(vacio, CAMPOS);
            assertEquals(Sort.by(Sort.Order.asc("nombre"), Sort.Order.asc("id")), s, "'" + vacio + "'");
        }
    }

    @Test
    void sinDireccionOrdenaAscendente() {
        assertEquals(Sort.by(Sort.Order.asc("tarifaDia"), Sort.Order.asc("id")), SortUtil.parsear("tarifa", CAMPOS));
    }

    @Test
    void conDireccionRespetaAscYDescSinImportarMayusculasNiEspacios() {
        assertEquals(Sort.by(Sort.Order.desc("tarifaDia"), Sort.Order.asc("id")), SortUtil.parsear("tarifa,desc", CAMPOS));
        assertEquals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")), SortUtil.parsear(" FECHA , DESC ", CAMPOS));
        assertEquals(Sort.by(Sort.Order.asc("nombre"), Sort.Order.asc("id")), SortUtil.parsear("Nombre,ASC", CAMPOS));
    }

    @Test
    void elAliasPublicoSeTraduceALaPropiedadDeLaEntidad() {
        Sort s = SortUtil.parsear("tarifa", CAMPOS);
        assertEquals("tarifaDia", s.iterator().next().getProperty());
    }

    @Test
    void unCampoQueNoEstaEnLaListaSeRechaza() {
        for (String malo : new String[]{"password", "tarifaDia", "id", "nombre;drop", "tarifa,sideways", "tarifa,asc,desc", ",asc"}) {
            assertThrows(BadRequestException.class, () -> SortUtil.parsear(malo, CAMPOS), malo);
        }
    }

    @Test
    void elMensajeDeErrorListaLosCamposPermitidosOrdenados() {
        BadRequestException e = assertThrows(BadRequestException.class, () -> SortUtil.parsear("otro", CAMPOS));
        assertTrue(e.getMessage().contains("[fecha, nombre, tarifa]"), e.getMessage());
    }
}
