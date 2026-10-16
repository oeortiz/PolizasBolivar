package com.bolivar.polizas.integration.mapping;

import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.TipoEntidad;

/** Entidad y acción con las que una operación de negocio se envía al CORE. */
public record DestinoRemoto(TipoEntidad entityType, AccionRemota accion) {
}
