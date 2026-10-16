-- =====================================================================
-- V3 - Pólizas y riesgos.
-- =====================================================================
-- Política monetaria: NUMERIC con escala 2 (pesos con centavos).
--   canon_mensual NUMERIC(15,2) -> hasta 9.999.999.999.999,99
--   prima         NUMERIC(17,2) -> margen para canon x meses y consolidación
-- El redondeo (HALF_UP a 2 decimales) se aplica en el componente de cálculo
-- (Etapa 2); la base solo garantiza escala y rangos válidos.
--
-- Estado contractual (estado) y estado técnico de integración con el CORE
-- (estado_sincronizacion) son columnas separadas, tal como exige el requisito.

-- ---------------------------------------------------------------------
-- POLIZA
-- ---------------------------------------------------------------------
CREATE TABLE poliza (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY,
    tipo                      VARCHAR(20)   NOT NULL,
    estado                    VARCHAR(20)   NOT NULL,
    estado_sincronizacion     VARCHAR(20)   NOT NULL DEFAULT 'NO_REQUERIDA',

    -- INDIVIDUAL: canon del único riesgo.
    -- COLECTIVA : canon consolidado de los riesgos activos (supuesto S-03).
    canon_mensual             NUMERIC(15,2) NOT NULL,
    -- INDIVIDUAL: canon x meses. COLECTIVA: suma de primas de riesgos activos.
    prima                     NUMERIC(17,2) NOT NULL,

    fecha_inicio_vigencia     DATE          NOT NULL,
    fecha_fin_vigencia        DATE          NOT NULL,
    duracion_meses            INTEGER       NOT NULL,

    -- Tomador. INDIVIDUAL: el arrendatario. COLECTIVA: inmobiliaria/copropiedad.
    tomador_tipo_documento    VARCHAR(5)    NOT NULL,
    tomador_numero_documento  VARCHAR(20)   NOT NULL,
    tomador_nombre            VARCHAR(200)  NOT NULL,

    fecha_cancelacion         TIMESTAMPTZ,
    motivo_cancelacion        VARCHAR(250),

    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                   BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_poliza PRIMARY KEY (id),
    CONSTRAINT ck_poliza_tipo
        CHECK (tipo IN ('INDIVIDUAL', 'COLECTIVA')),
    CONSTRAINT ck_poliza_estado
        CHECK (estado IN ('ACTIVA', 'RENOVADA', 'CANCELADA')),
    CONSTRAINT ck_poliza_estado_sincronizacion
        CHECK (estado_sincronizacion IN ('NO_REQUERIDA', 'PENDIENTE', 'SINCRONIZADA', 'FALLIDA')),
    CONSTRAINT ck_poliza_canon_no_negativo   CHECK (canon_mensual >= 0),
    CONSTRAINT ck_poliza_prima_no_negativa   CHECK (prima >= 0),
    CONSTRAINT ck_poliza_duracion_positiva   CHECK (duracion_meses > 0),
    CONSTRAINT ck_poliza_vigencia_coherente  CHECK (fecha_fin_vigencia > fecha_inicio_vigencia),
    CONSTRAINT ck_poliza_tomador_tipo_doc
        CHECK (tomador_tipo_documento IN ('CC', 'CE', 'NIT', 'PA', 'PPT')),
    -- Una póliza cancelada siempre tiene fecha de cancelación, y viceversa.
    CONSTRAINT ck_poliza_cancelacion_coherente
        CHECK ((estado = 'CANCELADA') = (fecha_cancelacion IS NOT NULL))
);

-- Filtros de GET /polizas (tipo y/o estado) con orden estable por id.
CREATE INDEX idx_poliza_tipo_estado ON poliza (tipo, estado, id);
CREATE INDEX idx_poliza_estado      ON poliza (estado, id);
CREATE INDEX idx_poliza_tomador     ON poliza (tomador_tipo_documento, tomador_numero_documento);
-- Pólizas con sincronización pendiente o fallida (monitoreo / métricas).
CREATE INDEX idx_poliza_sync_pendiente ON poliza (estado_sincronizacion)
    WHERE estado_sincronizacion IN ('PENDIENTE', 'FALLIDA');

CREATE TRIGGER trg_poliza_updated_at
    BEFORE UPDATE ON poliza
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_poliza_no_delete
    BEFORE DELETE ON poliza
    FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete();

COMMENT ON TABLE  poliza                        IS 'Póliza de arrendamiento individual o colectiva';
COMMENT ON COLUMN poliza.estado                 IS 'Estado contractual: ACTIVA, RENOVADA, CANCELADA';
COMMENT ON COLUMN poliza.estado_sincronizacion  IS 'Estado técnico de la última mutación frente al CORE';
COMMENT ON COLUMN poliza.duracion_meses         IS 'Duración inicial de la vigencia; se reutiliza en cada renovación';

