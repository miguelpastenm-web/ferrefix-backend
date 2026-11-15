package cl.ferrefix.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de Swagger y esquema Bearer JWT (botón "Authorize" en la UI).
 * Sin URL de servidor fija: Swagger usa el host desde el que se abre (localhost o Elastic IP).
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Ferrefix API - Tienda", version = "1.0",
                description = "Tienda de Ferrefix: productos, imágenes y órdenes de compra."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
