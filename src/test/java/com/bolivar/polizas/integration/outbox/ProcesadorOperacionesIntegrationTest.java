package com.bolivar.polizas.integration.outbox;

import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.bolivar.polizas.integration.port.ResultadoEnvio;
import com.bolivar.polizas.integration.port.SolicitudCore;
import com.bolivar.polizas.support.PostgresTestSupport;
import com.bolivar.polizas.support.PuertoCoreProgramable;
import com.bolivar.polizas.support.RelojMutable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Procesador del outbox de extremo a extremo con PostgreSQL. El sondeo automático está
 * deshabilitado: las pruebas invocan el procesador explícitamente y controlan el reloj, de modo
 * que la política "1 intento + 3 reintentos cada minuto" se verifica sin esperar minutos reales.
 * El puerto del CORE es un doble programable (ningún CORE real interviene).
 */
@SpringBootTest(properties = "polizas.integracion.procesador.habilitado=false")
@AutoConfigureMockMvc
class ProcesadorOperacionesIntegrationTest extends PostgresTestSupport {

    private static final String API = "/api/v1";
    private static final Instant INICIO = Instant.parse("2026-10-16T15:00:00Z");

    @TestConfiguration
    static class Dobles {
        @Bean
        @Primary
        PuertoCoreProgramable puertoProgramable() {
            return new PuertoCoreProgramable();
        }

        @Bean
        @Primary
        RelojMutable relojPruebas() {
            return new RelojMutable(INICIO, ZoneId.of("America/Bogota"));
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProcesadorOperaciones procesador;
    @Autowired
    private PuertoCoreProgramable puerto;
    @Autowired
    private RelojMutable reloj;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table audit_log, renewal_history, integration_operation, riesgo, poliza "
                + "restart identity cascade");
        jdbc.update("delete from system_constant where nombre like 'ipc\\_2%'");
        puerto.reiniciar();
        reloj.fijar(INICIO);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from system_constant where nombre like 'ipc\\_2%'");
    }

    @Test
    void todaMutacionSeEnviaPorElPuertoYSoloSeSincronizaConConfirmacion() throws Exception {
        long poliza = crearColectiva();
        long riesgo = idRiesgo(poliza, 0);
        mvc.perform(post(API + "/riesgos/{id}/cancelar", riesgo)).andExpect(status().isAccepted());
        assertThat(estadoSync("riesgo", riesgo)).isEqualTo("PENDIENTE");

        assertThat(procesador.procesarPendientes()).isEqualTo(1);

        assertThat(puerto.recibidas()).singleElement().satisfies(s -> {
            assertThat(s.request().mutation().action()).isEqualTo(AccionRemota.ANULAR);
            assertThat(s.request().mutation().entityType()).isEqualTo(TipoEntidad.RIESGO);
            assertThat(s.request().mutation().entityId()).isEqualTo(String.valueOf(riesgo));
        });
        Map<String, Object> op = operacionUnica();
        assertThat(op.get("estado")).isEqualTo("CONFIRMADA");
        assertThat(op.get("intentos")).isEqualTo(1);
        assertThat(op.get("adaptador")).isEqualTo("SIMULADO");
        assertThat(op.get("resultado").toString()).contains("\"simulado\": true");
        assertThat(estadoSync("riesgo", riesgo)).isEqualTo("SINCRONIZADA");
        assertThat(auditorias("INTEGRACION_CONFIRMADA")).isEqualTo(1);

        // Una operación confirmada no se vuelve a enviar.
        assertThat(procesador.procesarPendientes()).isZero();
        assertThat(puerto.recibidas()).hasSize(1);
    }

