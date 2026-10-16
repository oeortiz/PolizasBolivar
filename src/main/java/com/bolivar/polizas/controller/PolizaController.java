package com.bolivar.polizas.controller;

import com.bolivar.polizas.dto.request.CancelacionRequest;
import com.bolivar.polizas.dto.request.CrearPolizaRequest;
import com.bolivar.polizas.dto.request.RiesgoRequest;
import com.bolivar.polizas.dto.response.HistorialRenovacionResponse;
import com.bolivar.polizas.dto.response.MutacionResponse;
import com.bolivar.polizas.dto.response.PaginaResponse;
import com.bolivar.polizas.dto.response.PolizaResponse;
import com.bolivar.polizas.dto.response.RenovacionResponse;
import com.bolivar.polizas.dto.response.RiesgoResponse;
import com.bolivar.polizas.entity.Poliza;
import com.bolivar.polizas.entity.enums.EstadoPoliza;
import com.bolivar.polizas.entity.enums.TipoPoliza;
import com.bolivar.polizas.service.PolizaService;
import com.bolivar.polizas.service.RiesgoService;
import com.bolivar.polizas.service.renovacion.RenovacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Endpoints de pólizas bajo {@code /api/v1}. Solo traduce HTTP ↔ casos de uso;
 * las reglas viven en el dominio y los servicios.
 */
