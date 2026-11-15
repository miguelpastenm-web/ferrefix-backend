package cl.ferrefix.db;

import cl.ferrefix.model.dto.ActualizarPerfilRequestDto;
import cl.ferrefix.model.dto.RegisterRequestDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Amarra los largos de los DTO de usuario a las columnas de USUARIOS (V1): si uno cambia sin el otro, falla aquí. */
class EsquemaV1Test {

    static String sql;
    static Validator validator;

    @BeforeAll
    static void cargar() throws IOException {
        try (InputStream in = EsquemaV1Test.class.getResourceAsStream("/db/migration/V1__usuarios_y_roles.sql")) {
            assertNotNull(in, "No existe V1__usuarios_y_roles.sql");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    /** Largo declarado de una columna de la tabla USUARIOS, y si es nulable. */
    private record Columna(int largo, boolean nulable) { }

    private static Columna columna(String nombre) {
        Matcher m = Pattern.compile("(?m)^\\s*" + nombre + "\\s+VARCHAR2\\((\\d+) CHAR\\)(\\s+NOT NULL)?[,\\s]").matcher(sql);
        assertTrue(m.find(), "No se encontró la columna " + nombre + " en V1");
        return new Columna(Integer.parseInt(m.group(1)), m.group(2) == null);
    }

    private static int sizeMax(Class<?> dto, String propiedad) {
        for (ConstraintDescriptor<?> d : validator.getConstraintsForClass(dto).getConstraintsForProperty(propiedad).getConstraintDescriptors()) {
            if (d.getAnnotation() instanceof Size s) {
                return s.max();
            }
        }
        return fail("El campo " + propiedad + " de " + dto.getSimpleName() + " no tiene @Size");
    }

    @Test
    void lasCuatroColumnasDelPerfilExistenYSonNulables() {
        for (String c : new String[]{"TELEFONO", "DIRECCION", "REGION", "COMUNA"}) {
            assertTrue(columna(c).nulable(), c + " debe ser nulable (un opcional en blanco se guarda como null)");
        }
        assertFalse(columna("NOMBRE").nulable(), "NOMBRE es obligatorio");
    }

    @Test
    void losLargosDelDtoDePerfilSonLosDeLasColumnas() {
        assertEquals(120, columna("NOMBRE").largo());
        assertEquals(20, columna("TELEFONO").largo());
        assertEquals(200, columna("DIRECCION").largo());
        assertEquals(80, columna("REGION").largo());
        assertEquals(80, columna("COMUNA").largo());

        assertEquals(columna("NOMBRE").largo(), sizeMax(ActualizarPerfilRequestDto.class, "nombre"));
        assertEquals(columna("TELEFONO").largo(), sizeMax(ActualizarPerfilRequestDto.class, "telefono"));
        assertEquals(columna("DIRECCION").largo(), sizeMax(ActualizarPerfilRequestDto.class, "direccion"));
        assertEquals(columna("REGION").largo(), sizeMax(ActualizarPerfilRequestDto.class, "region"));
        assertEquals(columna("COMUNA").largo(), sizeMax(ActualizarPerfilRequestDto.class, "comuna"));
    }

    @Test
    void elRegistroUsaLosMismosLargosQueLaTabla() {
        assertEquals(columna("NOMBRE").largo(), sizeMax(RegisterRequestDto.class, "nombre"));
        assertEquals(columna("TELEFONO").largo(), sizeMax(RegisterRequestDto.class, "telefono"));
        assertEquals(columna("DIRECCION").largo(), sizeMax(RegisterRequestDto.class, "direccion"));
        assertEquals(columna("REGION").largo(), sizeMax(RegisterRequestDto.class, "region"));
        assertEquals(columna("COMUNA").largo(), sizeMax(RegisterRequestDto.class, "comuna"));
        assertEquals(12, columna("RUT").largo());
        assertEquals(12, sizeMax(RegisterRequestDto.class, "rut"));
    }

    @Test
    void rutYEmailSonUnicosYElPerfilNoLosEdita() {
        assertTrue(sql.contains("CONSTRAINT UK_USUARIOS_RUT UNIQUE (RUT)"));
        assertTrue(sql.contains("CONSTRAINT UK_USUARIOS_EMAIL UNIQUE (EMAIL)"));
        // El DTO de edición de perfil no tiene esos campos: no hay forma de cambiarlos por ahí.
        java.util.Set<String> componentes = java.util.Arrays.stream(ActualizarPerfilRequestDto.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of("nombre", "telefono", "direccion", "region", "comuna"), componentes,
                "El perfil solo debe poder editar estos cinco campos");
    }
}
