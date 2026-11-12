package cl.ferrefix.repository;

import cl.ferrefix.model.Maquina;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MaquinaRepository extends JpaRepository<Maquina, Long>, JpaSpecificationExecutor<Maquina> {

    boolean existsByCodigo(String codigo);

    /**
     * SELECT ... FOR UPDATE WAIT 10 sobre la fila del modelo. Es el primer paso de TODA operación que cambia
     * unidades (arrendar, devolver, cancelar, alta y baja de unidades): serializa las operaciones del mismo
     * modelo, así los conteos para recalcular su estado son siempre exactos. Si la espera supera 10 s se lanza
     * una excepción de bloqueo (que la API responde como 409).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("select m from Maquina m where m.id = :id")
    Optional<Maquina> findByIdForUpdate(@Param("id") Long id);
}
