package com.bolivar.polizas.service;

import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.bolivar.polizas.exception.DominioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Audita un rechazo sin dejar que un fallo de auditoría oculte el error de negocio. */
final class Rechazos {

    private static final Logger log = LoggerFactory.getLogger(Rechazos.class);

    private Rechazos() {
    }

    static void auditar(AuditoriaService auditoria, String accion, TipoEntidad entidad, Long id, DominioException error) {
        try {
            auditoria.registrarRechazo(accion, entidad, id, error);
        } catch (RuntimeException e) {
            log.warn("No se pudo auditar el rechazo de {} sobre {} {}", accion, entidad, id, e);
        }
    }
}