    @Test
    void aplicaUnIntentoMasTresReintentosConIntervaloDeUnMinutoYLuegoFalla() throws Exception {
        long poliza = crearColectiva();
        mvc.perform(post(API + "/polizas/{id}/cancelar", poliza)).andExpect(status().isAccepted());
        puerto.siempre(new ResultadoEnvio.FalloTransitorio(503, "CORE no disponible (HTTP 503)",
                AdaptadorIntegracion.SIMULADO));

        for (int intento = 1; intento <= 4; intento++) {
            assertThat(procesador.procesarPendientes()).as("intento %d", intento).isEqualTo(1);
            Map<String, Object> op = operacionUnica();
            assertThat(op.get("intentos")).isEqualTo(intento);
            if (intento < 4) {
                assertThat(op.get("estado")).isEqualTo("PENDIENTE");
                assertThat(((Timestamp) op.get("proximo_intento_at")).toInstant())
                        .isEqualTo(reloj.instant().plus(Duration.ofMinutes(1)));
                // Antes de cumplirse el minuto no se reintenta.
                reloj.avanzar(Duration.ofSeconds(59));
                assertThat(procesador.procesarPendientes()).isZero();
                reloj.avanzar(Duration.ofSeconds(1));
            }
        }

        Map<String, Object> op = operacionUnica();
        assertThat(op.get("estado")).isEqualTo("FALLIDA");
        assertThat(op.get("ultimo_error").toString()).contains("Reintentos agotados (4/4)");
        assertThat(op.get("ultimo_http_status")).isEqualTo(503);
        assertThat(op.get("confirmada_at")).isNull();
        assertThat(puerto.recibidas()).hasSize(4);
        assertThat(estadoSync("poliza", poliza)).isEqualTo("FALLIDA");
        assertThat(auditorias("INTEGRACION_FALLIDA")).isEqualTo(1);

        reloj.avanzar(Duration.ofHours(1));
        assertThat(procesador.procesarPendientes()).isZero();
        assertThat(puerto.recibidas()).hasSize(4);
    }

    @Test
    void todosLosReintentosUsanLaMismaClaveIdempotenteYElMismoCorrelationId() throws Exception {
        long poliza = crearColectiva();
        mvc.perform(post(API + "/polizas/{id}/cancelar", poliza)).andExpect(status().isAccepted());
        puerto.encolar(
                new ResultadoEnvio.Incierto(null, "Timeout esperando respuesta del CORE (10000 ms)", AdaptadorIntegracion.SIMULADO),
                new ResultadoEnvio.FalloTransitorio(null, "Sin conexión con el CORE", AdaptadorIntegracion.SIMULADO));

        procesador.procesarPendientes();
        reloj.avanzar(Duration.ofMinutes(1));
        procesador.procesarPendientes();
        reloj.avanzar(Duration.ofMinutes(1));
        procesador.procesarPendientes();

        assertThat(puerto.recibidas()).hasSize(3);
        SolicitudCore primera = puerto.recibidas().getFirst();
        assertThat(puerto.recibidas()).allSatisfy(s -> {
            assertThat(s.idempotencyKey()).isEqualTo(primera.idempotencyKey());
            assertThat(s.request().header().correlationId()).isEqualTo(primera.request().header().correlationId());
        });
        assertThat(primera.idempotencyKey().toString()).isEqualTo(operacionUnica().get("id").toString());
        assertThat(operacionUnica().get("estado")).isEqualTo("CONFIRMADA");
        assertThat(operacionUnica().get("intentos")).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from integration_operation", Integer.class)).isEqualTo(1);
    }

    @Test
    void unRechazoFuncionalNoSeReintenta() throws Exception {
        long poliza = crearColectiva();
        mvc.perform(post(API + "/polizas/{id}/cancelar", poliza)).andExpect(status().isAccepted());
        puerto.encolar(new ResultadoEnvio.Rechazado(422, "{\"error\":\"poliza inexistente en CORE\"}",
                AdaptadorIntegracion.SIMULADO));

        procesador.procesarPendientes();
        reloj.avanzar(Duration.ofMinutes(5));
        procesador.procesarPendientes();

        assertThat(puerto.recibidas()).hasSize(1);
        assertThat(operacionUnica().get("estado")).isEqualTo("RECHAZADA");
        assertThat(estadoSync("poliza", poliza)).isEqualTo("FALLIDA");
        assertThat(auditorias("INTEGRACION_RECHAZADA")).isEqualTo(1);
    }

