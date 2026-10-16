package com.bolivar.polizas.dto.response;

import com.bolivar.polizas.entity.Participante;
import com.bolivar.polizas.entity.enums.TipoDocumento;

public record ParticipanteResponse(TipoDocumento tipoDocumento, String numeroDocumento, String nombre) {

    public static ParticipanteResponse de(Participante p) {
        return p == null ? null : new ParticipanteResponse(p.tipoDocumento(), p.numeroDocumento(), p.nombre());
    }
}
