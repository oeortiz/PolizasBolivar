package com.bolivar.polizas.controller;

import com.bolivar.polizas.dto.request.CancelacionRequest;
import com.bolivar.polizas.dto.response.MutacionResponse;
import com.bolivar.polizas.service.RiesgoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Riesgos")
@RestController
@RequestMapping("/api/v1/riesgos")
public class RiesgoController {

    private final RiesgoService riesgoService;

    public RiesgoController(RiesgoService riesgoService) {
        this.riesgoService = riesgoService;
    }

    @Operation(summary = "Cancela un riesgo",
            description = "Sin borrado físico; deja de sumar en la prima consolidada y registra ANULAR sobre el riesgo.")
    @ApiResponse(responseCode = "202", description = "Cancelado localmente; sincronización con el CORE pendiente")
    @ApiResponse(responseCode = "404", description = "El riesgo no existe")
    @ApiResponse(responseCode = "409", description = "Ya cancelado, último riesgo activo o riesgo de póliza INDIVIDUAL")
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<MutacionResponse> cancelar(
            @PathVariable @Positive Long id,
            @Valid @RequestBody(required = false) CancelacionRequest request,
            @Parameter(description = "Clave idempotente opcional (máx. 60)")
            @RequestHeader(name = PolizaController.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        String motivo = request == null ? null : request.motivo();
        return RespuestasMutacion.aceptada(riesgoService.cancelar(id, motivo, idempotencyKey));
    }
}
