package com.bolivar.polizas.controller;

import com.bolivar.polizas.config.CoreMockApiKeyFilter;
import com.bolivar.polizas.dto.request.CoreMockEventoRequest;
import com.bolivar.polizas.dto.response.CoreMockEventoResponse;
import com.bolivar.polizas.service.CoreMockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock externo del CORE. La cabecera {@code x-api-key} se valida en
 * {@link CoreMockApiKeyFilter} antes de llegar aquí.
 */
@RestController
@RequestMapping("/core-mock")
@Tag(name = "Mock del CORE", description = "Registra en logs que una operación se intentó enviar al CORE")
@SecurityRequirement(name = CoreMockApiKeyFilter.ESQUEMA_SEGURIDAD)
public class CoreMockController {

    private final CoreMockService servicio;

    public CoreMockController(CoreMockService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/evento")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Registra en el log un intento de envío al CORE",
            description = "Requiere la cabecera x-api-key. No consulta ni modifica pólizas.")
    @ApiResponse(responseCode = "202", description = "Intento registrado en el log")
    @ApiResponse(responseCode = "400", description = "Cuerpo inválido")
    @ApiResponse(responseCode = "401", description = "Falta x-api-key o no es válida")
    public CoreMockEventoResponse registrar(@Valid @RequestBody CoreMockEventoRequest evento) {
        return servicio.registrarIntento(evento);
    }
}
