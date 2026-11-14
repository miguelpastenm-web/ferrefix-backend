package cl.ferrefix.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "USUARIOS")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "RUT", nullable = false, unique = true, length = 12)
    private String rut;

    @Column(name = "NOMBRE", nullable = false, length = 120)
    private String nombre;

    /** Se usa como nombre de usuario en el login. */
    @Column(name = "EMAIL", nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "TELEFONO", length = 20)
    private String telefono;

    @Column(name = "DIRECCION", length = 200)
    private String direccion;

    @Column(name = "REGION", length = 80)
    private String region;

    @Column(name = "COMUNA", length = 80)
    private String comuna;

    @Convert(converter = BooleanNumberConverter.class)
    @Column(name = "ACTIVO", nullable = false)
    private boolean activo = true;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "USUARIO_ROLES", joinColumns = @JoinColumn(name = "USUARIO_ID"))
    @Column(name = "ROL", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<Rol> roles = new HashSet<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
