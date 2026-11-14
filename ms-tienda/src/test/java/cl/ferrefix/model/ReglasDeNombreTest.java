package cl.ferrefix.model;

import cl.ferrefix.model.dto.ActualizarPerfilRequestDto;
import cl.ferrefix.model.dto.RegisterRequestDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * El perfil NO puede aceptar un nombre que el registro rechaza (ni al revés). Se compara de dos formas:
 * las restricciones declaradas y el resultado real de validar una batería de nombres.
 */
class ReglasDeNombreTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** Cada restricción como "Tipo{atributos}", sin el mensaje (que puede diferir sin cambiar la regla). */
    private Set<String> restricciones(Class<?> dto) {
        return validator.getConstraintsForClass(dto).getConstraintsForProperty("nombre").getConstraintDescriptors().stream()
                .map(ReglasDeNombreTest::describir)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private static String describir(ConstraintDescriptor<?> d) {
        Map<String, Object> atributos = new TreeMap<>(d.getAttributes());
        atributos.remove("message");
        atributos.remove("groups");
        atributos.remove("payload");
        return d.getAnnotation().annotationType().getSimpleName() + atributos;
    }

    private boolean registroAcepta(String nombre) {
        Set<ConstraintViolation<RegisterRequestDto>> v = validator.validateValue(RegisterRequestDto.class, "nombre", nombre);
        return v.isEmpty();
    }

    private boolean perfilAcepta(String nombre) {
        Set<ConstraintViolation<ActualizarPerfilRequestDto>> v = validator.validateValue(ActualizarPerfilRequestDto.class, "nombre", nombre);
        return v.isEmpty();
    }

    @Test
    void lasRestriccionesDeclaradasSonLasMismas() {
        assertFalse(restricciones(RegisterRequestDto.class).isEmpty());
        assertEquals(restricciones(RegisterRequestDto.class), restricciones(ActualizarPerfilRequestDto.class),
                "Si agregas una regla al nombre del registro (o del perfil), agrégala en ambos");
    }

    @Test
    void ambosAceptanYRechazanExactamenteLosMismosNombres() {
        List<String> nombres = List.of(
                "", " ", "   ", "a", "Ana", "Juan Maestro", "José Núñez-Pérez", "María O'Brien", "Constructora Los Andes SpA",
                "123", "Ana 2", "Ana <script>", "x".repeat(60), "x".repeat(120), "x".repeat(121), "ñ".repeat(120), "Ana\nPérez", "\t");
        for (String nombre : nombres) {
            assertEquals(registroAcepta(nombre), perfilAcepta(nombre), "Difieren para el nombre: '" + nombre + "'");
        }
        // y nulo también
        assertEquals(registroAcepta(null), perfilAcepta(null));
    }
}
