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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad stateless con JWT del servicio de autenticación.
 *  - Públicos: login, register, refresh, el documento OpenAPI y health.
 *  - Todo lo demás (GET /auth/me, PUT /users/me) exige un access token válido.
 *  - 401 y 403 salen en JSON (ErrorResponseDto) desde los handlers de seguridad.
 *  - CORS NO se configura aquí: lo resuelve el gateway (único punto de entrada).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JwtAuthenticationFilter es un @Component, así que Spring Boot además lo registraría por su cuenta como filtro
     * del contenedor, FUERA de la cadena de seguridad. Se deshabilita ese registro para que viva SOLO dentro de la cadena.
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
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Perfil propio: cualquier usuario autenticado (CLIENTE o ADMIN); la identidad sale del token.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/me").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