    @Test
    void recuperaOperacionesInterrumpidasPorUnReinicio() throws Exception {
        long poliza = crearColectiva();
        mvc.perform(post(API + "/polizas/{id}/cancelar", poliza)).andExpect(status().isAccepted());
        // Simula un proceso que tomó la operación y se cayó antes de registrar el resultado.
        jdbc.update("update integration_operation set estado = 'EN_PROCESO', intentos = 1, adaptador = 'SIMULADO', "
                + "proximo_intento_at = null, ultimo_intento_at = ?", Timestamp.from(INICIO.minus(Duration.ofMinutes(10))));

        assertThat(procesador.recuperarInterrumpidas()).isEqualTo(1);

        Map<String, Object> op = operacionUnica();
        assertThat(op.get("estado")).isEqualTo("PENDIENTE");
        assertThat(op.get("ultimo_error").toString()).contains("interrumpido");
        reloj.avanzar(Duration.ofMinutes(1));
        procesador.procesarPendientes();
        assertThat(operacionUnica().get("estado")).isEqualTo("CONFIRMADA");
        assertThat(operacionUnica().get("intentos")).isEqualTo(2);
    }

    @Test
    void noRecuperaUnaOperacionQueSigueEnCurso() throws Exception {
        long poliza = crearColectiva();
        mvc.perform(post(API + "/polizas/{id}/cancelar", poliza)).andExpect(status().isAccepted());
        jdbc.update("update integration_operation set estado = 'EN_PROCESO', intentos = 1, adaptador = 'SIMULADO', "
                + "proximo_intento_at = null, ultimo_intento_at = ?", Timestamp.from(INICIO.minus(Duration.ofSeconds(30))));

        assertThat(procesador.recuperarInterrumpidas()).isZero();
        assertThat(operacionUnica().get("estado")).isEqualTo("EN_PROCESO");
    }

    @Test
    void elAltaDeUnRiesgoSincronizaPolizaYRiesgo() throws Exception {
        long poliza = crearColectiva();
        MvcResult r = mvc.perform(post(API + "/polizas/{id}/riesgos", poliza).contentType(MediaType.APPLICATION_JSON)
                        .content(riesgo("Apto 103", "800000")))
                .andExpect(status().isAccepted()).andReturn();
        long riesgo = cuerpo(r).at("/riesgo/id").asLong();

        procesador.procesarPendientes();

        assertThat(estadoSync("poliza", poliza)).isEqualTo("SINCRONIZADA");
        assertThat(estadoSync("riesgo", riesgo)).isEqualTo("SINCRONIZADA");
    }

    @Test
    void unaRenovacionConfirmadaPermiteLaSiguiente() throws Exception {
        jdbc.update("insert into system_constant (nombre, valor) values ('ipc_2027_01', '5.60'), ('ipc_2028_01', '5.80')");
        long poliza = crearColectiva();
        mvc.perform(post(API + "/polizas/{id}/renovar", poliza)).andExpect(status().isAccepted());
        mvc.perform(post(API + "/polizas/{id}/renovar", poliza))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RENOVACION_EN_CURSO"));

        procesador.procesarPendientes();

        mvc.perform(get(API + "/polizas/{id}", poliza))
                .andExpect(jsonPath("$.estado").value("RENOVADA"))
                .andExpect(jsonPath("$.estadoSincronizacion").value("SINCRONIZADA"));
        mvc.perform(post(API + "/polizas/{id}/renovar", poliza))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.renovacion.ipcConstante").value("ipc_2028_01"));
    }

