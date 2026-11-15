package cl.ferrefix.config;

import cl.ferrefix.security.JwtAuthenticationFilter;
import cl.ferrefix.security.RestAccessDeniedHandler;
import cl.ferrefix.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad stateless con JWT. Los tokens los emite ms-auth; aquí solo se verifican (mismo secreto) y la identidad
 * y los roles se toman del propio token.
 *  - Públicos: lectura de productos, /media/**, el documento OpenAPI y health.
 *  - Todo lo demás exige token; el rol se controla por endpoint con @PreAuthorize("hasRole('ADMIN')").
 *  - 401 y 403 salen en JSON (ErrorResponseDto) desde los handlers de seguridad.
 *  - CORS NO se configura aquí: lo resuelve el gateway (único punto de entrada). Si este servicio también añadiera
 *    Access-Control-Allow-Origin, el navegador recibiría la cabecera duplicada y rechazaría la respuesta.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /**
     * JwtAuthenticationFilter es un @Component, así que Spring Boot además lo registraría por su cuenta como filtro
     * del contenedor, FUERA de la cadena de seguridad y (según el entorno) antes que ella: ahí autentica, pero
     * SecurityContextHolderFilter descarta ese contexto y la copia que sí está en la cadena se salta por ser
     * OncePerRequestFilter. Se deshabilita ese registro para que el filtro viva SOLO dentro de la cadena.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterSoloEnLaCadena() {
        FilterRegistrationBean<JwtAuthenticationFilter> registro = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/productos", "/api/v1/productos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/media/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Escritura de productos solo ADMIN, a nivel de URL: así el 401/403 se decide ANTES de validar el body.
                        // Se mantiene además @PreAuthorize en el controller (defensa en profundidad).
                        .requestMatchers(HttpMethod.POST, "/api/v1/productos", "/api/v1/productos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/productos", "/api/v1/productos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/productos", "/api/v1/productos/**").hasRole("ADMIN")
                        // Órdenes: comprar y ver las propias solo CLIENTE (ADMIN no compra); gestionar solo ADMIN.
                        // GET /ordenes/{id} queda para cualquier autenticado (el service oculta las ajenas con 404).
                        .requestMatchers(HttpMethod.POST, "/api/v1/ordenes").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ordenes/mis-ordenes").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ordenes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/ordenes/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/ordenes/**").hasRole("ADMIN")
                        // Imágenes: cualquier método sobre /api/v1/imagenes exige ADMIN (además de @PreAuthorize)
                        .requestMatchers("/api/v1/imagenes", "/api/v1/imagenes/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
