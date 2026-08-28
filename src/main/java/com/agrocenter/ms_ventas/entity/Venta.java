package com.agrocenter.ms_ventas.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Entity
@Table(
        name = "ventas",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ventas_cliente_idempotencia",
                columnNames = {"cliente_id", "idempotency_key"}
        ),
        indexes = {
                @Index(name = "idx_ventas_cliente_id", columnList = "cliente_id"),
                @Index(name = "idx_ventas_fecha_creacion", columnList = "fecha_creacion"),
                @Index(name = "idx_ventas_estado", columnList = "estado"),
                @Index(name = "idx_ventas_cliente_fecha", columnList = "cliente_id, fecha_creacion")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false, length = 100, updatable = false)
    private String clienteId;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVenta estado;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "motivo_cancelacion", length = 500)
    private String motivoCancelacion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(
            mappedBy = "venta",
            fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            orphanRemoval = true
    )
    @OrderBy("id ASC")
    private List<DetalleVenta> detalles = new ArrayList<>();

    public Venta(
            String clienteId,
            BigDecimal subtotal,
            BigDecimal total,
            String idempotencyKey,
            String requestHash
    ) {
        this.clienteId = clienteId;
        this.estado = EstadoVenta.PENDIENTE;
        this.subtotal = subtotal;
        this.total = total;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public void agregarDetalle(DetalleVenta detalle) {
        detalle.asociarVenta(this);
        this.detalles.add(detalle);
    }

    public List<DetalleVenta> getDetalles() {
        return Collections.unmodifiableList(detalles);
    }

    public void confirmar() {
        if (estado != EstadoVenta.PENDIENTE) {
            throw new IllegalStateException("Solo una venta pendiente puede confirmarse");
        }
        this.estado = EstadoVenta.CONFIRMADA;
        this.motivoCancelacion = null;
    }

    public void cancelar(String motivo) {
        if (estado == EstadoVenta.CONFIRMADA) {
            throw new IllegalStateException("Una venta confirmada no puede cancelarse automaticamente");
        }
        this.estado = EstadoVenta.CANCELADA;
        this.motivoCancelacion = motivo;
    }

    @PrePersist
    void prePersist() {
        Instant ahora = Instant.now();
        this.fechaCreacion = ahora;
        this.createdAt = ahora;
        this.updatedAt = ahora;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
