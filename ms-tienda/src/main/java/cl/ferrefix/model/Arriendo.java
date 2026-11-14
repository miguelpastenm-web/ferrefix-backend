package cl.ferrefix.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Arriendo de UNA unidad física. Tarifa y total quedan congelados al contratar. */
@Entity
@Table(name = "ARRIENDOS")
@Getter
@Setter
@NoArgsConstructor
public class Arriendo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UNIDAD_ID", nullable = false, updatable = false)
    private MaquinaUnidad unidad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USUARIO_ID", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "TARIFA_DIA", nullable = false, updatable = false)
    private Integer tarifaDia;

    @Column(name = "DIAS", nullable = false, updatable = false)
    private Integer dias;

    @Column(name = "FECHA_INICIO", nullable = false, updatable = false)
    private LocalDate fechaInicio;

    /** Último día de arriendo, inclusive: inicio + días - 1. */
    @Column(name = "FECHA_FIN", nullable = false, updatable = false)
    private LocalDate fechaFin;

    @Column(name = "TOTAL", nullable = false, updatable = false)
    private Long total;

    @Column(name = "TELEFONO_CONTACTO", nullable = false, length = 20)
    private String telefonoContacto;

    @Column(name = "DIRECCION_FAENA", nullable = false, length = 200)
    private String direccionFaena;

    @Column(name = "REGION_FAENA", nullable = false, length = 80)
    private String regionFaena;

    @Column(name = "COMUNA_FAENA", nullable = false, length = 80)
    private String comunaFaena;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private EstadoArriendo estado = EstadoArriendo.VIGENTE;

    @Column(name = "FECHA_DEVOLUCION")
    private LocalDateTime fechaDevolucion;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
}
