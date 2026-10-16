package com.bolivar.polizas.domain;

import com.bolivar.polizas.exception.ValorInvalidoException;

import java.time.YearMonth;

/**
 * Periodo (año-mes) al que corresponde un IPC y sus claves en {@code system_constant}.
 *
 * <ul>
 *   <li>Clave con periodo (preferida): {@code ipc_<aaaa>_<mm>}, p. ej. {@code ipc_2026_01}.
 *       Distingue años y permite conservar el histórico de IPC sin sobrescribir valores.</li>
 *   <li>Clave mensual heredada: {@code ipc_<mes>}, p. ej. {@code ipc_enero}. No distingue año;
 *       solo se usa como respaldo si la configuración lo permite.</li>
 * </ul>
 * Ambos formatos cumplen el CHECK {@code ^[a-z][a-z0-9_]*$} de la tabla: no hay cambio de esquema.
 */
public record PeriodoIpc(YearMonth periodo) {

    private static final String PREFIJO = "ipc_";
    private static final String[] MESES = {
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    };

    public PeriodoIpc {
        if (periodo == null) {
            throw new ValorInvalidoException("El periodo del IPC es obligatorio");
        }
    }

    public static PeriodoIpc de(int anio, int mes) {
        return new PeriodoIpc(YearMonth.of(anio, mes));
    }

    /** {@code ipc_2026_01}. */
    public String claveConPeriodo() {
        return "%s%04d_%02d".formatted(PREFIJO, periodo.getYear(), periodo.getMonthValue());
    }

    /** {@code ipc_enero}. */
    public String claveMensualSinAnio() {
        return PREFIJO + MESES[periodo.getMonthValue() - 1];
    }

    @Override
    public String toString() {
        return periodo.toString();
    }
}
