package cl.ferrefix.service;

import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.EstadoMaquina;
import cl.ferrefix.model.EstadoUnidad;
import cl.ferrefix.model.Maquina;
import cl.ferrefix.repository.MaquinaRepository;
import cl.ferrefix.repository.MaquinaUnidadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Reglas compartidas por todo lo que cambia unidades (alta/baja de unidades, arrendar, devolver, cancelar):
 * primero se BLOQUEA la fila del modelo y después se recalcula su estado dentro de la misma transacción.
 */
@Component
@RequiredArgsConstructor
public class MaquinaDisponibilidad {

    private final MaquinaRepository maquinaRepository;
    private final MaquinaUnidadRepository unidadRepository;

    /** SELECT ... FOR UPDATE del modelo. Debe ser el primer acceso a sus unidades en la transacción. */
    public Maquina bloquear(Long maquinaId) {
        return maquinaRepository.findByIdForUpdate(maquinaId)
                .orElseThrow(() -> new ResourceNotFoundException("Máquina no encontrada: " + maquinaId));
    }

    /** DISPONIBLE si queda al menos una unidad libre; si no, NO_DISPONIBLE. Se llama con el modelo ya bloqueado. */
    public void recalcularEstado(Maquina maquina) {
        long libres = unidadRepository.countByMaquinaIdAndEstado(maquina.getId(), EstadoUnidad.DISPONIBLE);
        maquina.setEstado(libres > 0 ? EstadoMaquina.DISPONIBLE : EstadoMaquina.NO_DISPONIBLE);
        maquina.setUpdatedAt(LocalDateTime.now());
    }
}
