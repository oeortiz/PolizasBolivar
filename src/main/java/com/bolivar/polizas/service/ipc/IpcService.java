package com.bolivar.polizas.service.ipc;

import com.bolivar.polizas.config.PolizasProperties;
import com.bolivar.polizas.domain.CalculadoraPrima;
import com.bolivar.polizas.domain.PeriodoIpc;
import com.bolivar.polizas.entity.SystemConstant;
import com.bolivar.polizas.exception.CodigoError;
import com.bolivar.polizas.exception.ReglaNegocioException;
import com.bolivar.polizas.exception.ValorInvalidoException;
import com.bolivar.polizas.repository.SystemConstantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resuelve el IPC aplicable desde {@code system_constant}.
 *
 * <ol>
 *   <li>Determina el periodo con la regla configurada ({@code polizas.ipc.regla-mes}).</li>
 *   <li>Busca la clave con periodo {@code ipc_<aaaa>_<mm>}.</li>
 *   <li>Solo si no existe y {@code polizas.ipc.permitir-clave-mensual-sin-anio=true}, busca
 *       la clave heredada {@code ipc_<mes>} y lo deja registrado en el log.</li>
 *   <li>Valida que el valor sea un decimal con punto (p. ej. {@code 5.60}) y esté en rango.</li>
 *   <li>Si falta o es inválido, falla de forma controlada (409); nunca asume IPC = 0.</li>
 * </ol>
 * Sin demarcación transaccional propia: se ejecuta dentro de la transacción del caso de uso
 * (p. ej. la renovación), de modo que el IPC leído y el cambio guardado son coherentes.
 */
@Service
public class IpcService {

    private static final Logger log = LoggerFactory.getLogger(IpcService.class);

    /** Decimal simple con punto: sin separador de miles, sin coma decimal ni notación científica. */
    private static final Pattern FORMATO_DECIMAL = Pattern.compile("^-?\\d{1,3}(\\.\\d{1,4})?$");

    private final SystemConstantRepository repository;
    private final CalculadoraPrima calculadora;
    private final PolizasProperties.Ipc configuracion;

    public IpcService(SystemConstantRepository repository, CalculadoraPrima calculadora,
                      PolizasProperties properties) {
        this.repository = repository;
        this.calculadora = calculadora;
        this.configuracion = properties.ipc();
    }

    public IpcAplicable obtener(LocalDate inicioNuevaVigencia, LocalDate fechaRenovacion) {
        PeriodoIpc periodo = configuracion.reglaMes().periodo(inicioNuevaVigencia, fechaRenovacion);
        return obtener(periodo);
    }

    public IpcAplicable obtener(PeriodoIpc periodo) {
        String claveConPeriodo = periodo.claveConPeriodo();
        Optional<SystemConstant> constante = repository.findByNombre(claveConPeriodo);
        if (constante.isPresent()) {
            return new IpcAplicable(validar(constante.get()), claveConPeriodo, periodo, false);
        }

        String claveMensual = periodo.claveMensualSinAnio();
        if (!configuracion.permitirClaveMensualSinAnio()) {
            throw noDisponible(periodo, "No hay IPC configurado para " + periodo + " (" + claveConPeriodo
                    + "). El respaldo con " + claveMensual + " está deshabilitado.");
        }
        SystemConstant mensual = repository.findByNombre(claveMensual)
                .orElseThrow(() -> noDisponible(periodo, "No hay IPC configurado para " + periodo
                        + " (se buscó " + claveConPeriodo + " y " + claveMensual + ")"));
        log.warn("IPC de {} tomado de la clave mensual sin año '{}': no existe '{}'",
                periodo, claveMensual, claveConPeriodo);
        return new IpcAplicable(validar(mensual), claveMensual, periodo, true);
    }

    private BigDecimal validar(SystemConstant constante) {
        String valor = constante.getValor() == null ? "" : constante.getValor().trim();
        if (!FORMATO_DECIMAL.matcher(valor).matches()) {
            throw invalido(constante, "no es un número decimal válido (use punto decimal, p. ej. 5.60)");
        }
        try {
            return calculadora.validarIpc(new BigDecimal(valor));
        } catch (ValorInvalidoException e) {
            throw invalido(constante, e.getMessage());
        }
    }

    private static ReglaNegocioException noDisponible(PeriodoIpc periodo, String mensaje) {
        return new ReglaNegocioException(CodigoError.IPC_NO_DISPONIBLE, mensaje);
    }

    private static ReglaNegocioException invalido(SystemConstant constante, String motivo) {
        return new ReglaNegocioException(CodigoError.IPC_INVALIDO,
                "El IPC configurado en '" + constante.getNombre() + "' " + motivo);
    }
}
