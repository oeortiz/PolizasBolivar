package com.bolivar.polizas.controller;

import com.bolivar.polizas.support.PostgresTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /polizas/{id}/renovar de extremo a extremo con PostgreSQL. La configuración por
 * defecto exige claves de IPC con periodo (ipc_aaaa_mm): ipc_enero no se usa.
 */
// Procesador del outbox deshabilitado: estas pruebas verifican el estado local y las
// operaciones PENDIENTES; el envío al CORE se prueba en ProcesadorOperacionesIntegrationTest.
@SpringBootTest(properties = "polizas.integracion.procesador.habilitado=false")
@AutoConfigureMockMvc
class RenovacionApiIntegrationTest extends PostgresTestSupport {

    private static final String API = "/api/v1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table audit_log, renewal_history, integration_operation, riesgo, poliza "
                + "restart identity cascade");
        limpiarIpcPorPeriodo();
        // Valor ilustrativo para la prueba (no oficial): nueva vigencia 2027-01 → ipc_2027_01.
        jdbc.update("insert into system_constant (nombre, valor) values ('ipc_2027_01', '5.60')");
    }

    @AfterEach
    void limpiarIpcPorPeriodo() {
        jdbc.update("delete from system_constant where nombre like 'ipc\\_2%'");
    }

    @Test
    void renuevaConIpcDisponibleYGuardaElHistorialCompleto() throws Exception {
        long id = crearIndividual("2026-01-01", "1500000");

        MvcResult resultado = mvc.perform(post(API + "/polizas/{id}/renovar", id))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", startsWith("/api/v1/operaciones/")))
                .andExpect(jsonPath("$.poliza.estado").value("RENOVADA"))
                .andExpect(jsonPath("$.poliza.estadoSincronizacion").value("PENDIENTE"))
                .andExpect(jsonPath("$.poliza.canonMensual").value(1584000.00))
                .andExpect(jsonPath("$.poliza.prima").value(19008000.00))
                .andExpect(jsonPath("$.poliza.fechaInicioVigencia").value("2027-01-01"))
                .andExpect(jsonPath("$.poliza.fechaFinVigencia").value("2028-01-01"))
                .andExpect(jsonPath("$.renovacion.ipcAplicado").value(5.60))
                .andExpect(jsonPath("$.renovacion.ipcConstante").value("ipc_2027_01"))
                .andExpect(jsonPath("$.renovacion.anterior.canonMensual").value(1500000.00))
                .andExpect(jsonPath("$.renovacion.anterior.prima").value(18000000.00))
                .andExpect(jsonPath("$.renovacion.anterior.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.renovacion.anterior.fechaInicioVigencia").value("2026-01-01"))
                .andExpect(jsonPath("$.renovacion.nueva.canonMensual").value(1584000.00))
                .andExpect(jsonPath("$.renovacion.nueva.prima").value(19008000.00))
                .andExpect(jsonPath("$.renovacion.nueva.estado").value("RENOVADA"))
                .andExpect(jsonPath("$.renovacion.riesgos", hasSize(1)))
                .andExpect(jsonPath("$.renovacion.riesgos[0].canonNuevo").value(1584000.00))
                .andExpect(jsonPath("$.operacion.operacionNegocio").value("RENOVAR_POLIZA"))
                .andExpect(jsonPath("$.operacion.accionRemota").value("MODIFICAR"))
                .andExpect(jsonPath("$.operacion.entityType").value("POLIZA"))
                .andExpect(jsonPath("$.operacion.effectiveDate").value("2027-01-01"))
                .andExpect(jsonPath("$.operacion.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.repeticion").value(false))
                .andReturn();

        // El historial no depende de los valores actuales de la póliza.
        Map<String, Object> fila = jdbc.queryForMap("select * from renewal_history where poliza_id = ?", id);
        assertThat((BigDecimal) fila.get("canon_anterior")).isEqualByComparingTo("1500000");
        assertThat((BigDecimal) fila.get("canon_nuevo")).isEqualByComparingTo("1584000");
        assertThat((BigDecimal) fila.get("prima_anterior")).isEqualByComparingTo("18000000");
        assertThat((BigDecimal) fila.get("prima_nueva")).isEqualByComparingTo("19008000");
        assertThat((BigDecimal) fila.get("ipc_aplicado")).isEqualByComparingTo("5.60");
        assertThat(fila.get("estado_anterior")).isEqualTo("ACTIVA");
        assertThat(fila.get("estado_nuevo")).isEqualTo("RENOVADA");
        assertThat(fila.get("integration_operation_id").toString())
                .isEqualTo(cuerpo(resultado).at("/operacion/id").asText());
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where accion = 'RENOVAR_POLIZA' and resultado = 'ACEPTADO_PENDIENTE'",
                Integer.class)).isEqualTo(1);

        mvc.perform(get(API + "/polizas/{id}/renovaciones", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nueva.prima").value(19008000.00));
    }

    @Test
    void renuevaUnaColectivaRecalculandoCadaRiesgoActivo() throws Exception {
        long id = crearColectiva();

        mvc.perform(post(API + "/polizas/{id}/renovar", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.poliza.prima").value(31680000.00))       // (1.584.000 + 1.056.000) × 12
                .andExpect(jsonPath("$.poliza.canonMensual").value(2640000.00))
                .andExpect(jsonPath("$.renovacion.riesgos", hasSize(2)));

        mvc.perform(get(API + "/polizas/{id}/riesgos", id))
                .andExpect(jsonPath("$[0].canonMensual").value(1584000.00))
                .andExpect(jsonPath("$[0].prima").value(19008000.00))
                .andExpect(jsonPath("$[1].canonMensual").value(1056000.00))
                .andExpect(jsonPath("$[1].prima").value(12672000.00));
    }

    @Test
    void unReintentoConLaMismaClaveNoCreaOtroHistorialNiOtraOperacion() throws Exception {
        long id = crearIndividual("2026-01-01", "1500000");

        MvcResult primero = mvc.perform(post(API + "/polizas/{id}/renovar", id).header("Idempotency-Key", "ren-1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.repeticion").value(false))
                .andReturn();
        MvcResult segundo = mvc.perform(post(API + "/polizas/{id}/renovar", id).header("Idempotency-Key", "ren-1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.repeticion").value(true))
                .andExpect(jsonPath("$.poliza.prima").value(19008000.00))
                .andReturn();

        assertThat(cuerpo(segundo).at("/renovacion/id").asLong()).isEqualTo(cuerpo(primero).at("/renovacion/id").asLong());
        assertThat(cuerpo(segundo).at("/operacion/id").asText()).isEqualTo(cuerpo(primero).at("/operacion/id").asText());
        assertThat(jdbc.queryForObject("select count(*) from renewal_history", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from integration_operation", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select canon_mensual from poliza where id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("1584000");
    }

    @Test
    void noEncadenaUnaSegundaRenovacionMientrasLaAnteriorNoEsteConfirmada() throws Exception {
        long id = crearIndividual("2026-01-01", "1500000");
        jdbc.update("insert into system_constant (nombre, valor) values ('ipc_2028_01', '5.80')");
        mvc.perform(post(API + "/polizas/{id}/renovar", id)).andExpect(status().isAccepted());

        // Reintento sin Idempotency-Key: no debe renovar de nuevo hacia 2028.
        mvc.perform(post(API + "/polizas/{id}/renovar", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RENOVACION_EN_CURSO"));

        assertThat(jdbc.queryForObject("select count(*) from renewal_history", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select fecha_inicio_vigencia from poliza where id = ?", String.class, id))
                .isEqualTo("2027-01-01");
    }

    @Test
    void sinIpcDelPeriodoRechazaDeFormaControladaSinCambiarNada() throws Exception {
        // Nueva vigencia 2027-03 → se requiere ipc_2027_03, que no existe.
        long id = crearIndividual("2026-03-01", "1500000");

        mvc.perform(post(API + "/polizas/{id}/renovar", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IPC_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ipc_2027_03")));

        assertThat(jdbc.queryForObject("select estado from poliza where id = ?", String.class, id)).isEqualTo("ACTIVA");
        assertThat(jdbc.queryForObject("select canon_mensual from poliza where id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("1500000");
        assertThat(jdbc.queryForObject("select count(*) from renewal_history", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from integration_operation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where accion = 'RENOVAR_POLIZA' and resultado = 'RECHAZADO'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void conElRespaldoDeshabilitadoNoUsaLaClaveMensualSinAnio() throws Exception {
        // Existe ipc_enero (datos base) pero no ipc_2027_01.
        jdbc.update("delete from system_constant where nombre = 'ipc_2027_01'");
        long id = crearIndividual("2026-01-01", "1500000");

        mvc.perform(post(API + "/polizas/{id}/renovar", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IPC_NO_DISPONIBLE"));
    }

    @Test
    void rechazaIpcConValorNoNumerico() throws Exception {
        jdbc.update("update system_constant set valor = '5,60' where nombre = 'ipc_2027_01'");
        long id = crearIndividual("2026-01-01", "1500000");

        mvc.perform(post(API + "/polizas/{id}/renovar", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IPC_INVALIDO"));
    }

    @Test
    void noRenuevaUnaPolizaCanceladaNiInexistente() throws Exception {
        // Sin IPC para 2027-03: el estado se valida antes que el IPC, así que el error es POLIZA_CANCELADA.
        long id = crearIndividual("2026-03-01", "1500000");
        mvc.perform(post(API + "/polizas/{id}/cancelar", id)).andExpect(status().isAccepted());

        mvc.perform(post(API + "/polizas/{id}/renovar", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("POLIZA_CANCELADA"));
        mvc.perform(post(API + "/polizas/{id}/renovar", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("POLIZA_NO_ENCONTRADA"));
        mvc.perform(get(API + "/polizas/{id}/renovaciones", 999))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------

    private long crearIndividual(String inicio, String canon) throws Exception {
        String cuerpo = """
                {"tipo":"INDIVIDUAL","fechaInicioVigencia":"%s","duracionMeses":12,"riesgos":[%s]}"""
                .formatted(inicio, riesgo("Calle 100 # 10-20", canon));
        MvcResult r = mvc.perform(post(API + "/polizas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
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
