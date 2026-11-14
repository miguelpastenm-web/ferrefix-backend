package cl.ferrefix.service.impl;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.model.dto.ImagenResponseDto;
import cl.ferrefix.service.ImagenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Guarda imágenes en disco. Seguridad:
 *  - el tipo se decide por los BYTES INICIALES del archivo (no por Content-Type ni por la extensión);
 *  - el nombre final es un UUID + extensión según el tipo detectado: el nombre del cliente nunca se usa;
 *  - el destino sale de una lista cerrada, así que no hay forma de salirse de la carpeta de uploads.
 */
@Service
public class ImagenServiceImpl implements ImagenService {

    public static final Set<String> DESTINOS = Set.of("productos", "ofertas", "banners", "maquinas");

    enum Tipo {
        JPEG("image/jpeg", ".jpg"), PNG("image/png", ".png"), WEBP("image/webp", ".webp"), AVIF("image/avif", ".avif");

        final String mime;
        final String extension;

        Tipo(String mime, String extension) {
            this.mime = mime;
            this.extension = extension;
        }
    }

    private static final int BYTES_CABECERA = 64;

    private final Path raiz;
    private final long maxBytes;

    public ImagenServiceImpl(@Value("${ferrefix.upload.dir}") String dir,
                             @Value("${ferrefix.upload.max-bytes}") long maxBytes) {
        this.raiz = Path.of(dir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    @Override
    public ImagenResponseDto guardar(MultipartFile archivo, String destino) {
        String carpeta = destino == null ? "" : destino.trim().toLowerCase(Locale.ROOT);
        if (!DESTINOS.contains(carpeta)) {
            throw new BadRequestException("Destino inválido. Valores permitidos: banners, maquinas, ofertas, productos");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw new BadRequestException("Debe adjuntar un archivo de imagen");
        }
        if (archivo.getSize() > maxBytes) {
            throw new MaxUploadSizeExceededException(maxBytes);
        }

        try {
            byte[] contenido = archivo.getBytes();
            Tipo tipo = detectarTipo(contenido);
            if (tipo == null) {
                throw new BadRequestException("El archivo no es una imagen válida. Formatos permitidos: JPEG, PNG, WEBP y AVIF");
            }

            Path directorio = raiz.resolve(carpeta).normalize();
            if (!directorio.startsWith(raiz)) { // defensa extra; con la lista cerrada no debería ocurrir
                throw new BadRequestException("Destino inválido");
            }
            Files.createDirectories(directorio);
            String nombre = UUID.randomUUID() + tipo.extension;
            Files.write(directorio.resolve(nombre), contenido, StandardOpenOption.CREATE_NEW);

            return new ImagenResponseDto("/media/" + carpeta + "/" + nombre, carpeta, tipo.mime, contenido.length);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen", e);
        }
    }

    /** Firma (magic numbers) de cada formato. Devuelve null si no coincide con ninguno. */
    static Tipo detectarTipo(byte[] b) {
        if (startsWith(b, 0xFF, 0xD8, 0xFF)) {
            return Tipo.JPEG;
        }
        if (startsWith(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Tipo.PNG;
        }
        if (b.length >= 12 && ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 12).equals("WEBP")) {
            return Tipo.WEBP;
        }
        if (b.length >= 16 && ascii(b, 4, 8).equals("ftyp") && tieneMarcaAvif(b)) {
            return Tipo.AVIF;
        }
        return null;
    }

    /** Caja ISO-BMFF "ftyp": marca principal en 8..12 y marcas compatibles hasta el final de la caja. */
    private static boolean tieneMarcaAvif(byte[] b) {
        int tamanoCaja = ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        int fin = Math.min(Math.min(b.length, BYTES_CABECERA), tamanoCaja < 16 ? 16 : tamanoCaja);
        for (int i = 8; i + 4 <= fin; i += (i == 8 ? 8 : 4)) { // salta el campo minor_version (12..16)
            String marca = ascii(b, i, i + 4);
            if (marca.equals("avif") || marca.equals("avis")) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] b, int... firma) {
        if (b.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((b[i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }

    private static String ascii(byte[] b, int desde, int hasta) {
        return new String(Arrays.copyOfRange(b, desde, hasta), StandardCharsets.ISO_8859_1);
    }
}
