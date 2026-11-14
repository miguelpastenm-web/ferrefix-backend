package cl.ferrefix.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoliticaEnvioTest {

    private final PoliticaEnvio politica = new PoliticaEnvio(3990, 50_000);

    @Test
    void gratisSoloSiElSubtotalSuperaElUmbral() {
        assertEquals(3990, politica.costoPara(1));
        assertEquals(3990, politica.costoPara(49_999));
        assertEquals(3990, politica.costoPara(50_000));   // "mayor que" 50.000: exactamente 50.000 SÍ paga envío
        assertEquals(0, politica.costoPara(50_001));
        assertEquals(0, politica.costoPara(5_000_000));
    }

    @Test
    void ambosValoresSonConfigurables() {
        PoliticaEnvio otra = new PoliticaEnvio(5000, 100_000);
        assertEquals(5000, otra.costoPara(100_000));
        assertEquals(0, otra.costoPara(100_001));
    }
}
