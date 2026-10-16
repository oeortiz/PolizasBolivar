package com.bolivar.polizas.exception;

/** El recurso solicitado no existe (HTTP 404). */
public class RecursoNoEncontradoException extends DominioException {

    private static final long serialVersionUID = 1L;

    public RecursoNoEncontradoException(CodigoError codigo, String mensaje) {
        super(codigo, mensaje);
    }

    public static RecursoNoEncontradoException poliza(Long id) {
        return new RecursoNoEncontradoException(CodigoError.POLIZA_NO_ENCONTRADA, "No existe la póliza " + id);
    }

    public static RecursoNoEncontradoException riesgo(Long id) {
        return new RecursoNoEncontradoException(CodigoError.RIESGO_NO_ENCONTRADO, "No existe el riesgo " + id);
    }

    public static RecursoNoEncontradoException operacion(Object id) {
        return new RecursoNoEncontradoException(CodigoError.OPERACION_NO_ENCONTRADA, "No existe la operación " + id);
    }
}
