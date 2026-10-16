package com.bolivar.polizas.integration.contract;

import com.bolivar.polizas.config.PolizasProperties;
import com.bolivar.polizas.domain.ReglaMesIpc;
import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.bolivar.polizas.integration.port.SolicitudCore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConstructorSolicitudCoreTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final ObjectMapper json = new ObjectMapper();
    private final ConstructorSolicitudCore constructor = new ConstructorSolicitudCore(json, propiedades(),
            Clock.fixed(Instant.parse("2026-12-20T15:30:00Z"), BOGOTA));

    @Test
    void construyeLaSolicitudConLaEstructuraDelContrato() throws Exception {
        UUID correlationId = UUID.randomUUID();
        IntegrationOperation op = new IntegrationOperation("RENOVAR_POLIZA:7:k", correlationId, TipoEntidad.POLIZA,
                7L, OperacionNegocio.RENOVAR_POLIZA, AccionRemota.MODIFICAR, "RENOVAR_POLIZA",
                LocalDate.of(2027, 1, 1), "{\"polizaId\":7,\"prima\":19008000.00}", 4, Instant.now());

        SolicitudCore solicitud = constructor.construir(op);
        JsonNode cuerpo = json.readTree(json.writeValueAsString(solicitud.request()));

        assertThat(solicitud.idempotencyKey()).isEqualTo(op.getId());
        assertThat(cuerpo.fieldNames()).toIterable().containsExactlyInAnyOrder("header", "mutation");
        assertThat(cuerpo.get("header").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("correlationId", "timestamp", "channel", "user");
        assertThat(cuerpo.at("/header/correlationId").asText()).isEqualTo(correlationId.toString());
        assertThat(cuerpo.at("/header/timestamp").asText()).isEqualTo("2026-12-20T10:30:00-05:00");
        assertThat(cuerpo.at("/header/channel").asText()).isEqualTo("PORTAL_CLIENTES");
        assertThat(cuerpo.at("/header/user").asText()).isEqualTo("system_user");

        assertThat(cuerpo.get("mutation").fieldNames()).toIterable().containsExactlyInAnyOrder(
                "entityType", "entityId", "action", "reasonCode", "effectiveDate", "payload");
        assertThat(cuerpo.at("/mutation/entityType").asText()).isEqualTo("POLIZA");
        assertThat(cuerpo.at("/mutation/entityId").asText()).isEqualTo("7");
        assertThat(cuerpo.at("/mutation/action").asText()).isEqualTo("MODIFICAR");
        assertThat(cuerpo.at("/mutation/reasonCode").asText()).isEqualTo("RENOVAR_POLIZA");
        assertThat(cuerpo.at("/mutation/effectiveDate").asText()).isEqualTo("2027-01-01");
        assertThat(cuerpo.at("/mutation/payload/prima").decimalValue()).isEqualByComparingTo("19008000.00");
    }

    @Test
    void elCorrelationIdYLaClaveSonEstablesEntreReintentos() {
        IntegrationOperation op = new IntegrationOperation("CANCELAR_RIESGO:3:k", UUID.randomUUID(), TipoEntidad.RIESGO,
                3L, OperacionNegocio.CANCELAR_RIESGO, AccionRemota.ANULAR, null, LocalDate.of(2026, 3, 1), "{}", 4, Instant.now());

        SolicitudCore primero = constructor.construir(op);
        SolicitudCore segundo = constructor.construir(op);

        assertThat(segundo.idempotencyKey()).isEqualTo(primero.idempotencyKey());
        assertThat(segundo.request().header().correlationId()).isEqualTo(primero.request().header().correlationId());
        assertThat(segundo.request().mutation().action()).isEqualTo(AccionRemota.ANULAR);
        assertThat(segundo.request().mutation().entityType()).isEqualTo(TipoEntidad.RIESGO);
    }

    private static PolizasProperties propiedades() {
        return new PolizasProperties(BOGOTA, "PORTAL_CLIENTES", "system_user",
                new PolizasProperties.Integracion(4, AdaptadorIntegracion.SIMULADO, null, null, null),
                new PolizasProperties.Ipc(ReglaMesIpc.MES_INICIO_NUEVA_VIGENCIA, false));
    }
}
