-- =====================================================================
-- V1 - Funciones comunes reutilizadas por los triggers del esquema.
-- =====================================================================

-- Mantiene updated_at en la base de datos como segunda línea de defensa
-- (la aplicación también lo asigna desde las entidades JPA).
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Impide el borrado físico de tablas que deben conservar historial
-- (requisito: "No permitir eliminar físicamente registros que deban
-- conservar historial"). Las cancelaciones se modelan como cambios de estado.
CREATE OR REPLACE FUNCTION fn_prevent_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'No se permite eliminar físicamente registros de la tabla %', TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;
