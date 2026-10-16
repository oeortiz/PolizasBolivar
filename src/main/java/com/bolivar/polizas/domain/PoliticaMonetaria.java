package com.bolivar.polizas.domain;

import com.bolivar.polizas.exception.ValorInvalidoException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Política única de precisión y redondeo para valores monetarios (supuesto S-01).
 *
 * <ul>
 *   <li>Montos: escala 2 (pesos con centavos), redondeo {@link RoundingMode#HALF_UP}.</li>
 *   <li>Porcentajes de IPC: hasta 4 decimales (columna {@code NUMERIC(7,4)}).</li>
 *   <li>Se redondea una sola vez por resultado, al final de cada cálculo; nunca en
 *       pasos intermedios.</li>
 *   <li>Los montos de entrada con más de 2 decimales significativos se rechazan:
 *       no se redondean silenciosamente datos que envía el usuario.</li>
 * </ul>
 *
 * Los límites máximos reflejan las columnas de la base de datos para fallar con un
 * error de negocio claro en lugar de un error SQL.
 */
public final class PoliticaMonetaria {

    public static final int ESCALA_MONTO = 2;
    public static final int ESCALA_PORCENTAJE = 4;
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    /** Máximo de {@code NUMERIC(15,2)}: canon mensual. */
    public static final BigDecimal CANON_MAXIMO = new BigDecimal("9999999999999.99");
    /** Máximo de {@code NUMERIC(17,2)}: primas individuales y consolidadas. */
    public static final BigDecimal PRIMA_MAXIMA = new BigDecimal("999999999999999.99");

    public static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA_MONTO);
    static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private PoliticaMonetaria() {
    }

    /** Redondea un resultado de cálculo a la escala monetaria. */
    public static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(ESCALA_MONTO, REDONDEO);
    }

    /**
     * Valida un monto de entrada y lo normaliza a escala 2 sin redondear.
     * Acepta {@code 1500000}, {@code 1500000.5} o {@code 1500000.500}; rechaza {@code 1500000.505}.
     */
    static BigDecimal normalizarMontoEntrada(BigDecimal valor, String campo) {
        if (valor == null) {
            throw new ValorInvalidoException(campo + " es obligatorio");
        }
        if (valor.stripTrailingZeros().scale() > ESCALA_MONTO) {
            throw new ValorInvalidoException(campo + " admite máximo " + ESCALA_MONTO + " decimales");
        }
        return valor.setScale(ESCALA_MONTO, RoundingMode.UNNECESSARY);
    }
}
