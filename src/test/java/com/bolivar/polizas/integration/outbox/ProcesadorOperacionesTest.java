package com.bolivar.polizas.integration.outbox;

import com.bolivar.polizas.config.PolizasProperties;
import com.bolivar.polizas.domain.ReglaMesIpc;
import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.bolivar.polizas.integration.contract.ConstructorSolicitudCore;
import com.bolivar.polizas.integration.port.ResultadoEnvio;
import com.bolivar.polizas.integration.simulated.ModoSimulacion;
import com.bolivar.polizas.observability.MetricasIntegracion;
import com.bolivar.polizas.repository.IntegrationOperationRepository;
import com.bolivar.polizas.service.AuditoriaService;
import com.bolivar.polizas.support.PuertoCoreProgramable;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas del procesador que dependen de la configuración, sin base de datos. */
class ProcesadorOperacionesTest {

    private static final Instant T0 = Instant.parse("2026-10-16T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(T0, ZoneId.of("America/Bogota"));

    private final IntegrationOperationRepository repository = mock(IntegrationOperationRepository.class);
    private final SincronizacionEntidades sincronizacion = mock(SincronizacionEntidades.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final PuertoCoreProgramable puerto = new PuertoCoreProgramable();

    @Test
    void sinReintentoDeInciertosUnTimeoutPasaAFallidaParaRevision() {
        IntegrationOperation op = operacion();
        when(repository.reservarPendientes(any(), anyInt())).thenReturn(List.of(op));
        when(repository.findById(op.getId())).thenReturn(Optional.of(op));
        puerto.encolar(new ResultadoEnvio.Incierto(null, "Timeout", AdaptadorIntegracion.SIMULADO));

        procesador(false, AdaptadorIntegracion.SIMULADO, null).procesarPendientes();

        assertThat(op.getEstado()).isEqualTo(EstadoIntegracion.FALLIDA);
        assertThat(op.getIntentos()).isEqualTo(1);
        assertThat(op.getUltimoError()).contains("requiere revisión");
        verify(auditoria).registrar(eq("INTEGRACION_FALLIDA"), eq(TipoEntidad.POLIZA), eq(1L), any(), eq(op.getId()), any());
        verify(sincronizacion).actualizar(op);
    }

    @Test
    void conReintentoDeInciertosUnTimeoutSeReprogramaAUnMinuto() {
        IntegrationOperation op = operacion();
        when(repository.reservarPendientes(any(), anyInt())).thenReturn(List.of(op));
        when(repository.findById(op.getId())).thenReturn(Optional.of(op));
        puerto.encolar(new ResultadoEnvio.Incierto(null, "Timeout", AdaptadorIntegracion.SIMULADO));

        procesador(true, AdaptadorIntegracion.SIMULADO, null).procesarPendientes();

        assertThat(op.getEstado()).isEqualTo(EstadoIntegracion.PENDIENTE);
        assertThat(op.getProximoIntentoAt()).isEqualTo(T0.plus(Duration.ofMinutes(1)));
    }

    @Test
    void unaExcepcionDelAdaptadorSeTrataComoResultadoIncierto() {
        IntegrationOperation op = operacion();
        when(repository.reservarPendientes(any(), anyInt())).thenReturn(List.of(op));
        when(repository.findById(op.getId())).thenReturn(Optional.of(op));
        PuertoCoreProgramable roto = new PuertoCoreProgramable() {
            @Override
            public synchronized ResultadoEnvio enviar(com.bolivar.polizas.integration.port.SolicitudCore s) {
                throw new IllegalStateException("fallo inesperado");
            }
        };

        new ProcesadorOperaciones(repository, roto, constructor(), sincronizacion, auditoria, tx(),
                propiedades(true, AdaptadorIntegracion.SIMULADO, null), new ObjectMapper(), RELOJ, metricas()).procesarPendientes();

        assertThat(op.getEstado()).isEqualTo(EstadoIntegracion.PENDIENTE);
        assertThat(op.getUltimoError()).contains("Error inesperado en el adaptador");
    }

    @Test
    void exigeUnUmbralDeRecuperacionMayorQueElTiempoMaximoDeUnIntento() {
        PolizasProperties.WebLogic weblogic = new PolizasProperties.WebLogic(URI.create("https://w/m"),
                URI.create("https://i/t"), "c", "s", null, Duration.ofSeconds(30), Duration.ofSeconds(60),
                "/k", "x", "PKCS12", "/t", "x", "PKCS12", List.of("TLSv1.3"));

        assertThatThrownBy(() -> procesador(true, AdaptadorIntegracion.WEBLOGIC, weblogic))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("umbral-operacion-interrumpida");
    }

    private ProcesadorOperaciones procesador(boolean reintentarInciertos, AdaptadorIntegracion adaptador,
                                             PolizasProperties.WebLogic weblogic) {
        return new ProcesadorOperaciones(repository, puerto, constructor(), sincronizacion, auditoria, tx(),
                propiedades(reintentarInciertos, adaptador, weblogic), new ObjectMapper(), RELOJ, metricas());
    }

    private static MetricasIntegracion metricas() {
        return new MetricasIntegracion(new SimpleMeterRegistry());
    }

    private ConstructorSolicitudCore constructor() {
        return new ConstructorSolicitudCore(new ObjectMapper(),
                propiedades(true, AdaptadorIntegracion.SIMULADO, null), RELOJ);
    }

    private static TransactionTemplate tx() {
        return new TransactionTemplate(mock(PlatformTransactionManager.class));
    }

    private static PolizasProperties propiedades(boolean reintentarInciertos, AdaptadorIntegracion adaptador,
                                                 PolizasProperties.WebLogic weblogic) {
        return new PolizasProperties(ZoneId.of("America/Bogota"), "PORTAL_CLIENTES", "system_user",
                new PolizasProperties.Integracion(4, adaptador, weblogic,
                        new PolizasProperties.Simulado(ModoSimulacion.EXITO, 2, 503, Duration.ZERO),
                        new PolizasProperties.Procesador(true, Duration.ofMinutes(1), reintentarInciertos,
                                Duration.ofSeconds(5), 20, Duration.ofMinutes(2))),
                new PolizasProperties.Ipc(ReglaMesIpc.MES_INICIO_NUEVA_VIGENCIA, false));
    }

    private static IntegrationOperation operacion() {
        return new IntegrationOperation("CANCELAR_POLIZA:1:" + UUID.randomUUID(), UUID.randomUUID(), TipoEntidad.POLIZA,
                1L, OperacionNegocio.CANCELAR_POLIZA, AccionRemota.ANULAR, "CANCELAR_POLIZA",
                LocalDate.of(2026, 10, 16), "{\"polizaId\":1}", 4, T0);
    }
}
