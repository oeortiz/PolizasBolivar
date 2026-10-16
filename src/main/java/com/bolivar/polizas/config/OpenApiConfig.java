package com.bolivar.polizas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI 3 (springdoc): {@code /v3/api-docs} y Swagger UI en {@code /swagger-ui.html}.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI polizasOpenApi() {
        return new OpenAPI().info(new Info()
                .title("API de pólizas de arrendamiento")
                .version("v1")
                .description("""
                        Gestión de pólizas de arrendamiento INDIVIDUAL y COLECTIVA: riesgos, prima \
                        (canon × meses), renovación con IPC, cancelaciones e historial.

                        **Integración con el CORE:** toda mutación se registra localmente y se sincroniza \
                        de forma asíncrona con el servicio de edición de WebLogic. Las mutaciones responden \
                        **202 Accepted** con un enlace a `/api/v1/operaciones/{id}`; no son un éxito definitivo \
                        hasta que la operación está CONFIRMADA.

                        **Idempotencia:** las mutaciones aceptan la cabecera opcional `Idempotency-Key` \
                        (máx. 60 caracteres). Repetir la solicitud con la misma clave devuelve el resultado \
                        original (`repeticion: true`).

                        **Errores:** formato Problem Details (RFC 9457) con `codigo` y `correlationId`.

                        **Trazabilidad:** envíe `X-Correlation-Id` (UUID) o se generará uno; se devuelve en la respuesta."""));
    }
}
