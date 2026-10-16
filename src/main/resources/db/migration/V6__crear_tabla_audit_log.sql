-- =====================================================================
-- V6 - Auditoría de operaciones relevantes (solo inserción).
-- =====================================================================
-- Responde a: quién (actor/canal), cuándo (occurred_at), qué (accion sobre
-- entity_type/entity_id), con qué resultado, y cómo trazarlo
-- (correlation_id, integration_operation_id).
-- entity_id no tiene FK porque es polimórfico (POLIZA o RIESGO).
-- "detalle" no debe contener secretos ni datos sensibles sin enmascarar.

CREATE TABLE audit_log (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY,
    occurred_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actor                     VARCHAR(100)  NOT NULL,
    canal                     VARCHAR(50),
    accion                    VARCHAR(50)   NOT NULL,
    entity_type               VARCHAR(10)   NOT NULL,
    entity_id                 BIGINT,
    resultado                 VARCHAR(20)   NOT NULL,
    correlation_id            UUID,
    integration_operation_id  UUID,
    detalle                   JSONB,
    mensaje_error             VARCHAR(2000),

    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_integration_operation FOREIGN KEY (integration_operation_id)
        REFERENCES integration_operation (id) ON DELETE RESTRICT,
    CONSTRAINT ck_audit_log_entity_type CHECK (entity_type IN ('POLIZA', 'RIESGO')),
    CONSTRAINT ck_audit_log_resultado
        CHECK (resultado IN ('EXITO', 'ACEPTADO_PENDIENTE', 'RECHAZADO', 'ERROR'))
);

CREATE INDEX idx_audit_log_entidad     ON audit_log (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_log_correlation ON audit_log (correlation_id);
CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at DESC);

CREATE TRIGGER trg_audit_log_no_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION fn_prevent_update();

CREATE TRIGGER trg_audit_log_no_delete
    BEFORE DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete();

COMMENT ON TABLE audit_log IS 'Bitácora inmutable de operaciones sobre pólizas y riesgos';
