package com.bolivar.polizas.controller;

import com.bolivar.polizas.dto.response.OperacionResponse;
import com.bolivar.polizas.service.OperacionService;
import com.bolivar.polizas.entity.IntegrationOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** Endpoints adicionales: estado de integración con el CORE y reapertura manual de una mutación. */
@Tag(name = "Operaciones de integración", description = "Estado de sincronización con el CORE")
@RestController
@RequestMapping("/api/v1/operaciones")
public class OperacionController {

    private final OperacionService operacionService;

    public OperacionController(OperacionService operacionService) {
        this.operacionService = operacionService;
    }

    @Operation(summary = "Estado de una operación de integración",
            description = "PENDIENTE, EN_PROCESO, CONFIRMADA, RECHAZADA o FALLIDA, con intentos y último error. "
                    + "adaptador=SIMULADO indica que el CORE real no intervino.")
    @ApiResponse(responseCode = "200", description = "Operación")
    @ApiResponse(responseCode = "404", description = "No existe")
    @GetMapping("/{id}")
    public OperacionResponse obtener(@PathVariable UUID id) {
        return OperacionResponse.de(operacionService.obtener(id));
    }

    /** Reapertura manual de una operación FALLIDA (revisión operativa). Responde 202. */
    @Operation(summary = "Reabre una operación FALLIDA", description = "Nuevo presupuesto de intentos, misma clave idempotente.")
    @ApiResponse(responseCode = "202", description = "Reabierta; se enviará de nuevo")
    @ApiResponse(responseCode = "409", description = "La operación no está FALLIDA")
    @PostMapping("/{id}/reintentar")
    public ResponseEntity<OperacionResponse> reintentar(@PathVariable UUID id) {
        IntegrationOperation op = operacionService.reintentar(id);
        return ResponseEntity.accepted()
                .location(URI.create(OperacionResponse.enlace(op)))
                .body(OperacionResponse.de(op));
    }
}
