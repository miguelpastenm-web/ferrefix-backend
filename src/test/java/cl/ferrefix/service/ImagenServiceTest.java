package cl.ferrefix.service;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.model.dto.ImagenResponseDto;
import cl.ferrefix.service.impl.ImagenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ImagenServiceTest {

    @TempDir Path dir;
    ImagenServiceImpl service;

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    @BeforeEach
    void setUp() {
        service = new ImagenServiceImpl(dir.toString(), 1024);
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] webp() {
        byte[] b = new byte[20];
        System.arraycopy(ascii("RIFF"), 0, b, 0, 4);
        System.arraycopy(ascii("WEBP"), 0, b, 8, 4);
        return b;
    }

    /** Caja ftyp: tamaño, "ftyp", marca principal, minor_version y marcas compatibles. */
    private static byte[] ftyp(String principal, String... compatibles) {
        int tam = 16 + 4 * compatibles.length;
        byte[] b = new byte[tam + 8];
        b[3] = (byte) tam;
        System.arraycopy(ascii("ftyp"), 0, b, 4, 4);
        System.arraycopy(ascii(principal), 0, b, 8, 4);
        for (int i = 0; i < compatibles.length; i++) {
            System.arraycopy(ascii(compatibles[i]), 0, b, 16 + 4 * i, 4);
        }
        return b;
    }

    private MockMultipartFile archivo(String nombreCliente, byte[] bytes) {
        return new MockMultipartFile("archivo", nombreCliente, "application/octet-stream", bytes);
    }

    private long archivosEnDisco() throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    // ---------------------------------------------------------------- formatos válidos
    @Test
    void guardaCadaFormatoConExtensionSegunSuContenido() throws IOException {
        record Caso(byte[] bytes, String ext, String mime) { }
        List<Caso> casos = List.of(
                new Caso(JPEG, ".jpg", "image/jpeg"),
                new Caso(PNG, ".png", "image/png"),
                new Caso(webp(), ".webp", "image/webp"),
                new Caso(ftyp("avif"), ".avif", "image/avif"),
                new Caso(ftyp("mif1", "miaf", "avif"), ".avif", "image/avif"));
        for (Caso c : casos) {
            ImagenResponseDto r = service.guardar(archivo("foto.bin", c.bytes()), "productos");
            assertTrue(r.url().matches("^/media/productos/[0-9a-f-]{36}\\" + c.ext() + "$"), r.url());
            assertEquals(c.mime(), r.tipo());
            assertEquals(c.bytes().length, r.tamano());
            assertTrue(Files.exists(dir.resolve(r.url().substring("/media/".length()))));
        }
    }

    @Test
    void nombreEsUuidYElNombreDelClienteSeIgnora() throws IOException {
        ImagenResponseDto r = service.guardar(archivo("../../etc/passwd.webp", PNG), "productos");
        String nombre = r.url().substring(r.url().lastIndexOf('/') + 1);
        assertTrue(nombre.endsWith(".png"));                              // extensión por contenido, no por el nombre
        assertDoesNotThrow(() -> UUID.fromString(nombre.replace(".png", "")));
        assertFalse(r.url().contains("passwd"));
        assertEquals(1, archivosEnDisco());                               // solo dentro de la carpeta de uploads
    }

    @Test
    void dosSubidasIgualesGeneranNombresDistintos() {
        String a = service.guardar(archivo("a.png", PNG), "productos").url();
        String b = service.guardar(archivo("a.png", PNG), "productos").url();
        assertNotEquals(a, b);
    }

    @Test
    void aceptaLosTresDestinosSinImportarMayusculas() {
        assertTrue(service.guardar(archivo("a.png", PNG), "Productos").url().startsWith("/media/productos/"));
        assertTrue(service.guardar(archivo("a.png", PNG), " OFERTAS ").url().startsWith("/media/ofertas/"));
        assertTrue(service.guardar(archivo("a.png", PNG), "banners").url().startsWith("/media/banners/"));
    }

    // ---------------------------------------------------------------- rechazos
    @Test
    void archivoFalsoConExtensionWebpSeRechaza() throws IOException {
        MockMultipartFile falso = new MockMultipartFile("archivo", "foto.webp", "image/webp",
                "<?php echo 'hola'; ?>".getBytes(StandardCharsets.UTF_8));
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.guardar(falso, "productos"));
        assertTrue(ex.getMessage().contains("no es una imagen válida"));
        assertEquals(0, archivosEnDisco());
    }

    @Test
    void rechazaGifBmpYOtrosContenedores() throws IOException {
        byte[] gif = ascii("GIF89a\u0001\u0000\u0001\u0000");
        byte[] bmp = ascii("BM\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000");
        byte[] wav = new byte[20];                                         // RIFF pero WAVE, no WEBP
        System.arraycopy(ascii("RIFF"), 0, wav, 0, 4);
        System.arraycopy(ascii("WAVE"), 0, wav, 8, 4);
        for (byte[] malo : List.of(gif, bmp, wav, ftyp("heic", "mif1"), ftyp("mp42", "isom"))) {
            assertThrows(BadRequestException.class, () -> service.guardar(archivo("x.png", malo), "productos"));
        }
        assertEquals(0, archivosEnDisco());
    }

    @Test
    void rechazaArchivosMuyCortosSinFallarConError500() {
        assertThrows(BadRequestException.class, () -> service.guardar(archivo("x.png", new byte[]{1, 2}), "productos"));
    }

    @Test
    void rechazaArchivoVacioONulo() {
        assertThrows(BadRequestException.class, () -> service.guardar(archivo("x.png", new byte[0]), "productos"));
        assertThrows(BadRequestException.class, () -> service.guardar(null, "productos"));
    }

    @Test
    void rechazaDestinosFueraDeLaLista() throws IOException {
        for (String malo : new String[]{"otros", "../x", "productos/../..", "", "  ", null, "/etc"}) {
            assertThrows(BadRequestException.class, () -> service.guardar(archivo("a.png", PNG), malo), String.valueOf(malo));
        }
        assertEquals(0, archivosEnDisco());
    }

    @Test
    void archivoMasGrandeQueElLimiteLanza413() throws IOException {
        byte[] grande = new byte[1025];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);
        assertThrows(MaxUploadSizeExceededException.class, () -> service.guardar(archivo("a.png", grande), "productos"));
        assertEquals(0, archivosEnDisco());
    }

    @Test
    void archivoJustoEnElLimiteSeAcepta() {
        byte[] justo = new byte[1024];
        System.arraycopy(PNG, 0, justo, 0, PNG.length);
        assertDoesNotThrow(() -> service.guardar(archivo("a.png", justo), "productos"));
    }
}
