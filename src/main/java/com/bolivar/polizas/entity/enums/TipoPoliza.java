package com.bolivar.polizas.entity.enums;

/**
 * Tipo de póliza de arrendamiento.
 */
public enum TipoPoliza {
    /** Exactamente un riesgo; tomador y asegurado = arrendatario; beneficiario = arrendador. */
    INDIVIDUAL,
    /** Inmobiliarias y copropiedades; uno o varios riesgos; prima consolidada. */
    COLECTIVA;
}
