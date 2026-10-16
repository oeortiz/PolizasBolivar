package com.bolivar.polizas.integration.mapping;

import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import org.springframework.stereotype.Component;

/**
 * Único punto que traduce operaciones de negocio al contrato remoto de WebLogic, que
 * solo admite ANULAR, SUSPENDER, ACTIVAR y MODIFICAR (no existen RENOVAR ni CANCELAR).
 *
 * <p><b>Propuesta inicial sujeta a confirmación con el responsable del CORE</b>,
 * especialmente para renovaciones y cancelaciones de riesgos (supuesto S-09):</p>
 * <ul>
 *   <li>Cancelar póliza → ANULAR sobre POLIZA.</li>
 *   <li>Cancelar riesgo → ANULAR sobre RIESGO.</li>
 *   <li>Renovar póliza → MODIFICAR sobre POLIZA.</li>
 *   <li>Modificar póliza / riesgo → MODIFICAR sobre la entidad.</li>
 *   <li>Agregar riesgo → MODIFICAR sobre POLIZA (no viene en el enunciado).</li>
 * </ul>
 * ACTIVAR y SUSPENDER quedan disponibles para capacidades futuras. El {@code switch} es
 * exhaustivo: una operación nueva sin mapeo no compila.
 */
@Component
public class MapeadorOperacionCore {

    public DestinoRemoto mapear(OperacionNegocio operacion) {
        return switch (operacion) {
            case CANCELAR_POLIZA -> new DestinoRemoto(TipoEntidad.POLIZA, AccionRemota.ANULAR);
            case CANCELAR_RIESGO -> new DestinoRemoto(TipoEntidad.RIESGO, AccionRemota.ANULAR);
            case RENOVAR_POLIZA, MODIFICAR_POLIZA, AGREGAR_RIESGO ->
                    new DestinoRemoto(TipoEntidad.POLIZA, AccionRemota.MODIFICAR);
            case MODIFICAR_RIESGO -> new DestinoRemoto(TipoEntidad.RIESGO, AccionRemota.MODIFICAR);
        };
    }
}