@Tag(name = "Pólizas", description = "Consulta, alta, renovación y cancelación de pólizas y alta de riesgos")
@RestController
@RequestMapping("/api/v1/polizas")
public class PolizaController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final PolizaService polizaService;
    private final RiesgoService riesgoService;
    private final RenovacionService renovacionService;

    public PolizaController(PolizaService polizaService, RiesgoService riesgoService,
                            RenovacionService renovacionService) {
        this.polizaService = polizaService;
        this.riesgoService = riesgoService;
        this.renovacionService = renovacionService;
    }

    /** Lista pólizas con filtros opcionales por tipo y estado, paginadas y ordenadas por id. */
    @Operation(summary = "Lista pólizas", description = "Filtros opcionales por tipo y estado; paginada y ordenada por id.")
    @ApiResponse(responseCode = "200", description = "Página de pólizas")
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos")
    @GetMapping
    public PaginaResponse<PolizaResponse> listar(
            @RequestParam(required = false) TipoPoliza tipo,
            @RequestParam(required = false) EstadoPoliza estado,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PaginaResponse.de(polizaService.listar(tipo, estado, page, size), PolizaResponse::de);
    }

    /** Endpoint adicional: consulta de una póliza (destino del header Location del alta). */
    @Operation(summary = "Consulta una póliza (endpoint adicional)")
    @ApiResponse(responseCode = "200", description = "Póliza")
    @ApiResponse(responseCode = "404", description = "No existe")
    @GetMapping("/{id}")
    public PolizaResponse obtener(@PathVariable @Positive Long id) {
        return PolizaResponse.de(polizaService.obtener(id));
    }

    @Operation(summary = "Riesgos de una póliza", description = "Activos y cancelados, en orden de alta.")
    @ApiResponse(responseCode = "200", description = "Riesgos")
    @ApiResponse(responseCode = "404", description = "La póliza no existe")
    @GetMapping("/{id}/riesgos")
    public List<RiesgoResponse> riesgos(@PathVariable @Positive Long id) {
        return polizaService.riesgos(id).stream().map(RiesgoResponse::de).toList();
    }

    /** Endpoint adicional (supuesto S-15): alta local, sin mutación al CORE → 201. */
    @Operation(summary = "Crea una póliza (endpoint adicional)",
            description = "INDIVIDUAL: exactamente un riesgo y sin tomador (es el arrendatario). COLECTIVA: tomador y "
                    + "uno o varios riesgos. La prima la calcula el servidor (canon × meses).")
    @ApiResponse(responseCode = "201", description = "Póliza creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @PostMapping
    public ResponseEntity<PolizaResponse> crear(@Valid @RequestBody CrearPolizaRequest request) {
        Poliza poliza = polizaService.crear(
                request.tipo(),
                request.tomador() == null ? null : request.tomador().aDominio(),
                request.fechaInicioVigencia(),
                request.duracionMeses(),
                request.riesgos().stream().map(RiesgoRequest::aDominio).toList());
        return ResponseEntity.created(URI.create("/api/v1/polizas/" + poliza.getId()))
                .body(PolizaResponse.de(poliza));
    }

    /**
     * Renueva la póliza con el IPC del periodo de la nueva vigencia. Idempotente con
     * {@code Idempotency-Key}: un reintento devuelve el mismo historial y la misma operación.
     */
    @Operation(summary = "Renueva una póliza con el IPC",
            description = "Aplica el IPC del periodo (ipc_aaaa_mm) al canon, recalcula la prima, desplaza la vigencia, "
                    + "guarda el historial y registra MODIFICAR para el CORE.")
    @ApiResponse(responseCode = "202", description = "Renovada localmente; sincronización con el CORE pendiente")
    @ApiResponse(responseCode = "200", description = "Repetición de una renovación ya confirmada por el CORE")
    @ApiResponse(responseCode = "404", description = "La póliza no existe")
    @ApiResponse(responseCode = "409", description = "Cancelada, IPC no disponible/inválido o renovación en curso")
    @PostMapping("/{id}/renovar")
    public ResponseEntity<RenovacionResponse> renovar(
            @PathVariable @Positive Long id,
            @Parameter(description = "Clave idempotente opcional (máx. 60)")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return RespuestasMutacion.aceptada(renovacionService.renovar(id, idempotencyKey));
    }

    /** Endpoint adicional: historial de renovaciones de la póliza, en orden cronológico. */
    @Operation(summary = "Historial de renovaciones (endpoint adicional)")
    @ApiResponse(responseCode = "200", description = "Renovaciones en orden cronológico")
    @ApiResponse(responseCode = "404", description = "La póliza no existe")
    @GetMapping("/{id}/renovaciones")
    public List<HistorialRenovacionResponse> renovaciones(@PathVariable @Positive Long id) {
        return renovacionService.historial(id).stream().map(HistorialRenovacionResponse::de).toList();
    }

    @Operation(summary = "Cancela una póliza", description = "Sin borrado físico; cancela sus riesgos activos y registra ANULAR.")
    @ApiResponse(responseCode = "202", description = "Cancelada localmente; sincronización con el CORE pendiente")
    @ApiResponse(responseCode = "404", description = "La póliza no existe")
    @ApiResponse(responseCode = "409", description = "Ya cancelada")
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<MutacionResponse> cancelar(
            @PathVariable @Positive Long id,
            @Valid @RequestBody(required = false) CancelacionRequest request,
            @Parameter(description = "Clave idempotente opcional (máx. 60)")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        String motivo = request == null ? null : request.motivo();
        return RespuestasMutacion.aceptada(polizaService.cancelar(id, motivo, idempotencyKey));
    }

    /** Solo pólizas COLECTIVA; la prima consolidada se actualiza en la misma transacción. */
    @Operation(summary = "Agrega un riesgo a una póliza COLECTIVA",
            description = "Calcula la prima del riesgo con la vigencia de la póliza y actualiza la prima consolidada.")
    @ApiResponse(responseCode = "202", description = "Riesgo agregado; sincronización con el CORE pendiente")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "404", description = "La póliza no existe")
    @ApiResponse(responseCode = "409", description = "Póliza INDIVIDUAL o cancelada")
    @PostMapping("/{id}/riesgos")
    public ResponseEntity<MutacionResponse> agregarRiesgo(
            @PathVariable @Positive Long id,
            @Valid @RequestBody RiesgoRequest request,
            @Parameter(description = "Clave idempotente opcional (máx. 60)")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return RespuestasMutacion.aceptada(riesgoService.agregar(id, request.aDominio(), idempotencyKey));
    }
}
