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
 *  - Públicos: lectura de máquinas, crear cotizaciones, el documento OpenAPI y health.
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
                        // El detalle de unidades es solo para ADMIN: debe ir ANTES del permitAll de /maquinas/**
                        .requestMatchers(HttpMethod.GET, "/api/v1/maquinas/*/unidades").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/maquinas", "/api/v1/maquinas/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Escritura de máquinas y de sus unidades solo ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/v1/maquinas", "/api/v1/maquinas/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/maquinas", "/api/v1/maquinas/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/maquinas", "/api/v1/maquinas/**").hasRole("ADMIN")
                        // Arriendos: arrendar y ver los propios solo CLIENTE; gestionar solo ADMIN.
                        // GET /arriendos/{id} queda para cualquier autenticado (el service oculta los ajenos con 404).
                        .requestMatchers(HttpMethod.POST, "/api/v1/arriendos").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/arriendos/mis-arriendos").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/arriendos").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/arriendos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/arriendos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/arriendos/**").hasRole("ADMIN")
                        // Cotizaciones: CREAR es público (con o sin token); todo lo demás es solo ADMIN. El orden importa:
                        // la regla pública del POST va antes del "cualquier otro método = ADMIN".
                        .requestMatchers(HttpMethod.POST, "/api/v1/cotizaciones").permitAll()
                        .requestMatchers("/api/v1/cotizaciones", "/api/v1/cotizaciones/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
