package com.bolivar.polizas.exception;

import com.bolivar.polizas.config.CorrelationIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Manejo centralizado de errores con Problem Details (RFC 9457).
 *
 * <p>Todas las respuestas de error incluyen {@code codigo} (estable, ver {@link CodigoError})
 * y {@code correlationId}. Nunca se exponen trazas, SQL ni mensajes internos: los errores
 * técnicos se registran en el log con el correlationId y al cliente se le devuelve un
 * mensaje genérico.</p>
 *
 * <ul>
 *   <li>400: validación de entrada y reglas de cálculo ({@link ValorInvalidoException}).</li>
 *   <li>404: recurso inexistente.</li>
 *   <li>409: conflicto de estado, duplicado o concurrencia.</li>
 *   <li>500: error no controlado.</li>
 * </ul>
 * Los fallos del CORE no llegan aquí: se resuelven de forma asíncrona y se consultan en
 * {@code /operaciones/{id}}. 502/503 se reservan para operaciones síncronas futuras.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ProblemDetail> noEncontrado(RecursoNoEncontradoException ex, WebRequest request) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getCodigo(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(ValorInvalidoException.class)
    public ResponseEntity<ProblemDetail> valorInvalido(ValorInvalidoException ex, WebRequest request) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getCodigo(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ProblemDetail> reglaNegocio(ReglaNegocioException ex, WebRequest request) {
        return respuesta(HttpStatus.CONFLICT, ex.getCodigo(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> restriccion(ConstraintViolationException ex, WebRequest request) {
        List<Map<String, String>> errores = ex.getConstraintViolations().stream()
                .map(v -> Map.of("campo", String.valueOf(v.getPropertyPath()), "mensaje", v.getMessage()))
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, CodigoError.SOLICITUD_INVALIDA,
                "La solicitud contiene datos inválidos", request, errores);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class,
            CannotAcquireLockException.class})
    public ResponseEntity<ProblemDetail> concurrencia(RuntimeException ex, WebRequest request) {
        log.info("Conflicto de concurrencia: {}", ex.getClass().getSimpleName());
        return respuesta(HttpStatus.CONFLICT, CodigoError.CONFLICTO_CONCURRENCIA,
                "El recurso fue modificado por otra solicitud; reintente la operación", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> integridad(DataIntegrityViolationException ex, WebRequest request) {
        // Defensa de la base de datos (unicidad, CHECK, triggers). El detalle SQL solo va al log.
        log.warn("Violación de integridad de datos", ex);
        return respuesta(HttpStatus.CONFLICT, CodigoError.CONFLICTO_DATOS,
                "La operación entra en conflicto con el estado actual de los datos", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> inesperado(Exception ex, WebRequest request) {
        log.error("Error no controlado", ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO,
                "Error interno. Use el correlationId para rastrearlo.", request, null);
    }

    // ------------------------------------------------------------------
    // Errores estándar de Spring MVC (validación, JSON ilegible, tipos, 404/405/415...)
    // ------------------------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::error)
                .toList();
        return new ResponseEntity<>(problema(HttpStatus.BAD_REQUEST, CodigoError.SOLICITUD_INVALIDA,
                "La solicitud contiene datos inválidos", request, errores), headers, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<Map<String, String>> errores = ex.getParameterValidationResults().stream()
                .flatMap(r -> r instanceof ParameterErrors errors
                        // @Valid @RequestBody validado como parte de la validación del método
                        ? errors.getFieldErrors().stream().map(GlobalExceptionHandler::error)
                        : r.getResolvableErrors().stream().map(e -> Map.of(
                                "campo", String.valueOf(r.getMethodParameter().getParameterName()),
                                "mensaje", String.valueOf(e.getDefaultMessage()))))
                .toList();
        return new ResponseEntity<>(problema(HttpStatus.BAD_REQUEST, CodigoError.SOLICITUD_INVALIDA,
                "La solicitud contiene parámetros inválidos", request, errores), headers, HttpStatus.BAD_REQUEST);
    }

    /** Enriquece el resto de errores estándar con codigo y correlationId sin exponer detalles internos. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        HttpStatus efectivo = status == null ? HttpStatus.BAD_REQUEST : status;
        CodigoError codigo = efectivo.is5xxServerError() ? CodigoError.ERROR_INTERNO : CodigoError.SOLICITUD_INVALIDA;
        String detalle = efectivo.is5xxServerError()
                ? "Error interno. Use el correlationId para rastrearlo."
                : mensajeEstandar(efectivo);
        return new ResponseEntity<>(problema(efectivo, codigo, detalle, request, null), headers, efectivo);
    }

    // ------------------------------------------------------------------

    private static String mensajeEstandar(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Recurso no encontrado";
            case METHOD_NOT_ALLOWED -> "Método HTTP no permitido para este recurso";
            case UNSUPPORTED_MEDIA_TYPE -> "Tipo de contenido no soportado; use application/json";
            case NOT_ACCEPTABLE -> "Formato de respuesta no disponible";
            default -> "Solicitud inválida: revise el formato del cuerpo y de los parámetros";
        };
    }

    private static Map<String, String> error(FieldError e) {
        return Map.of("campo", e.getField(), "mensaje", String.valueOf(e.getDefaultMessage()));
    }

    private static ResponseEntity<ProblemDetail> respuesta(HttpStatus status, CodigoError codigo, String detalle,
                                                           WebRequest request, List<Map<String, String>> errores) {
        return ResponseEntity.status(status).body(problema(status, codigo, detalle, request, errores));
    }

    private static ProblemDetail problema(HttpStatus status, CodigoError codigo, String detalle,
                                          WebRequest request, List<Map<String, String>> errores) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setType(URI.create("urn:polizas:error:" + codigo.name().toLowerCase()));
        problema.setTitle(status.getReasonPhrase());
        if (request instanceof ServletWebRequest servlet) {
            problema.setInstance(URI.create(servlet.getRequest().getRequestURI()));
        }
        problema.setProperty("codigo", codigo.name());
        problema.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        if (errores != null && !errores.isEmpty()) {
            problema.setProperty("errores", errores);
        }
        return problema;
    }
}
