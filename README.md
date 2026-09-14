# AgroCenter Digital - ms-ventas

Microservicio responsable de la gestión de pedidos, checkout, persistencia de transacciones e historial de ventas en **AgroCenter Digital**. Sigue el patrón **Database-per-Service** accediendo de forma exclusiva a `db_ventas`.

---

## 1. Arquitectura y Enrutamiento en AWS

En el entorno productivo de AWS ECS Fargate:
* **Puerto**: `8082`
* **Target Group**: `tg-ms-ventas` (Healthy)
* **Router ALB Interno**: `internal-agrocenter-bff-alb:8080`
* **Path-Based Routing**: Rutas `/api/ventas/*` direccionadas a `tg-ms-ventas`.
* **Comunicación con Inventario**: Se comunica mediante REST con `ms-inventario` para validar existencias y ejecutar salidas de stock con clave de idempotencia (`VENTA-<id>`).

```text
Frontend (Vercel) -> bff-web (8080)
                         |
                         v (vía ALB interno: /api/ventas/*)
                    ms-ventas (Puerto 8082)
                         |---> db_ventas (PostgreSQL)
                         `---> ms-inventario (Puerto 8081 / salida de stock)
```

---

## 2. Seguridad y Control de Acceso

Configurado como **OAuth2 Resource Server** con AWS Cognito:

* **Roles y Permisos**:
  - `POST /api/v1/ventas`: Requiere rol `ROLE_CLIENTE` (o `ROLE_ADMIN`).
  - `GET /api/v1/ventas/mis-pedidos`: Requiere `ROLE_CLIENTE` y lista únicamente los pedidos asociados al `sub` del usuario autenticado (prevención de IDOR).
  - `GET /api/v1/ventas`: Requiere `ROLE_ADMIN` para reportería global.
  - `GET /api/v1/ventas/{id}`: Acceso restringido al propietario del pedido o a `ROLE_ADMIN`.
* **Validación de Tokens de Cognito**:
  - `CognitoTokenUseValidator`: Valida `token_use: "access"`.
  - `CognitoAudienceValidator`: Acepta tanto `aud` como `client_id`.
  - `CognitoAuthoritiesConverter`: Filtra grupos técnicos (como `us-east-1_..._Google`) y mapea limpiamente a `ROLE_CLIENTE` y `ROLE_ADMIN`.
* **Identificación Confiable**: El identificador del comprador se extrae directamente del claim criptográfico `sub`, impidiendo la suplantación de identidad en peticiones de venta.

---

## 3. Endpoints Principales

| Método | Ruta | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/ventas` | `ROLE_CLIENTE` | Registra una venta, descuenta stock e inserta detalles |
| `GET` | `/api/v1/ventas/mis-pedidos`| `ROLE_CLIENTE` | Consulta pedidos del cliente autenticado |
| `GET` | `/api/v1/ventas/{id}` | Propietario / `ROLE_ADMIN` | Consulta detalle de venta |
| `GET` | `/api/v1/ventas` | `ROLE_ADMIN` | Listado paginado de todas las ventas |
| `GET` | `/actuator/health` | **Público** | Health check para ALB y ECS |

---

## 4. Despliegue CI/CD

El repositorio contiene el flujo automatizado [`.github/workflows/deploy.yml`](file:///.github/workflows/deploy.yml):
* **Disparador**: Cada `git push` a `main`.
* **Acción**: Construcción de imagen Docker multi-etapa con Java 21 y publicación en Docker Hub:
  ```text
  tag: <DOCKERHUB_USERNAME>/agrocenter-ms-ventas:latest
  ```
* **Actualización en ECS**: Tarea de Fargate asociada al Target Group `tg-ms-ventas` que se recarga automáticamente.

---

## 5. Pruebas y Validación Local

```bash
# Ejecutar suite de pruebas unitarias y de integración
./mvnw clean test
```
* Cubre reglas de negocio, cálculo preciso con `BigDecimal`, descuentos de stock, validación de propietarios (anti-IDOR) y pruebas de seguridad MockMvc con perfiles `test`.
