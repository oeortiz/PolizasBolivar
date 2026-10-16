package com.bolivar.polizas.integration.weblogic;

import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.integration.port.ResultadoEnvio;

/**
 * Clasifica la respuesta HTTP del servicio de mutaciones. El contrato de respuesta real no
 * está disponible, por lo que la clasificación usa solo el código HTTP (pendiente de
 * validar con el responsable del CORE, supuesto S-24).
 */
public final class ClasificadorRespuestaCore {

    private static final AdaptadorIntegracion ADAPTADOR = AdaptadorIntegracion.WEBLOGIC;

    private ClasificadorRespuestaCore() {
    }

    public static ResultadoEnvio clasificar(int status, String cuerpo) {
        if (status == 202) {
            return new ResultadoEnvio.Incierto(status,
                    "El CORE aceptó la mutación sin confirmar su aplicación (HTTP 202)", ADAPTADOR);
        }
        if (status >= 200 && status < 300) {
            return new ResultadoEnvio.Confirmado(status, cuerpo, ADAPTADOR);
        }
        return switch (status) {
            // No procesada: se puede reintentar sin riesgo de duplicar.
            case 401 -> new ResultadoEnvio.FalloTransitorio(status, "Token rechazado por el CORE (HTTP 401)", ADAPTADOR);
            case 408 -> new ResultadoEnvio.FalloTransitorio(status, "El CORE no recibió la solicitud a tiempo (HTTP 408)", ADAPTADOR);
            case 429 -> new ResultadoEnvio.FalloTransitorio(status, "El CORE limitó la tasa de solicitudes (HTTP 429)", ADAPTADOR);
            case 503 -> new ResultadoEnvio.FalloTransitorio(status, "CORE no disponible (HTTP 503)", ADAPTADOR);
            // Pudo haberse procesado: resultado desconocido.
            case 500, 502, 504 -> new ResultadoEnvio.Incierto(status,
                    "Error del CORE o del gateway con resultado desconocido (HTTP " + status + ")", ADAPTADOR);
            default -> status >= 400 && status < 500
                    ? new ResultadoEnvio.Rechazado(status, cuerpo, ADAPTADOR)
                    : new ResultadoEnvio.Incierto(status, "Respuesta no esperada del CORE (HTTP " + status + ")", ADAPTADOR);
        };
    }
}
