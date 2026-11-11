package cl.ferrefix.config;

import cl.ferrefix.model.Rol;
import cl.ferrefix.model.Usuario;
import cl.ferrefix.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Crea un ADMIN y un CLIENTE de demostración al arrancar, solo si su contraseña viene
 * por variable de entorno (ADMIN_PASSWORD / CLIENT_PASSWORD). Sin contraseña no se crea nada:
 * no hay credenciales por defecto en el repositorio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final int MIN_PASSWORD = 8;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ferrefix.seed.admin-email}")
    private String adminEmail;
    @Value("${ferrefix.seed.admin-password:}")
    private String adminPassword;
    @Value("${ferrefix.seed.client-email}")
    private String clientEmail;
    @Value("${ferrefix.seed.client-password:}")
    private String clientPassword;

    @Override
    public void run(String... args) {
        crear("11111111-1", "Administrador Ferrefix", adminEmail, adminPassword, Rol.ADMIN);
        crear("15432890-4", "Cliente Demo", clientEmail, clientPassword, Rol.CLIENTE);
    }

    private void crear(String rut, String nombre, String email, String password, Rol rol) {
        if (password == null || password.length() < MIN_PASSWORD) {
            log.warn("Seed de {} omitido: defina su contraseña (mín. {} caracteres) en el .env", rol, MIN_PASSWORD);
            return;
        }
        if (usuarioRepository.existsByEmailIgnoreCase(email) || usuarioRepository.existsByRut(rut)) {
            return;
        }
        Usuario u = new Usuario();
        u.setRut(rut);
        u.setNombre(nombre);
        u.setEmail(email.trim().toLowerCase(Locale.ROOT));
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRoles(Set.of(rol));
        usuarioRepository.save(u);
        log.info("Usuario inicial {} creado: {}", rol, u.getEmail());
    }
}