    @Test
    void unaOperacionFallidaPuedeReintentarseManualmente() throws Exception {
        long poliza = crearColectiva();
        mvc.perform(post(API + "/polizas/{id}/cancelar", poliza)).andExpect(status().isAccepted());
        puerto.siempre(new ResultadoEnvio.FalloTransitorio(503, "CORE no disponible", AdaptadorIntegracion.SIMULADO));
        for (int i = 0; i < 4; i++) {
            procesador.procesarPendientes();
            reloj.avanzar(Duration.ofMinutes(1));
        }
        String id = operacionUnica().get("id").toString();
        assertThat(operacionUnica().get("estado")).isEqualTo("FALLIDA");

        puerto.reiniciar();
        mvc.perform(post(API + "/operaciones/{id}/reintentar", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.intentos").value(0));
        assertThat(estadoSync("poliza", poliza)).isEqualTo("PENDIENTE");

        procesador.procesarPendientes();
        assertThat(operacionUnica().get("estado")).isEqualTo("CONFIRMADA");
        assertThat(estadoSync("poliza", poliza)).isEqualTo("SINCRONIZADA");
        assertThat(puerto.recibidas().getFirst().idempotencyKey().toString()).isEqualTo(id);

        mvc.perform(post(API + "/operaciones/{id}/reintentar", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("OPERACION_NO_REINTENTABLE"));
        mvc.perform(post(API + "/operaciones/{id}/reintentar", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void laConsultaDeLaOperacionReflejaElEstadoReal() throws Exception {
        long poliza = crearColectiva();
        MvcResult r = mvc.perform(post(API + "/polizas/{id}/cancelar", poliza)).andExpect(status().isAccepted()).andReturn();
        String id = cuerpo(r).at("/operacion/id").asText();
        mvc.perform(get(API + "/operaciones/{id}", id)).andExpect(jsonPath("$.estado").value("PENDIENTE"));

        procesador.procesarOperacion(UUID.fromString(id));

        mvc.perform(get(API + "/operaciones/{id}", id))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.adaptador").value("SIMULADO"))
                .andExpect(jsonPath("$.intentos").value(1));
    }

    // ------------------------------------------------------------------

    private Map<String, Object> operacionUnica() {
        return jdbc.queryForMap("select * from integration_operation");
    }

    private String estadoSync(String tabla, long id) {
        return jdbc.queryForObject("select estado_sincronizacion from " + tabla + " where id = ?", String.class, id);
    }

    private int auditorias(String accion) {
        return jdbc.queryForObject("select count(*) from audit_log where accion = ?", Integer.class, accion);
    }

    private long crearColectiva() throws Exception {
        String cuerpo = """
                {"tipo":"COLECTIVA","fechaInicioVigencia":"2026-01-01","duracionMeses":12,
                 "tomador":{"tipoDocumento":"NIT","numeroDocumento":"900123456","nombre":"Inmobiliaria Ejemplo SAS"},
                 "riesgos":[%s,%s]}""".formatted(riesgo("Apto 101", "1500000"), riesgo("Apto 102", "1000000"));
        MvcResult r = mvc.perform(post(API + "/polizas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn();
        return cuerpo(r).get("id").asLong();
    }

    private long idRiesgo(long polizaId, int indice) throws Exception {
        MvcResult r = mvc.perform(get(API + "/polizas/{id}/riesgos", polizaId)).andReturn();
        return cuerpo(r).get(indice).get("id").asLong();
    }

    private JsonNode cuerpo(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private static String riesgo(String direccion, String canon) {
        return """
                {"direccionInmueble":"%s","canonMensual":%s,
                 "arrendatario":{"tipoDocumento":"CC","numeroDocumento":"1020304050","nombre":"Carlos Arrendatario"},
                 "arrendador":{"tipoDocumento":"CC","numeroDocumento":"79888777","nombre":"Ana Arrendadora"}}"""
                .formatted(direccion, canon);
    }
}
