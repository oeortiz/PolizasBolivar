package com.bolivar.polizas.entity;

import com.bolivar.polizas.domain.CalculadoraPrima;
import com.bolivar.polizas.domain.Vigencia;
import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.entity.enums.EstadoPoliza;
import com.bolivar.polizas.entity.enums.EstadoRiesgo;
import com.bolivar.polizas.entity.enums.EstadoSincronizacion;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.ResultadoAuditoria;
import com.bolivar.polizas.entity.enums.TipoDocumento;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.bolivar.polizas.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que las migraciones Flyway y el mapeo JPA son coherentes
 * (Hibernate arranca con ddl-auto=validate) y que las restricciones de
 * integridad del esquema se cumplen en PostgreSQL real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EsquemaPersistenciaTest extends PostgresTestSupport {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2027, 1, 1);
    private static final CalculadoraPrima CALCULADORA = new CalculadoraPrima();

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aplicaTodasLasMigracionesYCargaIpcDeEjemplo() {
        Integer migraciones = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(migraciones).isEqualTo(8);

        Map<String, Object> enero = jdbc.queryForMap(
                "select valor from system_constant where nombre = 'ipc_enero'");
        assertThat(enero.get("valor")).isEqualTo("5.60");
        assertThat(jdbc.queryForObject(
                "select valor from system_constant where nombre = 'ipc_febrero'", String.class))
                .isEqualTo("5.80");
    }

    @Test
    void persisteYRecuperaPolizaIndividualConSuRiesgo() {
        Poliza poliza = polizaIndividual();
        em.persist(poliza);
        em.flush();
        em.clear();

        Poliza leida = em.find(Poliza.class, poliza.getId());
        assertThat(leida.getEstado()).isEqualTo(EstadoPoliza.ACTIVA);
        assertThat(leida.getEstadoSincronizacion()).isEqualTo(EstadoSincronizacion.NO_REQUERIDA);
        assertThat(leida.getCanonMensual()).isEqualByComparingTo("1500000");
        assertThat(leida.getPrima()).isEqualByComparingTo("18000000");
        assertThat(leida.getPrima().scale()).isEqualTo(2);
        assertThat(leida.getTomador().tipoDocumento()).isEqualTo(TipoDocumento.CC);
        assertThat(leida.getCreatedAt()).isNotNull();
        assertThat(leida.getVersion()).isZero();
        assertThat(leida.getRiesgos()).singleElement()
                .satisfies(r -> {
                    assertThat(r.getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
                    assertThat(r.getArrendador().nombre()).isEqualTo("Ana Arrendadora");
                });
    }

    @Test
    void rechazaSegundoRiesgoEnPolizaIndividual() {
        Poliza poliza = em.persistAndFlush(polizaIndividual());

        // Se omite a propósito la regla del agregado (Poliza.agregarRiesgo) usando el
        // constructor interno, para comprobar la defensa del trigger en la base de datos.
        Riesgo intruso = new Riesgo(poliza, "Dirección 2", arrendatario(), arrendador(),
                new BigDecimal("1000000.00"), new BigDecimal("12000000.00"));

        assertThatThrownBy(() -> em.persistAndFlush(intruso))
                .rootCause().hasMessageContaining("ya tiene un riesgo asociado");
    }

    @Test
    void permiteVariosRiesgosEnPolizaColectiva() {
        Poliza colectiva = Poliza.crearColectiva(
                new Participante(TipoDocumento.NIT, "900123456", "Inmobiliaria Ejemplo SAS"),
                Vigencia.desde(INICIO, 12),
                List.of(new DatosRiesgo("Apto 101", arrendatario(), arrendador(), new BigDecimal("1500000")),
                        new DatosRiesgo("Apto 102", arrendatario(), arrendador(), new BigDecimal("1000000"))),
                CALCULADORA);
        em.persistAndFlush(colectiva);
        assertThat(colectiva.getPrima()).isEqualByComparingTo("30000000");

        BigDecimal suma = jdbc.queryForObject(
                "select sum(prima) from riesgo where poliza_id = ? and estado = 'ACTIVO'",
                BigDecimal.class, colectiva.getId());
        assertThat(suma).isEqualByComparingTo("30000000");
    }

    @Test
    void impideBorradoFisicoDePolizas() {
        Poliza poliza = em.persistAndFlush(polizaIndividual());

        assertThatThrownBy(() -> jdbc.update("delete from poliza where id = ?", poliza.getId()))
                .hasMessageContaining("No se permite eliminar físicamente");
    }

    @Test
    void impideConstantesDuplicadas() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into system_constant (nombre, valor) values ('ipc_enero', '6.00')"))
                .hasMessageContaining("uk_system_constant_nombre");
    }

    @Test
    void persisteOperacionDeIntegracionPendienteYNoPermiteConfirmarSinEvidencia() {
        IntegrationOperation op = new IntegrationOperation("renovar-1-abc", UUID.randomUUID(),
                TipoEntidad.POLIZA, 1L, OperacionNegocio.RENOVAR_POLIZA, AccionRemota.MODIFICAR,
                "RENOVACION", INICIO, "{\"canonMensual\":\"1584000.00\"}",
                IntegrationOperation.MAX_INTENTOS_POR_DEFECTO, java.time.Instant.now());
        em.persistAndFlush(op);
        em.clear();

        IntegrationOperation leida = em.find(IntegrationOperation.class, op.getId());
        assertThat(leida.getEstado()).isEqualTo(EstadoIntegracion.PENDIENTE);
        assertThat(leida.getIntentos()).isZero();
        assertThat(leida.getMaxIntentos()).isEqualTo(4);
        assertThat(leida.getPayload()).contains("1584000.00");

        assertThatThrownBy(() -> jdbc.update(
                "update integration_operation set estado = 'CONFIRMADA' where id = ?", op.getId()))
                .hasMessageContaining("ck_integration_operation_confirmacion_valida");
    }

    @Test
    void rechazaAccionesRemotasFueraDelContrato() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into integration_operation (id, idempotency_key, correlation_id, entity_type,
                    entity_id, operacion_negocio, accion_remota, effective_date, payload)
                values (gen_random_uuid(), 'k-1', gen_random_uuid(), 'POLIZA', 1,
                    'RENOVAR_POLIZA', 'RENOVAR', current_date, '{}')
                """))
                .hasMessageContaining("ck_integration_operation_accion_remota");
    }

    @Test
    void historialDeRenovacionEsInmutableYNoAdmiteDuplicadosPorReintento() {
        Poliza poliza = em.persistAndFlush(polizaIndividual());
        FotografiaPoliza anterior = FotografiaPoliza.de(poliza);
        FotografiaPoliza nueva = new FotografiaPoliza(new BigDecimal("1584000.00"),
                new BigDecimal("19008000.00"), FIN, FIN.plusMonths(12), EstadoPoliza.RENOVADA);

        RenewalHistory historial = new RenewalHistory(poliza, "renovar-" + poliza.getId() + "-k1", null,
                anterior, nueva, new BigDecimal("5.60"), "ipc_enero", 12, "[]", "tester");
        em.persistAndFlush(historial);
        em.clear();

        RenewalHistory leido = em.find(RenewalHistory.class, historial.getId());
        assertThat(leido.getAnterior().canonMensual()).isEqualByComparingTo("1500000");
        assertThat(leido.getNueva().prima()).isEqualByComparingTo("19008000");
        assertThat(leido.getIpcAplicado()).isEqualByComparingTo("5.60");

        assertThatThrownBy(() -> jdbc.update(
                "update renewal_history set canon_nuevo = 1 where id = ?", historial.getId()))
                .hasMessageContaining("son inmutables");
    }

    @Test
    void registraAuditoria() {
        AuditLog log = new AuditLog("tester", "PORTAL_CLIENTES", "CANCELAR_POLIZA", TipoEntidad.POLIZA,
                1L, ResultadoAuditoria.ACEPTADO_PENDIENTE, UUID.randomUUID(), null,
                "{\"motivo\":\"solicitud del tomador\"}", null);
        em.persistAndFlush(log);
        assertThat(log.getId()).isNotNull();
        assertThat(log.getOccurredAt()).isNotNull();
    }

    private static Poliza polizaIndividual() {
        return Poliza.crearIndividual(
                new DatosRiesgo("Calle 100 # 10-20, Bogotá", arrendatario(), arrendador(), new BigDecimal("1500000")),
                Vigencia.desde(INICIO, 12), CALCULADORA);
    }

    private static Participante arrendatario() {
        return new Participante(TipoDocumento.CC, "1020304050", "Carlos Arrendatario");
    }

    private static Participante arrendador() {
        return new Participante(TipoDocumento.CC, "79888777", "Ana Arrendadora");
    }
}
