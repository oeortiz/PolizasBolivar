package com.bolivar.polizas.service.ipc;

import com.bolivar.polizas.config.PolizasProperties;
import com.bolivar.polizas.domain.CalculadoraPrima;
import com.bolivar.polizas.domain.PeriodoIpc;
import com.bolivar.polizas.domain.ReglaMesIpc;
import com.bolivar.polizas.entity.SystemConstant;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.exception.CodigoError;
import com.bolivar.polizas.exception.ReglaNegocioException;
import com.bolivar.polizas.repository.SystemConstantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IpcServiceTest {

    private static final PeriodoIpc ENERO_2027 = PeriodoIpc.de(2027, 1);

    private final SystemConstantRepository repository = mock(SystemConstantRepository.class);

    @Test
    void prefiereLaClaveConPeriodoSobreLaMensual() {
        constante("ipc_2027_01", "5.20");
        constante("ipc_enero", "5.60");

        IpcAplicable ipc = servicio(true).obtener(ENERO_2027);

        assertThat(ipc.porcentaje()).isEqualByComparingTo("5.20");
        assertThat(ipc.constanteNombre()).isEqualTo("ipc_2027_01");
        assertThat(ipc.claveMensualSinAnio()).isFalse();
        verify(repository, never()).findByNombre("ipc_enero");
    }

    @Test
    void recurreALaClaveMensualSoloSiLaConfiguracionLoPermite() {
        sinConstante("ipc_2027_01");
        constante("ipc_enero", "5.60");

        IpcAplicable ipc = servicio(true).obtener(ENERO_2027);

        assertThat(ipc.porcentaje()).isEqualByComparingTo("5.60");
        assertThat(ipc.constanteNombre()).isEqualTo("ipc_enero");
        assertThat(ipc.claveMensualSinAnio()).isTrue();
    }

    @Test
    void conElRespaldoDeshabilitadoNoUsaLaClaveMensual() {
        sinConstante("ipc_2027_01");
        constante("ipc_enero", "5.60");

        assertThatThrownBy(() -> servicio(false).obtener(ENERO_2027))
                .isInstanceOf(ReglaNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", CodigoError.IPC_NO_DISPONIBLE)
                .hasMessageContaining("ipc_2027_01");
        verify(repository, never()).findByNombre("ipc_enero");
    }

    @Test
    void fallaDeFormaControladaSiNoExisteNingunaClaveSinAsumirCero() {
        when(repository.findByNombre(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio(true).obtener(ENERO_2027))
                .isInstanceOf(ReglaNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", CodigoError.IPC_NO_DISPONIBLE)
                .hasMessageContaining("ipc_2027_01")
                .hasMessageContaining("ipc_enero");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "5,60", "5.6.0", "1e2", "5.12345", "1000", "-100", "-150.5"})
    void rechazaValoresNoNumericosOFueraDeRango(String valor) {
        constante("ipc_2027_01", valor);

        assertThatThrownBy(() -> servicio(true).obtener(ENERO_2027))
                .isInstanceOf(ReglaNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", CodigoError.IPC_INVALIDO)
                .hasMessageContaining("ipc_2027_01");
    }

    @ParameterizedTest
    @ValueSource(strings = {"5.60", " 5.6 ", "0", "-1.25", "12.3456"})
    void aceptaPorcentajesValidosIncluidoCeroExplicito(String valor) {
        constante("ipc_2027_01", valor);

        assertThat(servicio(true).obtener(ENERO_2027).porcentaje()).isNotNull();
    }

    @Test
    void aplicaLaReglaDeMesConfigurada() {
        constante("ipc_2026_12", "5.10");
        IpcService servicio = new IpcService(repository, new CalculadoraPrima(),
                propiedades(ReglaMesIpc.MES_ANTERIOR_INICIO_NUEVA_VIGENCIA, false));

        IpcAplicable ipc = servicio.obtener(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 15));

        assertThat(ipc.constanteNombre()).isEqualTo("ipc_2026_12");
        assertThat(ipc.periodo()).isEqualTo(PeriodoIpc.de(2026, 12));
    }

    private IpcService servicio(boolean permitirMensual) {
        return new IpcService(repository, new CalculadoraPrima(),
                propiedades(ReglaMesIpc.MES_INICIO_NUEVA_VIGENCIA, permitirMensual));
    }

    private static PolizasProperties propiedades(ReglaMesIpc regla, boolean permitirMensual) {
        return new PolizasProperties(ZoneId.of("America/Bogota"), "PORTAL_CLIENTES", "system_user",
                new PolizasProperties.Integracion(4, AdaptadorIntegracion.SIMULADO, null, null, null),
                new PolizasProperties.Ipc(regla, permitirMensual));
    }

    private void constante(String nombre, String valor) {
        when(repository.findByNombre(nombre)).thenReturn(Optional.of(new SystemConstant(nombre, valor, null)));
    }

    private void sinConstante(String nombre) {
        when(repository.findByNombre(nombre)).thenReturn(Optional.empty());
    }
}