-- ---------------------------------------------------------------------
-- RIESGO
-- ---------------------------------------------------------------------
-- No tiene vigencia propia: usa la vigencia de su póliza.
CREATE TABLE riesgo (
    id                            BIGINT GENERATED ALWAYS AS IDENTITY,
    poliza_id                     BIGINT        NOT NULL,
    estado                        VARCHAR(20)   NOT NULL,
    estado_sincronizacion         VARCHAR(20)   NOT NULL DEFAULT 'NO_REQUERIDA',

    canon_mensual                 NUMERIC(15,2) NOT NULL,
    prima                         NUMERIC(17,2) NOT NULL,

    direccion_inmueble            VARCHAR(250)  NOT NULL,

    -- Arrendatario: asegurado (y tomador en la individual).
    arrendatario_tipo_documento   VARCHAR(5)    NOT NULL,
    arrendatario_numero_documento VARCHAR(20)   NOT NULL,
    arrendatario_nombre           VARCHAR(200)  NOT NULL,
    -- Arrendador: beneficiario.
    arrendador_tipo_documento     VARCHAR(5)    NOT NULL,
    arrendador_numero_documento   VARCHAR(20)   NOT NULL,
    arrendador_nombre             VARCHAR(200)  NOT NULL,

    fecha_cancelacion             TIMESTAMPTZ,
    motivo_cancelacion            VARCHAR(250),

    created_at                    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                       BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_riesgo PRIMARY KEY (id),
    CONSTRAINT fk_riesgo_poliza FOREIGN KEY (poliza_id)
        REFERENCES poliza (id) ON DELETE RESTRICT,
    CONSTRAINT ck_riesgo_estado
        CHECK (estado IN ('ACTIVO', 'CANCELADO')),
    CONSTRAINT ck_riesgo_estado_sincronizacion
        CHECK (estado_sincronizacion IN ('NO_REQUERIDA', 'PENDIENTE', 'SINCRONIZADA', 'FALLIDA')),
    CONSTRAINT ck_riesgo_canon_positivo   CHECK (canon_mensual > 0),
    CONSTRAINT ck_riesgo_prima_positiva   CHECK (prima > 0),
    CONSTRAINT ck_riesgo_arrendatario_tipo_doc
        CHECK (arrendatario_tipo_documento IN ('CC', 'CE', 'NIT', 'PA', 'PPT')),
    CONSTRAINT ck_riesgo_arrendador_tipo_doc
        CHECK (arrendador_tipo_documento IN ('CC', 'CE', 'NIT', 'PA', 'PPT')),
    CONSTRAINT ck_riesgo_cancelacion_coherente
        CHECK ((estado = 'CANCELADO') = (fecha_cancelacion IS NOT NULL))
);

-- GET /polizas/{id}/riesgos y consolidación de primas de riesgos activos.
CREATE INDEX idx_riesgo_poliza_estado ON riesgo (poliza_id, estado);

CREATE TRIGGER trg_riesgo_updated_at
    BEFORE UPDATE ON riesgo
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_riesgo_no_delete
    BEFORE DELETE ON riesgo
    FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete();

-- Defensa en profundidad: una póliza INDIVIDUAL tiene exactamente un riesgo.
-- La regla principal vive en la capa de servicio; este trigger impide que un
-- error de aplicación o una carga manual la viole. Bloquea la fila de la
-- póliza (FOR UPDATE) para serializar inserciones concurrentes.
-- Cuenta riesgos en cualquier estado: un riesgo cancelado no habilita otro.
CREATE OR REPLACE FUNCTION fn_riesgo_individual_unico()
RETURNS TRIGGER AS $$
DECLARE
    v_tipo VARCHAR(20);
BEGIN
    SELECT tipo INTO v_tipo FROM poliza WHERE id = NEW.poliza_id FOR UPDATE;

    IF v_tipo = 'INDIVIDUAL' AND EXISTS (
        SELECT 1 FROM riesgo WHERE poliza_id = NEW.poliza_id AND id <> NEW.id
    ) THEN
        RAISE EXCEPTION 'La póliza INDIVIDUAL % ya tiene un riesgo asociado', NEW.poliza_id
            USING ERRCODE = 'unique_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_riesgo_individual_unico
    BEFORE INSERT OR UPDATE OF poliza_id ON riesgo
    FOR EACH ROW EXECUTE FUNCTION fn_riesgo_individual_unico();

COMMENT ON TABLE  riesgo                IS 'Inmueble arrendado asegurado dentro de una póliza';
COMMENT ON COLUMN riesgo.prima          IS 'canon_mensual x poliza.duracion_meses';
