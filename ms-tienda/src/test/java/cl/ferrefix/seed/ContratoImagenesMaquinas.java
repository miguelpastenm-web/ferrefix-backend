package cl.ferrefix.seed;

import java.util.List;

/**
 * Contrato entre ms-arriendos y ms-tienda: las 4 máquinas iniciales apuntan a estas URLs (las siembra ms-arriendos) y
 * los archivos viven en seed-media/maquinas de ms-tienda, que sirve /media/**. Esta lista está en ambos servicios:
 * si una cambia y la otra no, el test de cada lado lo detecta.
 */
final class ContratoImagenesMaquinas {

    static final List<String> URLS = List.of(
            "/media/maquinas/maq-bet-02.webp",
            "/media/maquinas/maq-bosch-01.webp",
            "/media/maquinas/maq-gen-03.webp",
            "/media/maquinas/maq-plac-04.webp");

    private ContratoImagenesMaquinas() {
    }
}
