# AgroCenter Digital - ms-ventas

Microservicio responsable del checkout, persistencia e historial de ventas. Es un
proyecto Maven independiente dentro del repositorio y no comparte tablas ni acceso
JDBC con `ms-inventario`.

## Arquitectura

```text
React
  -> API Gateway
  -> bff-web (proyecto futuro)
  -> ms-ventas :8082
       -> db_ventas (PostgreSQL)
       -> ms-inventario :8081 (REST interno)
```

En esta etapa solo se implementa `ms-ventas`. El BFF y la configuracion definitiva
de AWS Cognito se conectaran posteriormente mediante los contratos y variables ya
preparados.

## Responsabilidades

- obtiene `clienteId` exclusivamente desde el claim confiable `sub` del JWT;
- valida productos y disponibilidad en `ms-inventario`;
- usa el precio, SKU y nombre devueltos por inventario como snapshot historico;
- calcula subtotales y total con `BigDecimal`;
- registra `Venta` y `DetalleVenta` solo en `db_ventas`;
- descuenta inventario por REST y mantiene estados `PENDIENTE`, `CONFIRMADA` y
  `CANCELADA`;
- protege consultas por propietario para evitar IDOR;
- soporta idempotencia, correlacion, errores JSON, Actuator y OpenAPI.

No incluye pagos, facturacion, despacho, notificaciones ni mensajeria.

## Requisitos

- Java 21
- Docker y Docker Compose, o PostgreSQL 16 accesible
- `ms-inventario` accesible por HTTP para ejecutar un checkout real

El proyecto usa Spring Boot 3.5.7. Las pruebas usan H2 y no necesitan PostgreSQL,
AWS ni un inventario real.

## Variables de entorno

| Variable | Uso | Ejemplo seguro |
|---|---|---|
| `SERVER_PORT` | Puerto HTTP | `8082` |
| `DB_URL` | JDBC de `db_ventas` | `jdbc:postgresql://localhost:5432/db_ventas` |
| `DB_USERNAME` | Usuario PostgreSQL | `postgres` |
| `DB_PASSWORD` | Contrasena PostgreSQL | sin valor por defecto |
| `INVENTORY_SERVICE_URL` | URL base de `ms-inventario` | `http://localhost:8081` |
| `INVENTORY_CONNECT_TIMEOUT` | Timeout de conexion | `2s` |
| `INVENTORY_READ_TIMEOUT` | Timeout total de respuesta | `3s` |
| `INVENTORY_SERVICE_TOKEN` | Bearer interno opcional | vacio; se propaga el JWT recibido |
| `COGNITO_ISSUER_URI` | Issuer esperado en produccion | URL del User Pool |
| `COGNITO_JWK_SET_URI` | JWKS de Cognito | URL `/.well-known/jwks.json` |
| `COGNITO_AUDIENCE` | Audience requerida | `agrocenter-api` |
| `SPRING_PROFILES_ACTIVE` | Perfil | `dev` o `prod` |
| `DEV_JWT_SECRET` | Clave HS256 local de al menos 32 caracteres | solo perfil `dev` |
| `DEV_JWT_ISSUER` | Issuer local compartido | `http://localhost:8081/dev-issuer` |
| `SWAGGER_ENABLED` | Habilitar OpenAPI | `true` en desarrollo |

`INVENTORY_SERVICE_TOKEN`, si se configura, debe ser un JWT de servicio con los
scopes de lectura y escritura esperados por inventario. Si se deja vacio,
`ms-ventas` propaga el Bearer del usuario. Nunca se registra ningun token en logs.

## Ejecucion local con Docker

Crear la configuracion local:

```powershell
Copy-Item .env.example .env
```

Editar `.env` y reemplazar como minimo `POSTGRES_PASSWORD` y `DEV_JWT_SECRET`.
Luego:

```powershell
docker compose up --build -d
docker compose ps
curl.exe http://localhost:8082/actuator/health
```

El Compose de este proyecto levanta exclusivamente `ms-ventas` y su PostgreSQL.
No crea ni modifica el contenedor de inventario. Por defecto intenta alcanzar un
`ms-inventario` ya iniciado en el host mediante
`http://host.docker.internal:8081`.

Para detenerlo sin eliminar los datos:

```powershell
docker compose down
```

