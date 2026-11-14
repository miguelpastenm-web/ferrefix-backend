package cl.ferrefix.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Sirve /media/** (público): primero lo subido por el ADMIN (carpeta de uploads) y luego las imágenes
 * de los productos iniciales que viajan dentro de la app (classpath:/seed-media/).
 * Los nombres son UUID (no cambian), por eso la caché es larga.
 */
@Configuration
public class MediaConfig implements WebMvcConfigurer {

    private final Path raiz;

    public MediaConfig(@Value("${ferrefix.upload.dir}") String dir) {
        this.raiz = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ubicacionUploads = raiz.toUri().toString();
        if (!ubicacionUploads.endsWith("/")) {
            ubicacionUploads += "/"; // sin la barra final, Spring no trata la ubicación como carpeta
        }
        registry.addResourceHandler("/media/**")
                .addResourceLocations(ubicacionUploads, "classpath:/seed-media/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
