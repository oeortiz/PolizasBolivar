-- =====================================================================
-- V4 - Operaciones de integración con el CORE (outbox transaccional).
-- =====================================================================
-- Cada mutación local (cancelar, renovar, agregar/cancelar riesgo) registra
-- aquí su intención de sincronización EN LA MISMA transacción que el cambio
-- de dominio. Un procesador asíncrono (Etapa 6) la envía a WebLogic,
-- aplica la política de reintentos y la recupera tras un reinicio.

CREATE TABLE integration_operation (
    -- Identificador único de la mutación. Se envía al CORE como identificador
    -- idempotente y se mantiene estable en todos los reintentos.
    id                   UUID          NOT NULL,
    -- Clave idempotente de la solicitud de negocio (cabecera Idempotency-Key
    -- o clave derivada). Evita registrar dos veces la misma operación.
    idempotency_key      VARCHAR(100)  NOT NULL,
    correlation_id       UUID          NOT NULL,

    entity_type          VARCHAR(10)   NOT NULL,
    entity_id            BIGINT        NOT NULL,
    operacion_negocio    VARCHAR(30)   NOT NULL,
    accion_remota        VARCHAR(10)   NOT NULL,
    reason_code          VARCHAR(50),
    effective_date       DATE          NOT NULL,
    -- Cuerpo exacto de la mutación; los reintentos reenvían este mismo payload.
    payload              JSONB         NOT NULL,

    estado               VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    intentos             INTEGER       NOT NULL DEFAULT 0,
    -- 1 intento inicial + 3 reintentos (supuesto S-08, configurable).
    max_intentos         INTEGER       NOT NULL DEFAULT 4,
    proximo_intento_at   TIMESTAMPTZ,
    ultimo_intento_at    TIMESTAMPTZ,
    confirmada_at        TIMESTAMPTZ,

    ultimo_error         VARCHAR(2000),
    ultimo_http_status   INTEGER,
    -- Respuesta conocida del CORE (referencia remota, código, etc.).
    resultado            JSONB,
    -- Adaptador que procesó el último intento. Permite distinguir una
    -- confirmación del CORE real de una del adaptador simulado.
    adaptador            VARCHAR(20),

    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_integration_operation PRIMARY KEY (id),
    CONSTRAINT uk_integration_operation_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_integration_operation_entity_type
        CHECK (entity_type IN ('POLIZA', 'RIESGO')),
    -- Contrato remoto: solo estas cuatro acciones (no RENOVAR ni CANCELAR).
    CONSTRAINT ck_integration_operation_accion_remota
        CHECK (accion_remota IN ('ANULAR', 'SUSPENDER', 'ACTIVAR', 'MODIFICAR')),
    CONSTRAINT ck_integration_operation_operacion_negocio
        CHECK (operacion_negocio IN ('CANCELAR_POLIZA', 'RENOVAR_POLIZA', 'MODIFICAR_POLIZA',
                                     'AGREGAR_RIESGO', 'CANCELAR_RIESGO', 'MODIFICAR_RIESGO')),
    CONSTRAINT ck_integration_operation_estado
        CHECK (estado IN ('PENDIENTE', 'EN_PROCESO', 'CONFIRMADA', 'RECHAZADA', 'FALLIDA')),
    CONSTRAINT ck_integration_operation_adaptador
        CHECK (adaptador IS NULL OR adaptador IN ('WEBLOGIC', 'SIMULADO')),
    CONSTRAINT ck_integration_operation_max_intentos CHECK (max_intentos >= 1),
    CONSTRAINT ck_integration_operation_intentos
        CHECK (intentos >= 0 AND intentos <= max_intentos),
    -- No se puede marcar como confirmada sin una confirmación registrada.
    CONSTRAINT ck_integration_operation_confirmacion_valida
        CHECK ((estado = 'CONFIRMADA') = (confirmada_at IS NOT NULL)
               AND (estado <> 'CONFIRMADA' OR (resultado IS NOT NULL AND adaptador IS NOT NULL)))
);

-- Selección del procesador / recuperación tras reinicio.
CREATE INDEX idx_integration_operation_pendientes
    ON integration_operation (proximo_intento_at)
    WHERE estado IN ('PENDIENTE', 'EN_PROCESO');
CREATE INDEX idx_integration_operation_entidad
    ON integration_operation (entity_type, entity_id, created_at);
CREATE INDEX idx_integration_operation_correlation
    ON integration_operation (correlation_id);

CREATE TRIGGER trg_integration_operation_updated_at
    BEFORE UPDATE ON integration_operation
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_integration_operation_no_delete
    BEFORE DELETE ON integration_operation
    FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete();

COMMENT ON TABLE  integration_operation        IS 'Outbox de mutaciones a sincronizar con el CORE vía WebLogic';
COMMENT ON COLUMN integration_operation.estado IS 'PENDIENTE, EN_PROCESO, CONFIRMADA, RECHAZADA (error de negocio del CORE), FALLIDA (reintentos agotados / revisión)';
