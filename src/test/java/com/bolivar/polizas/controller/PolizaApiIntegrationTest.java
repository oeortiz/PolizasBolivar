package com.bolivar.polizas.controller;

import com.bolivar.polizas.support.PostgresTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de extremo a extremo de la API (HTTP → servicio → dominio → PostgreSQL).
 * No hay adaptador del CORE todavía: las mutaciones quedan PENDIENTES en el outbox.
 */
// Procesador del outbox deshabilitado: estas pruebas verifican el estado local y las
// operaciones PENDIENTES; el envío al CORE se prueba en ProcesadorOperacionesIntegrationTest.
@SpringBootTest(properties = "polizas.integracion.procesador.habilitado=false")
@AutoConfigureMockMvc
class PolizaApiIntegrationTest extends PostgresTestSupport {

    private static final String API = "/api/v1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        // TRUNCATE no dispara los triggers que impiden DELETE.
        jdbc.execute("truncate table audit_log, renewal_history, integration_operation, riesgo, poliza "
                + "restart identity cascade");
    }

    // ------------------------------------------------------------------
    // Alta (endpoint adicional)
    // ------------------------------------------------------------------

    @Nested
    class Alta {

        @Test
        void creaPolizaIndividualConUnSoloRiesgoYPrimaCalculada() throws Exception {
            mvc.perform(post(API + "/polizas").contentType(MediaType.APPLICATION_JSON)
                            .content(individual("1500000")))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", startsWith("/api/v1/polizas/")))
                    .andExpect(jsonPath("$.tipo").value("INDIVIDUAL"))
                    .andExpect(jsonPath("$.estado").value("ACTIVA"))
                    .andExpect(jsonPath("$.estadoSincronizacion").value("NO_REQUERIDA"))
                    .andExpect(jsonPath("$.canonMensual").value(1500000.00))
                    .andExpect(jsonPath("$.prima").value(18000000.00))
                    .andExpect(jsonPath("$.fechaFinVigencia").value("2027-01-01"))
                    .andExpect(jsonPath("$.tomador.numeroDocumento").value("1020304050"));

            assertThat(jdbc.queryForObject("select count(*) from riesgo", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from audit_log where accion = 'CREAR_POLIZA' and resultado = 'EXITO'",
                    Integer.class)).isEqualTo(1);
        }

        @Test
        void rechazaPolizaIndividualConMasDeUnRiesgo() throws Exception {
            String cuerpo = """
                    {"tipo":"INDIVIDUAL","fechaInicioVigencia":"2026-01-01","duracionMeses":12,
                     "riesgos":[%s,%s]}""".formatted(riesgo("Calle 1", "1000000"), riesgo("Calle 2", "900000"));

            mvc.perform(post(API + "/polizas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"));
            assertThat(jdbc.queryForObject("select count(*) from poliza", Integer.class)).isZero();
        }

        @Test
        void validaLosDatosDeEntrada() throws Exception {
            String cuerpo = """
                    {"tipo":"COLECTIVA","fechaInicioVigencia":"2026-01-01","duracionMeses":0,
                     "riesgos":[{"direccionInmueble":"","canonMensual":1500000.555,
                                 "arrendatario":{"tipoDocumento":"CC","numeroDocumento":"1","nombre":"A"},
                                 "arrendador":{"tipoDocumento":"CC","numeroDocumento":"2","nombre":"B"}}]}""";

            mvc.perform(post(API + "/polizas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty())
                    .andExpect(jsonPath("$.errores[?(@.campo == 'duracionMeses')]").exists())
                    .andExpect(jsonPath("$.errores[?(@.campo == 'riesgos[0].canonMensual')]").exists())
                    .andExpect(jsonPath("$.errores[?(@.campo == 'riesgos[0].direccionInmueble')]").exists());
        }

        @Test
        void rechazaJsonMalFormadoSinExponerDetallesInternos() throws Exception {
            mvc.perform(post(API + "/polizas").contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Nested
    class Consultas {

        @Test
        void listaPolizasFiltrandoPorTipoYEstadoConPaginacion() throws Exception {
            long individual = crearIndividual("1500000");
            crearColectiva();
            crearColectiva();
            cancelarPoliza(individual, null);

            mvc.perform(get(API + "/polizas").param("tipo", "COLECTIVA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(2)))
                    .andExpect(jsonPath("$.totalElementos").value(2));

            mvc.perform(get(API + "/polizas").param("estado", "CANCELADA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(1)))
                    .andExpect(jsonPath("$.contenido[0].id").value(individual));

            mvc.perform(get(API + "/polizas").param("tipo", "INDIVIDUAL").param("estado", "ACTIVA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(0)));

            mvc.perform(get(API + "/polizas").param("size", "2").param("page", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(1)))
                    .andExpect(jsonPath("$.totalPaginas").value(2));
        }

        @Test
        void rechazaParametrosDeListadoInvalidos() throws Exception {
            mvc.perform(get(API + "/polizas").param("tipo", "OTRO"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
            mvc.perform(get(API + "/polizas").param("size", "0"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(API + "/polizas").param("size", "101"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(API + "/polizas").param("page", "-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void consultaLosRiesgosDeUnaPoliza() throws Exception {
            long colectiva = crearColectiva();

            mvc.perform(get(API + "/polizas/{id}/riesgos", colectiva))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].polizaId").value(colectiva))
                    .andExpect(jsonPath("$[0].prima").value(18000000.00))
                    .andExpect(jsonPath("$[1].prima").value(12000000.00));
        }

        @Test
        void respondeNotFoundParaPolizasRiesgosYOperacionesInexistentes() throws Exception {
            mvc.perform(get(API + "/polizas/{id}/riesgos", 999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("POLIZA_NO_ENCONTRADA"));
            mvc.perform(post(API + "/riesgos/{id}/cancelar", 999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("RIESGO_NO_ENCONTRADO"));
            mvc.perform(get(API + "/operaciones/{id}", UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("OPERACION_NO_ENCONTRADA"));
            mvc.perform(get(API + "/operaciones/{id}", "no-es-uuid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void propagaElCorrelationIdRecibido() throws Exception {
            String correlationId = UUID.randomUUID().toString();
            mvc.perform(get(API + "/polizas").header("X-Correlation-Id", correlationId))
                    .andExpect(header().string("X-Correlation-Id", correlationId));
        }
    }

    // ------------------------------------------------------------------
    // Riesgos
    // ------------------------------------------------------------------

    @Nested
    class Riesgos {

        @Test
        void rechazaAgregarRiesgoAPolizaIndividualYAuditaElRechazo() throws Exception {
            long individual = crearIndividual("1500000");

            mvc.perform(post(API + "/polizas/{id}/riesgos", individual)
                            .contentType(MediaType.APPLICATION_JSON).content(riesgo("Calle 9", "900000")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("RIESGO_ADICIONAL_EN_INDIVIDUAL"));

            assertThat(jdbc.queryForObject("select count(*) from riesgo where poliza_id = ?", Integer.class, individual))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from integration_operation", Integer.class)).isZero();
            assertThat(jdbc.queryForObject(
                    "select count(*) from audit_log where accion = 'AGREGAR_RIESGO' and resultado = 'RECHAZADO'",
                    Integer.class)).isEqualTo(1);
        }

        @Test
        void agregaRiesgoAColectivaActualizaConsolidacionYQuedaPendienteDeSincronizar() throws Exception {
            long colectiva = crearColectiva();

            MvcResult resultado = mvc.perform(post(API + "/polizas/{id}/riesgos", colectiva)
                            .contentType(MediaType.APPLICATION_JSON).content(riesgo("Apto 103", "800000")))
                    .andExpect(status().isAccepted())
                    .andExpect(header().string("Location", startsWith("/api/v1/operaciones/")))
                    .andExpect(jsonPath("$.riesgo.prima").value(9600000.00))
                    .andExpect(jsonPath("$.poliza.prima").value(39600000.00))
                    .andExpect(jsonPath("$.poliza.canonMensual").value(3300000.00))
                    .andExpect(jsonPath("$.poliza.estadoSincronizacion").value("PENDIENTE"))
                    .andExpect(jsonPath("$.operacion.operacionNegocio").value("AGREGAR_RIESGO"))
                    .andExpect(jsonPath("$.operacion.accionRemota").value("MODIFICAR"))
                    .andExpect(jsonPath("$.operacion.entityType").value("POLIZA"))
                    .andExpect(jsonPath("$.operacion.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.operacion.maxIntentos").value(4))
                    .andExpect(jsonPath("$.repeticion").value(false))
                    .andReturn();

            String operacionId = cuerpo(resultado).at("/operacion/id").asText();
            mvc.perform(get(API + "/operaciones/{id}", operacionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.intentos").value(0));
        }

        @Test
        void cancelaRiesgoConservaHistorialYActualizaLaConsolidacion() throws Exception {
            long colectiva = crearColectiva();
            long primerRiesgo = idRiesgo(colectiva, 0);

            mvc.perform(post(API + "/riesgos/{id}/cancelar", primerRiesgo)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Fin del arriendo\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.riesgo.estado").value("CANCELADO"))
                    .andExpect(jsonPath("$.riesgo.prima").value(18000000.00))
                    .andExpect(jsonPath("$.poliza.prima").value(12000000.00))
                    .andExpect(jsonPath("$.operacion.accionRemota").value("ANULAR"))
                    .andExpect(jsonPath("$.operacion.entityType").value("RIESGO"))
                    .andExpect(jsonPath("$.operacion.entityId").value(primerRiesgo));

            mvc.perform(get(API + "/polizas/{id}/riesgos", colectiva))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].estado").value("CANCELADO"));

            mvc.perform(post(API + "/riesgos/{id}/cancelar", primerRiesgo))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("RIESGO_YA_CANCELADO"));
        }

        @Test
        void noPermiteCancelarElUltimoRiesgoActivoDeUnaColectiva() throws Exception {
            long colectiva = crearColectiva();
            mvc.perform(post(API + "/riesgos/{id}/cancelar", idRiesgo(colectiva, 0))).andExpect(status().isAccepted());

            mvc.perform(post(API + "/riesgos/{id}/cancelar", idRiesgo(colectiva, 1)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("ULTIMO_RIESGO_ACTIVO"));
        }

        @Test
        void unReintentoConLaMismaIdempotencyKeyNoDuplicaLaOperacion() throws Exception {
            long colectiva = crearColectiva();
            long riesgo = idRiesgo(colectiva, 0);

            MvcResult primero = mvc.perform(post(API + "/riesgos/{id}/cancelar", riesgo)
                            .header("Idempotency-Key", "reintento-1"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.repeticion").value(false))
                    .andReturn();
            MvcResult segundo = mvc.perform(post(API + "/riesgos/{id}/cancelar", riesgo)
                            .header("Idempotency-Key", "reintento-1"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.repeticion").value(true))
                    .andReturn();

            assertThat(cuerpo(segundo).at("/operacion/id").asText())
                    .isEqualTo(cuerpo(primero).at("/operacion/id").asText());
            assertThat(jdbc.queryForObject("select count(*) from integration_operation", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select prima from poliza where id = ?", java.math.BigDecimal.class, colectiva))
                    .isEqualByComparingTo("12000000");
        }

        @Test
        void rechazaIdempotencyKeyConFormatoInvalido() throws Exception {
            long colectiva = crearColectiva();
            mvc.perform(post(API + "/riesgos/{id}/cancelar", idRiesgo(colectiva, 0))
                            .header("Idempotency-Key", "clave con espacios"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"));
        }
    }

    // ------------------------------------------------------------------
    // Cancelación de pólizas
    // ------------------------------------------------------------------

    @Nested
    class CancelacionPoliza {

        @Test
        void cancelaLaPolizaYSusRiesgosSinBorradoFisico() throws Exception {
            long colectiva = crearColectiva();

            mvc.perform(post(API + "/polizas/{id}/cancelar", colectiva)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Solicitud del tomador\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(header().string("Location", startsWith("/api/v1/operaciones/")))
                    .andExpect(jsonPath("$.poliza.estado").value("CANCELADA"))
                    .andExpect(jsonPath("$.poliza.estadoSincronizacion").value("PENDIENTE"))
                    .andExpect(jsonPath("$.poliza.fechaCancelacion").isNotEmpty())
                    .andExpect(jsonPath("$.operacion.operacionNegocio").value("CANCELAR_POLIZA"))
                    .andExpect(jsonPath("$.operacion.accionRemota").value("ANULAR"))
                    .andExpect(jsonPath("$.operacion.enlace", endsWith(
                            jdbc.queryForObject("select id::text from integration_operation", String.class))));

            assertThat(jdbc.queryForObject("select count(*) from poliza", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from riesgo where estado = 'CANCELADO'", Integer.class))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "select count(*) from audit_log where accion = 'CANCELAR_POLIZA' and resultado = 'ACEPTADO_PENDIENTE'",
                    Integer.class)).isEqualTo(1);
        }

        @Test
        void noPermiteCancelarDosVecesNiModificarUnaPolizaCancelada() throws Exception {
            long colectiva = crearColectiva();
            cancelarPoliza(colectiva, null);

            mvc.perform(post(API + "/polizas/{id}/cancelar", colectiva))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("POLIZA_CANCELADA"));
            mvc.perform(post(API + "/polizas/{id}/riesgos", colectiva)
                            .contentType(MediaType.APPLICATION_JSON).content(riesgo("Apto 9", "500000")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("POLIZA_CANCELADA"));
            assertThat(jdbc.queryForObject("select count(*) from integration_operation", Integer.class)).isEqualTo(1);
        }

        @Test
        void repetirLaCancelacionConLaMismaClaveDevuelveLaOperacionOriginal() throws Exception {
            long individual = crearIndividual("1500000");
            cancelarPoliza(individual, "cancelacion-abc");

            mvc.perform(post(API + "/polizas/{id}/cancelar", individual).header("Idempotency-Key", "cancelacion-abc"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.repeticion").value(true))
                    .andExpect(jsonPath("$.poliza.estado").value("CANCELADA"));
            assertThat(jdbc.queryForObject("select count(*) from integration_operation", Integer.class)).isEqualTo(1);
        }

        @Test
        void respondeNotFoundAlCancelarUnaPolizaInexistente() throws Exception {
            mvc.perform(post(API + "/polizas/{id}/cancelar", 12345))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("POLIZA_NO_ENCONTRADA"));
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private long crearIndividual(String canon) throws Exception {
        MvcResult r = mvc.perform(post(API + "/polizas").contentType(MediaType.APPLICATION_JSON)
                        .content(individual(canon)))
                .andExpect(status().isCreated()).andReturn();
        return cuerpo(r).get("id").asLong();
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

    private void cancelarPoliza(long id, String clave) throws Exception {
        var request = post(API + "/polizas/{id}/cancelar", id);
        if (clave != null) {
            request.header("Idempotency-Key", clave);
        }
        mvc.perform(request).andExpect(status().isAccepted());
    }

    private long idRiesgo(long polizaId, int indice) throws Exception {
        MvcResult r = mvc.perform(get(API + "/polizas/{id}/riesgos", polizaId)).andReturn();
        return cuerpo(r).get(indice).get("id").asLong();
    }

    private JsonNode cuerpo(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private String individual(String canon) throws Exception {
        return json.writeValueAsString(Map.of(
                "tipo", "INDIVIDUAL",
                "fechaInicioVigencia", "2026-01-01",
                "duracionMeses", 12,
                "riesgos", new Object[]{json.readValue(riesgo("Calle 100 # 10-20", canon), Map.class)}));
    }

    private static String riesgo(String direccion, String canon) {
        return """
                {"direccionInmueble":"%s","canonMensual":%s,
                 "arrendatario":{"tipoDocumento":"CC","numeroDocumento":"1020304050","nombre":"Carlos Arrendatario"},
                 "arrendador":{"tipoDocumento":"CC","numeroDocumento":"79888777","nombre":"Ana Arrendadora"}}"""
                .formatted(direccion, canon);
    }
}
