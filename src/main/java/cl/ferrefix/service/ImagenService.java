package cl.ferrefix.service;

import cl.ferrefix.model.dto.ImagenResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface ImagenService {

    /** Guarda la imagen en disco con nombre UUID. Destinos: productos, ofertas, banners. */
    ImagenResponseDto guardar(MultipartFile archivo, String destino);
}
