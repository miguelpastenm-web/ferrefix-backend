package cl.ferrefix.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Unidad física de un modelo. Su estado solo cambia al arrendar, devolver o cancelar un arriendo. */
@Entity
@Table(name = "MAQUINA_UNIDADES")
@Getter
@Setter
@NoArgsConstructor
public class MaquinaUnidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MAQUINA_ID", nullable = false)
    private Maquina maquina;

    @Column(name = "CODIGO", nullable = false, unique = true, length = 40)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private EstadoUnidad estado = EstadoUnidad.DISPONIBLE;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
