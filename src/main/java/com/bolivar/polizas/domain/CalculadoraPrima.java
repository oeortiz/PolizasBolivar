package com.bolivar.polizas.domain;

import com.bolivar.polizas.exception.ValorInvalidoException;

import java.math.BigDecimal;
import java.util.Collection;

import static com.bolivar.polizas.domain.PoliticaMonetaria.CANON_MAXIMO;
import static com.bolivar.polizas.domain.PoliticaMonetaria.CERO;
import static com.bolivar.polizas.domain.PoliticaMonetaria.CIEN;
import static com.bolivar.polizas.domain.PoliticaMonetaria.ESCALA_MONTO;
import static com.bolivar.polizas.domain.PoliticaMonetaria.ESCALA_PORCENTAJE;
import static com.bolivar.polizas.domain.PoliticaMonetaria.PRIMA_MAXIMA;
import static com.bolivar.polizas.domain.PoliticaMonetaria.REDONDEO;

/**
 * Reglas de cálculo del ejercicio, sin estado y sin dependencias de framework.
 *
 * <pre>
 * PRIMA              = CANON MENSUAL × NÚMERO DE MESES DE VIGENCIA
 * PRIMA CONSOLIDADA  = Σ PRIMAS DE LOS RIESGOS ACTIVOS
 * CANON NUEVO        = CANON ANTERIOR × (1 + IPC / 100)
 * </pre>
 *
 * No incluye tarifas, factores de riesgo ni porcentajes de prima: el enunciado
 * los excluye expresamente.
 */
public final class CalculadoraPrima {

    /** Límite técnico de {@code NUMERIC(7,4)}. */
    private static final BigDecimal IPC_MAXIMO = new BigDecimal("999.9999");
    private static final BigDecimal IPC_MINIMO_EXCLUSIVO = CIEN.negate();

    /**
     * Prima = canon × meses. Con canon de 2 decimales y meses enteros el producto es
     * exacto; el redondeo solo normaliza la escala.
     */
    public BigDecimal calcularPrima(BigDecimal canonMensual, int meses) {
        BigDecimal canon = validarCanon(canonMensual);
        if (meses <= 0) {
            throw new ValorInvalidoException("El número de meses de vigencia debe ser mayor que cero");
        }
        BigDecimal prima = PoliticaMonetaria.redondear(canon.multiply(BigDecimal.valueOf(meses)));
        return verificarPrima(prima);
    }

    /**
     * Canon nuevo = canon × (1 + IPC/100), calculado como canon × (100 + IPC) / 100 para
     * redondear una sola vez. Ejemplo: 1.500.000,00 con IPC 5,60 → 1.584.000,00.
     *
     * @param ipcPorcentaje porcentaje, p. ej. {@code 5.60} para 5,60 %. Puede ser 0 o negativo
     *                      si así está configurado, pero nunca se asume cuando falta.
     */
    public BigDecimal aplicarIpc(BigDecimal canonAnterior, BigDecimal ipcPorcentaje) {
        BigDecimal canon = validarCanon(canonAnterior);
        BigDecimal ipc = validarIpc(ipcPorcentaje);

        BigDecimal canonNuevo = canon.multiply(CIEN.add(ipc)).divide(CIEN, ESCALA_MONTO, REDONDEO);

        if (canonNuevo.signum() <= 0) {
            throw new ValorInvalidoException("El IPC aplicado deja el canon en cero o negativo");
        }
        if (canonNuevo.compareTo(CANON_MAXIMO) > 0) {
            throw new ValorInvalidoException("El canon resultante supera el máximo admitido");
        }
        return canonNuevo;
    }

    /** Suma de primas (ya filtradas como activas por el llamador). Colección vacía → 0,00. */
    public BigDecimal consolidar(Collection<BigDecimal> primas) {
        if (primas == null) {
            throw new ValorInvalidoException("La colección de primas es obligatoria");
        }
        BigDecimal total = CERO;
        for (BigDecimal prima : primas) {
            if (prima == null) {
                throw new ValorInvalidoException("No se puede consolidar una prima nula");
            }
            total = total.add(prima);
        }
        return verificarPrima(PoliticaMonetaria.redondear(total));
    }

    /** Valida un canon de entrada: obligatorio, positivo, máximo 2 decimales y dentro de rango. */
    public BigDecimal validarCanon(BigDecimal canonMensual) {
        BigDecimal canon = PoliticaMonetaria.normalizarMontoEntrada(canonMensual, "El canon mensual");
        if (canon.signum() <= 0) {
            throw new ValorInvalidoException("El canon mensual debe ser mayor que cero");
        }
        if (canon.compareTo(CANON_MAXIMO) > 0) {
            throw new ValorInvalidoException("El canon mensual supera el máximo admitido");
        }
        return canon;
    }

    /** Valida un porcentaje de IPC: obligatorio, mayor que -100 y con máximo 4 decimales. */
    public BigDecimal validarIpc(BigDecimal ipcPorcentaje) {
        if (ipcPorcentaje == null) {
            throw new ValorInvalidoException("El IPC es obligatorio; no se asume un valor por defecto");
        }
        if (ipcPorcentaje.stripTrailingZeros().scale() > ESCALA_PORCENTAJE) {
            throw new ValorInvalidoException("El IPC admite máximo " + ESCALA_PORCENTAJE + " decimales");
        }
        if (ipcPorcentaje.compareTo(IPC_MINIMO_EXCLUSIVO) <= 0 || ipcPorcentaje.compareTo(IPC_MAXIMO) > 0) {
            throw new ValorInvalidoException("El IPC debe ser mayor que -100 y menor o igual que 999.9999");
        }
        return ipcPorcentaje;
    }

    private static BigDecimal verificarPrima(BigDecimal prima) {
        if (prima.compareTo(PRIMA_MAXIMA) > 0) {
            throw new ValorInvalidoException("La prima resultante supera el máximo admitido");
        }
        return prima;
    }
}
