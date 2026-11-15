package cl.ferrefix.repository;

import cl.ferrefix.model.Cotizacion;
import cl.ferrefix.model.EstadoCotizacion;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {

    List<Cotizacion> findByEstado(EstadoCotizacion estado, Sort sort);
}
