# API de pólizas de arrendamiento

API REST para administrar pólizas de arrendamiento **individuales y colectivas**: riesgos, prima, renovación con IPC, cancelaciones e historial. Toda mutación se sincroniza con el **CORE de seguros** a través del servicio de edición publicado en **WebLogic**.

- Java 21 · Spring Boot 3.5 · Maven · PostgreSQL 16 · Flyway
- Arquitectura por capas + hexagonal + Event-Driven con Transactional Outbox 
- Modelo de datos y decisiones por etapa 
- Supuestos, matriz de pruebas, estado de la verificación y limitaciones

> **Por defecto el CORE está SIMULADO.** Ninguna mutación llega a un CORE real hasta que se configure `CORE_ADAPTADOR=WEBLOGIC` con URL, credenciales y certificados reales. Cada operación indica qué adaptador la procesó (`adaptador: SIMULADO | WEBLOGIC`).

## Reglas principales

| Regla | Fórmula / comportamiento |
|---|---|
| Prima | `PRIMA = CANON MENSUAL × MESES DE VIGENCIA` (`BigDecimal`, 2 decimales, HALF_UP) |
| Colectiva | `PRIMA CONSOLIDADA = Σ primas de los riesgos ACTIVOS` |
| Individual | Exactamente un riesgo; la prima de la póliza es la de su riesgo (sin doble conteo) |
| IPC | `CANON NUEVO = CANON × (1 + IPC/100)` y luego `PRIMA NUEVA = CANON NUEVO × MESES` |
| Fuente del IPC | `system_constant`, clave `ipc_<aaaa>_<mm>` del mes de inicio de la nueva vigencia. Si falta, la renovación se rechaza (409): nunca se asume 0 |
| Historial | Cada renovación guarda los valores anteriores y nuevos, el IPC y la clave usada (inmutable) |
| Sin borrado | Las cancelaciones son cambios de estado; la base de datos bloquea los DELETE |

## Ejecución local con Docker Compose

Requisitos: Docker con Compose v2.

```bash
cp .env.example .env
# Edite .env y asigne DB_PASSWORD (obligatorio)
docker compose up --build
```

> [!WARNING]
> Recuerda cambiar IP-CONTENEDOR por la ip de tu contenedor recién creado


| URL | Contenido |
|---|---|
| http://IP-CONTENEDOR:8080/swagger-ui.html | Swagger UI |
| http://IP-CONTENEDOR:8080/v3/api-docs | OpenAPI 3 (JSON) |
| http://IP-CONTENEDOR:8080/actuator/health | Salud (incluye `integracionCore`) |
| http://IP-CONTENEDOR:8080/actuator/prometheus | Métricas |

### Cargar el IPC para renovar

Los valores de ejemplo `ipc_enero` e `ipc_febrero` no tienen año y no se usan. Cargue el IPC por periodo, sustituyendo el valor ilustrativo por el oficial:

```bash
docker compose exec postgres psql -U polizas -d polizas \
  -c "INSERT INTO system_constant (nombre, valor, descripcion) VALUES 
  ('ipc_2026_01', '5.00', 'IPC enero 2026'),
  ('ipc_2026_02', '5.10', 'IPC febrero 2026'),
  ('ipc_2026_03', '5.40', 'IPC marzo 2026'),
  ('ipc_2026_04', '5.20', 'IPC abril 2026'),
  ('ipc_2026_05', '5.40', 'IPC mayo 2026'),
  ('ipc_2026_06', '5.70', 'IPC junio 2026'),
  ('ipc_2026_07', '5.90', 'IPC julio 2026'),
  ('ipc_2026_08', '5.70', 'IPC agosto 2026'),
  ('ipc_2026_09', '5.30', 'IPC septiembre 2026'),
  ('ipc_2026_10', '5.30', 'IPC octubre 2026'),
  ('ipc_2026_11', '5.20', 'IPC noviembre 2026'),
  ('ipc_2026_12', '5.50', 'IPC diciembre 2026');"

```

## Ejecución sin Docker