La imagen final usa Java 21, un usuario no-root, filesystem de solo lectura,
capabilities eliminadas y health check.

## Perfiles y JWT

`ms-ventas` es un OAuth2 Resource Server: no implementa login, no almacena
contrasenas y no emite tokens.

- `dev`: valida JWT HS256 con `DEV_JWT_SECRET`, issuer y audience. Permite usar un
  token local emitido externamente. Para probar junto con `ms-inventario`, ambos
  procesos deben compartir `DEV_JWT_SECRET`, `DEV_JWT_ISSUER` y audience.
- `prod`: exige issuer, JWKS y audience mediante variables de entorno; Swagger se
  desactiva.
- `test`: usa JWT simulados de Spring Security y H2; no llama a AWS.

Los roles se extraen desde `cognito:groups` y `custom:role`, manteniendo
compatibilidad con `CLIENTE` y `ADMIN`. Los scopes OAuth tambien se conservan como
autoridades `SCOPE_*`.

La aplicacion es stateless. CSRF se deshabilita porque la API usa exclusivamente
Bearer tokens y no autenticacion basada en cookies. No se habilita CORS en este
microservicio: debe administrarse en API Gateway/BFF.

## Endpoints

| Metodo | Ruta | Acceso | Resultado |
|---|---|---|---|
| `POST` | `/api/v1/ventas` | `CLIENTE` | Crea checkout; requiere `Idempotency-Key` |
| `GET` | `/api/v1/ventas/mis-pedidos` | `CLIENTE` | Lista solo ventas del `sub` autenticado |
| `GET` | `/api/v1/ventas/{id}` | propietario o `ADMIN` | Detalle de una venta |
| `GET` | `/api/v1/ventas` | `ADMIN` | Auditoria paginada de todas las ventas |
| `GET` | `/actuator/health` | red interna | Salud sin detalles sensibles |
| `GET` | `/swagger-ui.html` | desarrollo | UI de OpenAPI |
| `GET` | `/v3/api-docs` | desarrollo | Contrato OpenAPI JSON |

Los listados aceptan `pagina` desde `0` y `tamanio` entre `1` y `100`.

## Crear una venta

```http
POST /api/v1/ventas
Authorization: Bearer <jwt-cliente>
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
X-Correlation-ID: checkout-web-001
Content-Type: application/json

{
  "items": [
    {"productoId": 10, "cantidad": 2},
    {"productoId": 15, "cantidad": 3}
  ]
}
```

El request no acepta `clienteId`, precios, subtotales ni total. Una creacion nueva
responde `201 Created`. Repetir exactamente la solicitud con la misma clave
responde `200 OK` y `Idempotent-Replay: true`, sin crear ni descontar nuevamente.
Reutilizar la clave con otros items responde `409 IDEMPOTENCY_KEY_REUSED`.

Ejemplo de respuesta:

```json
{
  "id": 42,
  "clienteId": "cognito-sub-del-cliente",
  "fechaCreacion": "2026-08-28T20:00:00Z",
  "estado": "CONFIRMADA",
  "subtotal": 49980.00,
  "total": 49980.00,
  "motivoCancelacion": null,
  "items": [
    {
      "id": 81,
      "productoId": 10,
      "sku": "SEM-001",
      "nombreProducto": "Semilla de Maiz",
      "cantidad": 2,
      "precioUnitario": 24990.00,
      "subtotal": 49980.00
    }
  ],
  "createdAt": "2026-08-28T20:00:00Z",
  "updatedAt": "2026-08-28T20:00:01Z"
}
```

## Integracion con ms-inventario

`InventoryClient` concentra toda la comunicacion y usa contratos existentes:

```text
GET  /api/inventario/productos/{id}  -> snapshot confiable de SKU, nombre y precio
POST /api/inventario/stock/validar   -> disponibilidad solicitada
POST /api/inventario/stock/salida    -> descuento idempotente con referencia VENTA-<id>
POST /api/inventario/stock/entrada   -> compensacion con referencia COMPENSACION-VENTA-<id>
```

No existe acceso a `db_inventario`. Se propagan `Authorization` y
`X-Correlation-ID`. Los timeouts son configurables y no hay reintentos automaticos
sobre descuentos.

## Consistencia e idempotencia

El flujo no intenta convertir una transaccion PostgreSQL local en una transaccion
distribuida:

