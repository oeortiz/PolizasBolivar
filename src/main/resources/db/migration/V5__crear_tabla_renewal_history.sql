-- =====================================================================
-- V5 - Historial de renovaciones (fotografía inmutable).
-- =====================================================================

-- Función genérica para tablas de solo inserción (historial, auditoría).
CREATE OR REPLACE FUNCTION fn_prevent_update()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Los registros de la tabla % son inmutables', TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TABLE renewal_history (
    id                              BIGINT GENERATED ALWAYS AS IDENTITY,
    poliza_id                       BIGINT        NOT NULL,
    integration_operation_id        UUID,
    -- Misma clave idempotente de la solicitud: un reintento no crea otro historial.
    idempotency_key                 VARCHAR(100)  NOT NULL,

    canon_anterior                  NUMERIC(15,2) NOT NULL,
    canon_nuevo                     NUMERIC(15,2) NOT NULL,
    prima_anterior                  NUMERIC(17,2) NOT NULL,
    prima_nueva                     NUMERIC(17,2) NOT NULL,

    -- Porcentaje aplicado (5.6000 = 5,60 %) y constante de la que se obtuvo.
    ipc_aplicado                    NUMERIC(7,4)  NOT NULL,
    ipc_constante_nombre            VARCHAR(100)  NOT NULL,

    duracion_meses                  INTEGER       NOT NULL,
    fecha_inicio_vigencia_anterior  DATE          NOT NULL,
    fecha_fin_vigencia_anterior     DATE          NOT NULL,
    fecha_inicio_vigencia_nueva     DATE          NOT NULL,
    fecha_fin_vigencia_nueva        DATE          NOT NULL,

    estado_anterior                 VARCHAR(20)   NOT NULL,
    estado_nuevo                    VARCHAR(20)   NOT NULL,

    -- Fotografía por riesgo: [{riesgoId, canonAnterior, canonNuevo, primaAnterior,
    -- primaNueva}]. En una individual contiene su único riesgo; en una colectiva,
    -- los riesgos activos renovados.
    detalle_riesgos                 JSONB         NOT NULL,

    fecha_renovacion                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    usuario                         VARCHAR(100)  NOT NULL,

    CONSTRAINT pk_renewal_history PRIMARY KEY (id),
    CONSTRAINT fk_renewal_history_poliza FOREIGN KEY (poliza_id)
        REFERENCES poliza (id) ON DELETE RESTRICT,
    CONSTRAINT fk_renewal_history_integration_operation FOREIGN KEY (integration_operation_id)
        REFERENCES integration_operation (id) ON DELETE RESTRICT,
    CONSTRAINT uk_renewal_history_idempotency_key UNIQUE (idempotency_key),
    -- Una póliza no puede renovarse dos veces hacia el mismo periodo.
    CONSTRAINT uk_renewal_history_poliza_periodo UNIQUE (poliza_id, fecha_inicio_vigencia_nueva),
    CONSTRAINT ck_renewal_history_canon_valores   CHECK (canon_anterior >= 0 AND canon_nuevo >= 0),
    CONSTRAINT ck_renewal_history_prima_valores   CHECK (prima_anterior >= 0 AND prima_nueva >= 0),
    -- IPC > -100 % garantiza un canon resultante no negativo.
    CONSTRAINT ck_renewal_history_ipc             CHECK (ipc_aplicado > -100),
    CONSTRAINT ck_renewal_history_duracion        CHECK (duracion_meses > 0),
    CONSTRAINT ck_renewal_history_vigencia_ant    CHECK (fecha_fin_vigencia_anterior > fecha_inicio_vigencia_anterior),
    CONSTRAINT ck_renewal_history_vigencia_nueva  CHECK (fecha_fin_vigencia_nueva > fecha_inicio_vigencia_nueva),
    -- Una póliza CANCELADA no se puede renovar: tampoco puede aparecer en el historial.
    CONSTRAINT ck_renewal_history_estado_anterior CHECK (estado_anterior IN ('ACTIVA', 'RENOVADA')),
    CONSTRAINT ck_renewal_history_estado_nuevo    CHECK (estado_nuevo    IN ('ACTIVA', 'RENOVADA')),
    CONSTRAINT ck_renewal_history_detalle_array   CHECK (jsonb_typeof(detalle_riesgos) = 'array')
);

CREATE INDEX idx_renewal_history_poliza ON renewal_history (poliza_id, fecha_renovacion DESC);

CREATE TRIGGER trg_renewal_history_no_update
    BEFORE UPDATE ON renewal_history
    FOR EACH ROW EXECUTE FUNCTION fn_prevent_update();

CREATE TRIGGER trg_renewal_history_no_delete
    BEFORE DELETE ON renewal_history
    FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete();

COMMENT ON TABLE renewal_history IS 'Fotografía inmutable de los valores antes y después de cada renovación';
