CREATE TABLE consulta_b2b (
    id BIGSERIAL PRIMARY KEY,
    sku_producto VARCHAR(100),
    nombre_producto VARCHAR(255),
    cantidad INTEGER NOT NULL DEFAULT 1,
    nombre_cliente VARCHAR(150) NOT NULL,
    correo_contacto VARCHAR(150) NOT NULL,
    mensaje TEXT NOT NULL,
    origen VARCHAR(50) NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_consulta_b2b_estado CHECK (estado IN ('PENDIENTE', 'RESPONDIDO', 'ARCHIVADO')),
    CONSTRAINT ck_consulta_b2b_origen CHECK (origen IN ('PRODUCTO_MAYORISTA', 'CONTACTO_HOME')),
    CONSTRAINT ck_consulta_b2b_cantidad_positiva CHECK (cantidad > 0)
);

CREATE INDEX idx_consulta_b2b_estado ON consulta_b2b (estado);
CREATE INDEX idx_consulta_b2b_fecha_creacion ON consulta_b2b (fecha_creacion DESC);
CREATE INDEX idx_consulta_b2b_correo ON consulta_b2b (correo_contacto);
