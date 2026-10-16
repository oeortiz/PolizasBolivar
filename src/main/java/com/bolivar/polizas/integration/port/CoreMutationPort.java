package com.bolivar.polizas.integration.port;

import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;

/**
 * Puerto de salida hacia el servicio agnóstico de edición del CORE (arquitectura hexagonal).
 * La lógica de negocio solo conoce esta interfaz; el adaptador concreto (WebLogic real o
 * simulado) se elige por configuración.
 *
 * <p>Contrato del puerto: nunca lanza excepciones por fallos remotos; los clasifica en un
 * {@link ResultadoEnvio}. Un único intento por invocación: los reintentos los gobierna el
 * procesador del outbox.</p>
 */
public interface CoreMutationPort {

    ResultadoEnvio enviar(SolicitudCore solicitud);

    AdaptadorIntegracion tipo();
}
