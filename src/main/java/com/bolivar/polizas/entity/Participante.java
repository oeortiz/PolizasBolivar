package com.bolivar.polizas.entity;

import com.bolivar.polizas.entity.enums.TipoDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

/**
 * Datos de identificación de un participante (tomador, arrendatario o arrendador).
 * Los nombres de columna se redefinen con {@code @AttributeOverride} en cada uso.
 */
@Embeddable
public record Participante(
        @Enumerated(EnumType.STRING)
        @Column(name = "tipo_documento", length = 5, nullable = false)
        TipoDocumento tipoDocumento,

        @Column(name = "numero_documento", length = 20, nullable = false)
        String numeroDocumento,

        @Column(name = "nombre", length = 200, nullable = false)
        String nombre
) {
    public Participante {
        Objects.requireNonNull(tipoDocumento, "tipoDocumento es obligatorio");
        Objects.requireNonNull(numeroDocumento, "numeroDocumento es obligatorio");
        Objects.requireNonNull(nombre, "nombre es obligatorio");
    }

    /** Documento parcialmente enmascarado para logs y auditoría. */
    public String documentoEnmascarado() {
        int visibles = Math.min(3, numeroDocumento.length());
        return tipoDocumento + " " + "*".repeat(numeroDocumento.length() - visibles)
                + numeroDocumento.substring(numeroDocumento.length() - visibles);
    }

    @Override
    public String toString() {
        // Evita exponer datos personales completos en logs.
        return "Participante[" + documentoEnmascarado() + "]";
    }
}
