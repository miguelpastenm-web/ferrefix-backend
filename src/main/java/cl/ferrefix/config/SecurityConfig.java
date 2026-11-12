package cl.ferrefix.config;

import cl.ferrefix.security.JwtAuthenticationFilter;
import cl.ferrefix.security.RestAccessDeniedHandler;
import cl.ferrefix.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Seguridad stateless con JWT.
 *  - Públicos: login, register, refresh, lectura de productos y máquinas, /media/**, Swagger y health.
 *  - Todo lo demás exige token; el rol se controla por endpoint con @PreAuthorize("hasRole('ADMIN')").
 *  - 401 y 403 salen en JSON (ErrorResponseDto) desde los handlers de seguridad.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Value("${ferrefix.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh").permitAll()
                        // El detalle de unidades es solo para ADMIN: debe ir ANTES del permitAll de /maquinas/**
                        .requestMatchers(HttpMethod.GET, "/api/v1/maquinas/*/unidades").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/productos", "/api/v1/productos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/maquinas", "/api/v1/maquinas/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/media/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Escritura de productos solo ADMIN, a nivel de URL: así el 401/403 se decide ANTES de validar el body.
                        // Se mantiene además @PreAuthorize en el controller (defensa en profundidad).
                        .requestMatchers(HttpMethod.POST, "/api/v1/productos", "/api/v1/productos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/productos", "/api/v1/productos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/productos", "/api/v1/productos/**").hasRole("ADMIN")
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
