package com.bolivar.polizas.service.ipc;

import com.bolivar.polizas.domain.PeriodoIpc;

import java.math.BigDecimal;

/**
 * IPC resuelto para una renovación.
 *
 * @param porcentaje          valor validado (5.60 = 5,60 %)
 * @param constanteNombre     clave de {@code system_constant} usada; se guarda en el historial
 * @param periodo             periodo determinado por la regla configurada
 * @param claveMensualSinAnio {@code true} si se usó el respaldo {@code ipc_<mes>} (no distingue año)
 */
public record IpcAplicable(BigDecimal porcentaje, String constanteNombre, PeriodoIpc periodo,
                           boolean claveMensualSinAnio) {
}
