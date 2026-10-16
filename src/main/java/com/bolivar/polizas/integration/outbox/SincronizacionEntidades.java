package com.bolivar.polizas.integration.outbox;

import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.Poliza;
import com.bolivar.polizas.entity.Riesgo;
import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.entity.enums.EstadoSincronizacion;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.bolivar.polizas.repository.IntegrationOperationRepository;
import com.bolivar.polizas.repository.PolizaRepository;
import com.bolivar.polizas.repository.RiesgoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

/**
 * Deriva el estado técnico de sincronización de pólizas y riesgos a partir de TODAS sus
 * operaciones de integración (no solo la última), con la regla "el peor estado manda":
 *
 * <ul>
 *   <li>alguna FALLIDA o RECHAZADA → {@code FALLIDA} (requiere revisión);</li>
 *   <li>si no, alguna PENDIENTE o EN_PROCESO → {@code PENDIENTE};</li>
 *   <li>si todas están CONFIRMADA → {@code SINCRONIZADA}.</li>
 * </ul>
 * Así una confirmación posterior no oculta una mutación anterior que nunca llegó al CORE.
 * El estado contractual de la póliza no se modifica aquí.
 */
@Component
public class SincronizacionEntidades {

    private final PolizaRepository polizaRepository;
    private final RiesgoRepository riesgoRepository;
    private final IntegrationOperationRepository operacionRepository;
    private final ObjectMapper objectMapper;

    public SincronizacionEntidades(PolizaRepository polizaRepository, RiesgoRepository riesgoRepository,
                                   IntegrationOperationRepository operacionRepository, ObjectMapper objectMapper) {
        this.polizaRepository = polizaRepository;
        this.riesgoRepository = riesgoRepository;
        this.operacionRepository = operacionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void actualizar(IntegrationOperation op) {
        // Se bloquea primero la póliza (mismo orden que los casos de uso) para leer su versión
        // vigente y ver las operaciones que otra transacción haya confirmado entretanto.
        Long polizaId = op.getEntityType() == TipoEntidad.POLIZA
                ? op.getEntityId()
                : riesgoRepository.findPolizaIdByRiesgoId(op.getEntityId()).orElse(null);
        if (polizaId == null) {
            return;
        }
        Optional<Poliza> poliza = polizaRepository.findByIdForUpdate(polizaId);
        if (poliza.isEmpty()) {
            return;
        }

        if (op.getEntityType() == TipoEntidad.POLIZA) {
            poliza.get().actualizarEstadoSincronizacion(estadoDe(TipoEntidad.POLIZA, polizaId));
        } else {
            riesgoRepository.findById(op.getEntityId())
                    .ifPresent(r -> r.actualizarEstadoSincronizacion(estadoDe(TipoEntidad.RIESGO, r.getId())));
        }

        // El alta de un riesgo se sincroniza como MODIFICAR sobre la póliza (S-09): el riesgo
        // creado hereda el estado de esa operación combinado con el de sus propias operaciones.
        if (op.getOperacionNegocio() == OperacionNegocio.AGREGAR_RIESGO) {
            riesgoCreado(op).flatMap(riesgoRepository::findById).ifPresent(r ->
                    r.actualizarEstadoSincronizacion(peor(derivado(op.getEstado()), estadoDe(TipoEntidad.RIESGO, r.getId()))));
        }
    }

    private EstadoSincronizacion estadoDe(TipoEntidad tipo, Long id) {
        return agregado(operacionRepository.findByEntityTypeAndEntityId(tipo, id));
    }

    static EstadoSincronizacion agregado(Collection<IntegrationOperation> operaciones) {
        EstadoSincronizacion resultado = operaciones.isEmpty()
                ? EstadoSincronizacion.NO_REQUERIDA
                : EstadoSincronizacion.SINCRONIZADA;
        for (IntegrationOperation op : operaciones) {
            resultado = peor(resultado, derivado(op.getEstado()));
        }
        return resultado;
    }

    static EstadoSincronizacion derivado(EstadoIntegracion estado) {
        return switch (estado) {
            case CONFIRMADA -> EstadoSincronizacion.SINCRONIZADA;
            case FALLIDA, RECHAZADA -> EstadoSincronizacion.FALLIDA;
            case PENDIENTE, EN_PROCESO -> EstadoSincronizacion.PENDIENTE;
        };
    }

    static EstadoSincronizacion peor(EstadoSincronizacion a, EstadoSincronizacion b) {
        return gravedad(a) >= gravedad(b) ? a : b;
    }

    private static int gravedad(EstadoSincronizacion e) {
        return switch (e) {
            case NO_REQUERIDA -> 0;
            case SINCRONIZADA -> 1;
            case PENDIENTE -> 2;
            case FALLIDA -> 3;
        };
    }

    private Optional<Long> riesgoCreado(IntegrationOperation op) {
        try {
            JsonNode nodo = objectMapper.readTree(op.getPayload()).get("riesgoId");
            return nodo != null && nodo.canConvertToLong() ? Optional.of(nodo.asLong()) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
