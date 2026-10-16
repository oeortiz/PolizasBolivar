package com.bolivar.polizas.config;

import com.bolivar.polizas.domain.ReglaMesIpc;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.integration.simulated.ModoSimulacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * Propiedades tipadas de la aplicación (prefijo {@code polizas}). Se validan al
 * arrancar: una configuración incompleta impide iniciar la aplicación.
 */
@Validated
@ConfigurationProperties(prefix = "polizas")
public record PolizasProperties(
        @NotNull ZoneId zonaHoraria,
        @NotBlank String canal,
        @NotBlank String usuarioSistema,
        @Valid @NotNull Integracion integracion,
        @Valid @NotNull Ipc ipc
) {

    /**
     * @param reglaMes                     regla para elegir el mes de IPC aplicable (supuesto S-10)
     * @param permitirClaveMensualSinAnio  si es {@code true} y no existe {@code ipc_<aaaa>_<mm>}, se usa
     *                                     {@code ipc_<mes>}; en producción debería ser {@code false}
     */
    public record Ipc(@NotNull ReglaMesIpc reglaMes, boolean permitirClaveMensualSinAnio) {
    }

    /**
     * @param maxIntentos 1 intento inicial + 3 reintentos (supuesto S-08)
     * @param adaptador   SIMULADO (por defecto) o WEBLOGIC. Con SIMULADO ninguna operación llega al CORE real.
     */
    public record Integracion(
            @Min(1) int maxIntentos,
            @NotNull @DefaultValue("SIMULADO") AdaptadorIntegracion adaptador,
            @Valid @DefaultValue WebLogic weblogic,
            @Valid @DefaultValue Simulado simulado,
            @Valid @DefaultValue Procesador procesador
    ) {
    }

    /**
     * Procesador asíncrono del outbox (Etapa 6).
     *
     * @param habilitado                    activa el sondeo periódico y el disparo tras commit
     * @param intervaloReintento            espera entre intentos (requisito: 1 minuto)
     * @param reintentarResultadosInciertos si es {@code false}, un resultado incierto (timeout, 5xx
     *                                      ambiguo) pasa a FALLIDA para revisión en vez de reenviarse.
     *                                      Debe ser {@code false} si el CORE no respeta Idempotency-Key.
     * @param intervaloSondeo               cada cuánto se buscan operaciones vencidas
     * @param tamanoLote                    máximo de operaciones reservadas por ciclo
     * @param umbralOperacionInterrumpida   antigüedad a partir de la cual una operación EN_PROCESO se
     *                                      considera interrumpida (caída del proceso); debe superar
     *                                      holgadamente el timeout de lectura
     */
    public record Procesador(
            @DefaultValue("true") boolean habilitado,
            @NotNull @DefaultValue("1m") Duration intervaloReintento,
            @DefaultValue("true") boolean reintentarResultadosInciertos,
            @NotNull @DefaultValue("5s") Duration intervaloSondeo,
            @Min(1) @DefaultValue("20") int tamanoLote,
            @NotNull @DefaultValue("2m") Duration umbralOperacionInterrumpida
    ) {
    }

    /**
     * Conexión con el servicio de mutaciones publicado en WebLogic. No hay valores por defecto
     * para URL, credenciales ni certificados: se inyectan por variables de entorno o un gestor
     * de secretos. Solo se exigen cuando {@code adaptador = WEBLOGIC} (ver {@code IntegracionConfig}).
     */
    public record WebLogic(
            URI mutacionesUrl,
            URI tokenUrl,
            String clientId,
            String clientSecret,
            String scope,
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("10s") Duration readTimeout,
            String keyStorePath,
            String keyStorePassword,
            @DefaultValue("PKCS12") String keyStoreType,
            String trustStorePath,
            String trustStorePassword,
            @DefaultValue("PKCS12") String trustStoreType,
            @DefaultValue({"TLSv1.3", "TLSv1.2"}) List<String> protocolosTls
    ) {
        /** Representación sin secretos para logs. */
        @Override
        public String toString() {
            return "WebLogic[mutacionesUrl=" + mutacionesUrl + ", tokenUrl=" + tokenUrl + ", clientId=" + clientId
                    + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout
                    + ", protocolosTls=" + protocolosTls + "]";
        }
    }

    /**
     * Comportamiento del adaptador simulado (desarrollo y pruebas).
     *
     * @param fallosAntesDeExito para {@link ModoSimulacion#FALLA_Y_RECUPERA}: intentos fallidos por operación
     * @param httpStatus         estado HTTP simulado en {@link ModoSimulacion#ERROR_HTTP} y FALLA_Y_RECUPERA
     */
    public record Simulado(
            @NotNull @DefaultValue("EXITO") ModoSimulacion modo,
            @Min(0) @DefaultValue("2") int fallosAntesDeExito,
            @DefaultValue("503") int httpStatus,
            @NotNull @DefaultValue("0ms") Duration latencia
    ) {
    }
}
