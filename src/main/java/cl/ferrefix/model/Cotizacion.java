package cl.ferrefix.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Solicitud de cotización por volumen. La puede crear cualquier visitante; solo un ADMIN la lee y la gestiona. */
@Entity
@Table(name = "COTIZACIONES")
@Getter
@Setter
@NoArgsConstructor
public class Cotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null si la pidió un visitante sin sesión (o con un token inválido o vencido). */
    @Column(name = "USUARIO_ID")
    private Long usuarioId;

    @Column(name = "NOMBRE", nullable = false, length = 80)
    private String nombre;

    @Column(name = "RUT", nullable = false, length = 12)
    private String rut;

    @Column(name = "EMAIL", nullable = false, length = 120)
    private String email;

    @Column(name = "TELEFONO", nullable = false, length = 20)
    private String telefono;

    @Column(name = "TIPO_PROYECTO", nullable = false, length = 60)
    private String tipoProyecto;

    @Column(name = "REGION", nullable = false, length = 80)
    private String region;

    @Column(name = "DETALLE", nullable = false, length = 2000)
    private String detalle;

    @Convert(converter = BooleanNumberConverter.class)
    @Column(name = "DESPACHO_OBRA", nullable = false)
    private boolean despachoObra = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private EstadoCotizacion estado = EstadoCotizacion.PENDIENTE;

    @Column(name = "NOTA_ADMIN", length = 1000)
    private String notaAdmin;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
}
