package com.bolivar.polizas.config;

import com.bolivar.polizas.domain.ReglaMesIpc;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.integration.port.CoreMutationPort;
import com.bolivar.polizas.integration.simulated.ModoSimulacion;
import com.bolivar.polizas.integration.weblogic.SslContextFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegracionConfigTest {

    @Test
    void porDefectoUsaElAdaptadorSimulado() {
        CoreMutationPort port = new IntegracionConfig().coreMutationPort(
                propiedades(AdaptadorIntegracion.SIMULADO, webLogicVacio()), new ObjectMapper(), Clock.systemUTC());

        assertThat(port.tipo()).isEqualTo(AdaptadorIntegracion.SIMULADO);
    }

    @Test
    void weblogicSinConfiguracionNoArrancaYEnumeraLoQueFaltaSinValores() {
        assertThatThrownBy(() -> new IntegracionConfig().coreMutationPort(
                propiedades(AdaptadorIntegracion.WEBLOGIC, webLogicVacio()), new ObjectMapper(), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mutaciones-url")
                .hasMessageContaining("client-secret")
                .hasMessageContaining("key-store-path")
                .hasMessageContaining("trust-store-path");
    }

    @Test
    void exigeHttpsEnLosEndpointsRemotos() {
        PolizasProperties.WebLogic http = new PolizasProperties.WebLogic(
                URI.create("http://weblogic.local/mutaciones"), URI.create("http://idp.local/token"),
                "c", "s", null, Duration.ofSeconds(5), Duration.ofSeconds(10),
                "/k.p12", "x", "PKCS12", "/t.p12", "x", "PKCS12", List.of("TLSv1.3"));

        assertThatThrownBy(() -> IntegracionConfig.validar(http))
                .hasMessageContaining("mutaciones-url (https://...)")
                .hasMessageContaining("token-url (https://...)")
                .hasMessageNotContaining("client-secret");
    }

    @Test
    void soloPermiteTls12OSuperior() {
        assertThat(SslContextFactory.parametros(List.of("TLSv1.3", "TLSv1.2")).getProtocols())
                .containsExactly("TLSv1.3", "TLSv1.2");
        assertThatThrownBy(() -> SslContextFactory.parametros(List.of("TLSv1.1")))
                .hasMessageContaining("TLSv1.1");
        assertThat(SslContextFactory.parametros(List.of("TLSv1.2")).getEndpointIdentificationAlgorithm())
                .isEqualTo("HTTPS");
    }

    private static PolizasProperties.WebLogic webLogicVacio() {
        return new PolizasProperties.WebLogic(null, null, null, null, null, Duration.ofSeconds(5), Duration.ofSeconds(10),
                null, null, "PKCS12", null, null, "PKCS12", List.of("TLSv1.3", "TLSv1.2"));
    }

    private static PolizasProperties propiedades(AdaptadorIntegracion adaptador, PolizasProperties.WebLogic weblogic) {
        return new PolizasProperties(ZoneId.of("America/Bogota"), "PORTAL_CLIENTES", "system_user",
                new PolizasProperties.Integracion(4, adaptador, weblogic,
                        new PolizasProperties.Simulado(ModoSimulacion.EXITO, 2, 503, Duration.ZERO), null),
                new PolizasProperties.Ipc(ReglaMesIpc.MES_INICIO_NUEVA_VIGENCIA, false));
    }
}