```bash
# PostgreSQL 16 disponible y base "polizas" creada
export DB_URL=jdbc:postgresql://localhost:5432/polizas DB_USERNAME=polizas DB_PASSWORD=...
mvn spring-boot:run
```

Flyway crea el esquema al arrancar. Hibernate solo valida (`ddl-auto=validate`).

## Recorrido de ejemplo

```bash
API=http://localhost:8080/api/v1

# 1. Póliza individual (201). La prima se calcula: 1.500.000 × 12 = 18.000.000
curl -s -X POST $API/polizas -H 'Content-Type: application/json' -d '{
  "tipo":"INDIVIDUAL","fechaInicioVigencia":"2026-01-01","duracionMeses":12,
  "riesgos":[{"direccionInmueble":"Calle 100 # 10-20, Bogotá","canonMensual":1500000,
    "arrendatario":{"tipoDocumento":"CC","numeroDocumento":"1020304050","nombre":"Carlos Arrendatario"},
    "arrendador":{"tipoDocumento":"CC","numeroDocumento":"79888777","nombre":"Ana Arrendadora"}}]}'

# 2. Listado con filtros y paginación
curl -s "$API/polizas?tipo=INDIVIDUAL&estado=ACTIVA&page=0&size=20"

# 3. Renovación idempotente (202): con ipc_2027_01 = 5.60 → canon 1.584.000, prima 19.008.000
curl -s -X POST $API/polizas/1/renovar -H 'Idempotency-Key: renovacion-1-2027'

# 4. Estado de la sincronización con el CORE
curl -s $API/operaciones/<id-de-la-operacion>

# 5. Historial de renovaciones
curl -s $API/polizas/1/renovaciones
```

