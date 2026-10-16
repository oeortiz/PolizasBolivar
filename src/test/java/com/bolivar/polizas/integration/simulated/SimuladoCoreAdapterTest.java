package com.bolivar.polizas.integration.simulated;

import com.bolivar.polizas.config.PolizasProperties;
import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.bolivar.polizas.integration.contract.MutationRequest;
import com.bolivar.polizas.integration.port.ResultadoEnvio;
import com.bolivar.polizas.integration.port.SolicitudCore;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SimuladoCoreAdapterTest {

    @Test
    void exitoConfirmaYDejaClaroQueEsSimulado() {
        ResultadoEnvio r = adaptador(ModoSimulacion.EXITO, 0, 503).enviar(solicitud());

        assertThat(r).isInstanceOf(ResultadoEnvio.Confirmado.class);
        assertThat(r.adaptador()).isEqualTo(AdaptadorIntegracion.SIMULADO);
        assertThat(((ResultadoEnvio.Confirmado) r).cuerpo()).contains("\"simulado\":true");
    }

    @Test
    void errorHttpSeClasificaIgualQueElAdaptadorReal() {
        assertThat(adaptador(ModoSimulacion.ERROR_HTTP, 0, 503).enviar(solicitud()))
                .isInstanceOf(ResultadoEnvio.FalloTransitorio.class);
        assertThat(adaptador(ModoSimulacion.ERROR_HTTP, 0, 400).enviar(solicitud()))
                .isInstanceOf(ResultadoEnvio.Rechazado.class);
        assertThat(adaptador(ModoSimulacion.ERROR_HTTP, 0, 504).enviar(solicitud()))
                .isInstanceOf(ResultadoEnvio.Incierto.class);
    }

    @Test
    void timeoutEsUnResultadoIncierto() {
        ResultadoEnvio r = adaptador(ModoSimulacion.TIMEOUT, 0, 503).enviar(solicitud());

        assertThat(r).isInstanceOf(ResultadoEnvio.Incierto.class);
        assertThat(r.httpStatus()).isNull();
        assertThat(r.detalle()).contains("SIMULADO");
    }

    @Test
    void fallaLasVecesConfiguradasYLuegoSeRecuperaPorMutacion() {
        SimuladoCoreAdapter adaptador = adaptador(ModoSimulacion.FALLA_Y_RECUPERA, 2, 503);
        SolicitudCore a = solicitud();
        SolicitudCore b = solicitud();

        assertThat(adaptador.enviar(a)).isInstanceOf(ResultadoEnvio.FalloTransitorio.class);
        assertThat(adaptador.enviar(a)).isInstanceOf(ResultadoEnvio.FalloTransitorio.class);
        assertThat(adaptador.enviar(a)).isInstanceOf(ResultadoEnvio.Confirmado.class);
        // El contador es por mutación: otra mutación empieza fallando.
        assertThat(adaptador.enviar(b)).isInstanceOf(ResultadoEnvio.FalloTransitorio.class);
        assertThat(adaptador.intentosRecibidos(a.idempotencyKey())).isEqualTo(3);
    }

    private static SimuladoCoreAdapter adaptador(ModoSimulacion modo, int fallos, int status) {
        return new SimuladoCoreAdapter(new PolizasProperties.Simulado(modo, fallos, status, Duration.ZERO));
    }

    private static SolicitudCore solicitud() {
        return new SolicitudCore(UUID.randomUUID(), new MutationRequest(
                new MutationRequest.Header(UUID.randomUUID(), "2026-12-20T10:30:00-05:00", "PORTAL_CLIENTES", "system_user"),
                new MutationRequest.Mutation(TipoEntidad.POLIZA, "1", AccionRemota.ANULAR, "CANCELAR_POLIZA",
                        "2026-12-20", JsonNodeFactory.instance.objectNode())));
    }
}
