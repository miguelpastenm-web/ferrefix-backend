package cl.ferrefix.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Modelo de máquina (catálogo). Las unidades físicas viven en MaquinaUnidad. */
@Entity
@Table(name = "MAQUINAS")
@Getter
@Setter
@NoArgsConstructor
public class Maquina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "NOMBRE", nullable = false, length = 150)
    private String nombre;

    @Column(name = "MARCA", nullable = false, length = 60)
    private String marca;

    @Column(name = "CATEGORIA", nullable = false, length = 60)
    private String categoria;

    @Column(name = "TARIFA_DIA", nullable = false)
    private Integer tarifaDia;

    /** Derivado de las unidades: DISPONIBLE si queda al menos una libre. Lo mantiene el service. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private EstadoMaquina estado = EstadoMaquina.NO_DISPONIBLE;

    @Column(name = "INCLUYE", length = 500)
    private String incluye;

    @Column(name = "IMAGEN_URL", length = 300)
    private String imagenUrl;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "maquina", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    @BatchSize(size = 50)
    private List<MaquinaEspecificacion> especificaciones = new ArrayList<>();

    @PrePersist
    void prePersist() {
        LocalDateTime ahora = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = ahora;
        }
        if (updatedAt == null) {
            updatedAt = ahora;
        }
    }

    public void agregarEspecificacion(String clave, String valor, int orden) {
        MaquinaEspecificacion e = new MaquinaEspecificacion();
        e.setMaquina(this);
        e.setClave(clave);
        e.setValor(valor);
        e.setOrden(orden);
        especificaciones.add(e);
    }
}