### Endpoints

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/v1/polizas?tipo=&estado=&page=&size=` | 200 |
| GET | `/api/v1/polizas/{id}/riesgos` | 200 / 404 |
| POST | `/api/v1/polizas/{id}/renovar` | 202 / 404 / 409 |
| POST | `/api/v1/polizas/{id}/cancelar` | 202 / 404 / 409 |
| POST | `/api/v1/polizas/{id}/riesgos` | 202 / 400 / 404 / 409 |
| POST | `/api/v1/riesgos/{id}/cancelar` | 202 / 404 / 409 |
| POST | `/api/v1/polizas` *(adicional)* | 201 / 400 |
| GET | `/api/v1/polizas/{id}` *(adicional)* | 200 / 404 |
| GET | `/api/v1/polizas/{id}/renovaciones` *(adicional)* | 200 / 404 |
| GET | `/api/v1/operaciones/{id}` *(adicional)* | 200 / 404 |
| POST | `/api/v1/operaciones/{id}/reintentar` *(adicional)* | 202 / 404 / 409 |

**202 Accepted** significa que el cambio local está guardado y que la sincronización con el CORE sigue pendiente. La cabecera `Location` apunta a `/api/v1/operaciones/{id}`. Solo cuando la operación está `CONFIRMADA` el cambio está aplicado en el CORE.

Los errores siguen Problem Details (RFC 9457), con `codigo` (por ejemplo `POLIZA_CANCELADA`, `IPC_NO_DISPONIBLE`, `RENOVACION_EN_CURSO`) y `correlationId`.

## Integración con el CORE

| Aspecto | Comportamiento |
|---|---|
| Contrato | `POST` con `{header:{correlationId,timestamp,channel,user}, mutation:{entityType,entityId,action,reasonCode,effectiveDate,payload}}`; acciones **ANULAR, SUSPENDER, ACTIVAR, MODIFICAR** |
| Mapeo (propuesta) | Cancelar póliza/riesgo → ANULAR · Renovar, modificar y agregar riesgo → MODIFICAR |
| Seguridad | HTTPS TLS 1.2/1.3, **mTLS** (keystore + truststore) y **OAuth 2.0** client credentials (token en caché) |
| Entrega | Outbox en PostgreSQL + evento tras el commit + sondeo cada 5 s; la API nunca espera al CORE |
| Reintentos | 1 intento + 3 reintentos, uno por minuto; misma `Idempotency-Key` y `correlationId` en todos |
| Recuperación | Las operaciones pendientes y las interrumpidas por una caída se retoman al arrancar |
| Revisión | Una operación `FALLIDA` se reabre con `POST /operaciones/{id}/reintentar` |

Para conectar el CORE real, configure en `.env` (o, mejor, en un gestor de secretos):

```bash
CORE_ADAPTADOR=WEBLOGIC
WEBLOGIC_MUTACIONES_URL=https://...
WEBLOGIC_TOKEN_URL=https://...
WEBLOGIC_CLIENT_ID=...    WEBLOGIC_CLIENT_SECRET=...    WEBLOGIC_SCOPE=...
WEBLOGIC_KEYSTORE_PATH=/certs/cliente.p12      WEBLOGIC_KEYSTORE_PASSWORD=...
WEBLOGIC_TRUSTSTORE_PATH=/certs/confianza.p12  WEBLOGIC_TRUSTSTORE_PASSWORD=...
```

y monte los certificados como volumen de solo lectura (ver el comentario en `docker-compose.yml`). 

### Simulador

`CORE_SIMULADO_MODO` admite `EXITO`, `ERROR_HTTP` (con `CORE_SIMULADO_HTTP_STATUS`), `TIMEOUT` y `FALLA_Y_RECUPERA` (con `CORE_SIMULADO_FALLOS`). Por ejemplo, para ver los reintentos con un CORE caído que se recupera al tercer intento, ponga esto en `.env` y reinicie con `docker compose up -d api`:

```bash
CORE_SIMULADO_MODO=FALLA_Y_RECUPERA
CORE_SIMULADO_FALLOS=2
```

Consulte `/api/v1/operaciones/{id}`: verá `intentos` 1 → 2 → 3, un minuto entre cada uno, hasta `CONFIRMADA` con `adaptador: SIMULADO`.

## Pruebas

```bash
mvn verify
```

- Las pruebas de integración usan **Testcontainers** (PostgreSQL 16) y **requieren Docker**.
- Para usar un PostgreSQL existente y vacío: `POLIZAS_TEST_DB_URL`, `POLIZAS_TEST_DB_USERNAME` y `POLIZAS_TEST_DB_PASSWORD`.



## Observabilidad

| Señal | Dónde |
|---|---|
| Logs | Perfil `json` → formato ECS con `correlationId`; sin perfil → texto con `[corr=…]` |
| CorrelationId | Cabecera `X-Correlation-Id` (entrada y salida), MDC, outbox, cabecera hacia el CORE y auditoría |
| Métricas HTTP | `http_server_requests_seconds` (latencia y errores por endpoint) |
| Métricas del CORE | `polizas_integracion_envio_seconds` (latencia por intento y resultado), `polizas_integracion_desenlaces_total`, `polizas_integracion_operaciones{estado}` (pendientes y fallidas) |
| Salud | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` |
| Auditoría | Tabla `audit_log` (inmutable): quién, cuándo, qué y resultado |

## Estructura

```
src/main/java/com/bolivar/polizas/
├── controller/      REST (/api/v1)
├── service/         casos de uso (pólizas, riesgos, renovación, IPC, auditoría)
├── domain/          reglas puras: prima, IPC, vigencia, política monetaria
├── entity/          JPA + agregado Poliza/Riesgo + enumeraciones
├── repository/      Spring Data JPA
├── dto/             solicitudes y respuestas
├── integration/     contrato, puerto, adaptadores WebLogic/simulado, outbox y procesador
├── observability/   métricas y health check de la integración
├── config/          propiedades, OpenAPI, integración, procesamiento asíncrono, correlationId
└── exception/       errores de negocio y GlobalExceptionHandler
src/main/resources/db/migration/   V1–V8 (Flyway)
```

## Seguridad

- No hay secretos en el repositorio: `.env`, `*.p12`, `*.jks`, `*.pem` y `*.key` están excluidos en `.gitignore` y `.dockerignore`.
- La imagen se ejecuta con un usuario sin privilegios.
- Actuator expone solo `health`, `info`, `metrics` y `prometheus`.

