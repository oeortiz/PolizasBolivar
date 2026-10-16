-- =====================================================================
-- V2 - Tabla system_constant (configuración, p. ej. IPC mensual).
-- =====================================================================
-- "valor" se almacena como texto porque la tabla es genérica; la aplicación
-- valida que el valor sea numérico antes de usarlo como porcentaje de IPC
-- y falla de forma controlada si falta o es inválido (nunca asume IPC 0).
--
-- Limitación conocida: claves como ipc_enero no distinguen año.
-- Extensión compatible propuesta (sin cambiar el esquema): claves con
-- periodo, p. ej. ipc_2026_01. Ver docs/01-modelo-datos.md.

CREATE TABLE system_constant (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    nombre      VARCHAR(100)  NOT NULL,
    valor       VARCHAR(255)  NOT NULL,
    descripcion VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version     BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_system_constant PRIMARY KEY (id),
    -- Evita constantes duplicadas (p. ej. dos registros ipc_enero).
    CONSTRAINT uk_system_constant_nombre UNIQUE (nombre),
    -- Claves normalizadas: minúsculas, dígitos y guion bajo.
    CONSTRAINT ck_system_constant_nombre_formato CHECK (nombre ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_system_constant_valor_no_vacio CHECK (length(trim(valor)) > 0)
);

CREATE TRIGGER trg_system_constant_updated_at
    BEFORE UPDATE ON system_constant
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

COMMENT ON TABLE  system_constant        IS 'Constantes de configuración del sistema (IPC mensual, etc.)';
COMMENT ON COLUMN system_constant.nombre IS 'Clave única normalizada, p. ej. ipc_enero';
COMMENT ON COLUMN system_constant.valor  IS 'Valor en texto; para IPC, porcentaje decimal con punto (5.60 = 5,60 %)';
