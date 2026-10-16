package com.bolivar.polizas.integration.port;

import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;

/**
 * Resultado de un intento de envío al CORE, clasificado para decidir qué hacer después
 * (Etapa 6). El adaptador siempre se informa para no confundir una respuesta simulada con
 * una confirmación del CORE real.
 *
 * <ul>
 *   <li>{@link Confirmado}: el CORE confirmó la mutación (2xx salvo 202). Única vía para
 *       marcar la operación como sincronizada.</li>
 *   <li>{@link Rechazado}: error funcional definitivo (4xx salvo 401, 408 y 429); no se reintenta.</li>
 *   <li>{@link FalloTransitorio}: la solicitud no llegó a procesarse (sin conexión, 503, 429,
 *       401 o token no disponible); se puede reintentar sin riesgo de duplicar.</li>
 *   <li>{@link Incierto}: la solicitud pudo procesarse sin que lo sepamos (timeout de lectura,
 *       500, 502, 504 o 202). Reintentar puede duplicar si el CORE no respeta la clave idempotente.</li>
 * </ul>
 */
public sealed interface ResultadoEnvio {

    AdaptadorIntegracion adaptador();

    /** Estado HTTP recibido, o {@code null} si no hubo respuesta. */
    Integer httpStatus();

    /** Texto para el registro de la operación (nunca contiene secretos). */
    String detalle();

    record Confirmado(int status, String cuerpo, AdaptadorIntegracion adaptador) implements ResultadoEnvio {
        @Override
        public Integer httpStatus() {
            return status;
        }

        @Override
        public String detalle() {
            return "Confirmado por " + adaptador + " (HTTP " + status + ")";
        }
    }

    record Rechazado(int status, String cuerpo, AdaptadorIntegracion adaptador) implements ResultadoEnvio {
        @Override
        public Integer httpStatus() {
            return status;
        }

        @Override
        public String detalle() {
            return "Rechazado por " + adaptador + " (HTTP " + status + ")" + resumen(cuerpo);
        }
    }

    record FalloTransitorio(Integer httpStatus, String detalle, AdaptadorIntegracion adaptador)
            implements ResultadoEnvio {
    }

    record Incierto(Integer httpStatus, String detalle, AdaptadorIntegracion adaptador) implements ResultadoEnvio {
    }

    private static String resumen(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return "";
        }
        String limpio = cuerpo.strip();
        return ": " + (limpio.length() > 500 ? limpio.substring(0, 500) + "…" : limpio);
    }
}