```text
validar todos los items
  -> guardar venta PENDIENTE
  -> descontar cada item con referencia idempotente VENTA-<id>
  -> CONFIRMADA
```

Si un descuento falla, la venta pasa a `CANCELADA`. Los items ya descontados se
reponen en orden inverso con una referencia de compensacion idempotente. Para que
esa compensacion funcione, el token usado contra inventario debe poseer permiso de
entrada (`ROLE_ADMIN` o `SCOPE_inventario.stock.write` en el contrato actual).

Limitacion conocida para la integracion final: el endpoint de entrada actual de
`ms-inventario` clasifica toda entrada con origen `MS_COMPRAS`. La referencia
`COMPENSACION-VENTA-*` conserva trazabilidad, pero al integrar todos los proyectos
conviene agregar en inventario un contrato atomico por lote o un origen explicito
de compensacion. No se modifico `ms-inventario` en esta entrega.

`Idempotency-Key` tiene una restriccion unica por cliente y se asocia a un hash
SHA-256 canonico de los items. La idempotencia de checkout y la de cada salida de
inventario son independientes y complementarias.

## Persistencia

Flyway aplica `V1__create_ventas_schema.sql`. El modelo contiene:

- `ventas`: cliente, fechas, estado, subtotal, total, clave/hash de idempotencia y
  motivo de cancelacion;
- `detalles_venta`: producto, SKU, nombre, cantidad, precio unitario y subtotal
  guardados como snapshot.

Los indices cubren `cliente_id`, `fecha_creacion`, `estado` y consultas por cliente
y fecha. Los valores monetarios usan `NUMERIC(15,2)`/`BigDecimal`.

## Errores

Las respuestas de error no incluyen stack traces ni detalles internos:

```json
{
  "timestamp": "2026-08-28T20:00:00Z",
  "status": 409,
  "error": "CONFLICT",
  "code": "INSUFFICIENT_STOCK",
  "message": "Stock insuficiente para uno o mas productos",
  "path": "/api/v1/ventas",
  "correlationId": "checkout-web-001",
  "validationErrors": {}
}
```

Se utilizan `400`, `401`, `403`, `404`, `409`, `500` y `503` segun el caso.

## Pruebas y compilacion

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean package
```

La suite cubre:

- creacion y calculo monetario;
- cantidades/IDs invalidos, pedido vacio y productos duplicados;
- stock insuficiente y fallo de inventario;
- cancelacion y compensacion de una venta parcial;
- idempotencia y restriccion unica en JPA;
- snapshots y transicion `PENDIENTE -> CONFIRMADA`;
- consultas del cliente e intento de acceso a una venta ajena;
- `401`, permisos `CLIENTE`/`ADMIN` y encabezado de correlacion;
- respuestas de `InventoryClient` para `200`, `404`, `409`, `500` y timeout.

## Integracion futura con BFF y Cognito

El BFF debera:

- consumir estos endpoints sin duplicar logica de ventas;
- propagar `Authorization`, `Idempotency-Key` y `X-Correlation-ID`;
- conservar los codigos HTTP y el JSON de error;
- mantener `ms-ventas` en una red privada.

La integracion definitiva de Cognito solo requiere configurar issuer, JWKS y
audience. No hay IDs de pools, credenciales AWS ni secretos embebidos en codigo.

## Pendientes para la integracion futura

- reemplazar los valores de ejemplo por el issuer, JWKS, audience y estrategia de
  roles definitivos de AWS Cognito;
- configurar en el BFF las URL internas, propagacion del Bearer token,
  `Idempotency-Key` y `X-Correlation-ID`;
- definir en el despliegue integrado si `ms-ventas` propagara el JWT del cliente o
  usara un token tecnico mediante `INVENTORY_SERVICE_TOKEN`;
- mejorar `ms-inventario` con una operacion atomica de stock por lote o con un
  origen explicito para movimientos de compensacion, evitando que una reposicion
  de ventas figure como `MS_COMPRAS`;
- ejecutar pruebas end-to-end cuando esten disponibles BFF, Cognito e inventario
  en la misma infraestructura y verificar los permisos de salida y compensacion;
- sustituir todas las claves y contrasenas locales de `.env.example` por secretos
  administrados en el entorno de despliegue.
