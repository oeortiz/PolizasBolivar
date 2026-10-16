-- =====================================================================
-- V8 - Convención de claves de IPC por periodo (sin cambio de esquema).
-- =====================================================================
-- Formato preferido: ipc_<aaaa>_<mm> (p. ej. ipc_2026_01), que distingue años
-- y conserva el histórico sin sobrescribir valores.
-- Formato heredado:  ipc_<mes> (p. ej. ipc_enero). Solo se usa como respaldo si
-- polizas.ipc.permitir-clave-mensual-sin-anio = true.
-- Ambos cumplen ck_system_constant_nombre_formato (^[a-z][a-z0-9_]*$).
--
-- Ejemplo de carga (valores ilustrativos, no oficiales):
--   INSERT INTO system_constant (nombre, valor, descripcion)
--   VALUES ('ipc_2027_01', '5.20', 'IPC (%) aplicable a enero de 2027');

COMMENT ON COLUMN system_constant.nombre IS
    'Clave única normalizada. IPC: ipc_<aaaa>_<mm> (preferida, p. ej. ipc_2026_01) o ipc_<mes> (heredada, sin año)';
