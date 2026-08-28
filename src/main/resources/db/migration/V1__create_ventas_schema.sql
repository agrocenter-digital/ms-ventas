CREATE TABLE ventas (
    id BIGSERIAL PRIMARY KEY,
    cliente_id VARCHAR(100) NOT NULL,
    fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL,
    estado VARCHAR(20) NOT NULL,
    subtotal NUMERIC(15, 2) NOT NULL,
    total NUMERIC(15, 2) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    motivo_cancelacion VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ventas_estado CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'CANCELADA')),
    CONSTRAINT ck_ventas_subtotal_no_negativo CHECK (subtotal >= 0),
    CONSTRAINT ck_ventas_total_no_negativo CHECK (total >= 0),
    CONSTRAINT uk_ventas_cliente_idempotencia UNIQUE (cliente_id, idempotency_key)
);

CREATE TABLE detalles_venta (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    sku VARCHAR(50) NOT NULL,
    nombre_producto VARCHAR(120) NOT NULL,
    cantidad INTEGER NOT NULL,
    precio_unitario NUMERIC(15, 2) NOT NULL,
    subtotal NUMERIC(15, 2) NOT NULL,
    CONSTRAINT fk_detalles_venta_venta FOREIGN KEY (venta_id) REFERENCES ventas (id),
    CONSTRAINT ck_detalles_venta_cantidad_positiva CHECK (cantidad > 0),
    CONSTRAINT ck_detalles_venta_precio_no_negativo CHECK (precio_unitario >= 0),
    CONSTRAINT ck_detalles_venta_subtotal_no_negativo CHECK (subtotal >= 0),
    CONSTRAINT uk_detalles_venta_producto UNIQUE (venta_id, producto_id)
);

CREATE INDEX idx_ventas_cliente_id ON ventas (cliente_id);
CREATE INDEX idx_ventas_fecha_creacion ON ventas (fecha_creacion DESC);
CREATE INDEX idx_ventas_estado ON ventas (estado);
CREATE INDEX idx_ventas_cliente_fecha ON ventas (cliente_id, fecha_creacion DESC);
CREATE INDEX idx_detalles_venta_venta_id ON detalles_venta (venta_id);
