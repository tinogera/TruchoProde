package com.truchoprode.exception;

/**
 * Raiz de los errores propios del dominio. Cada una sabe dos cosas: un codigo estable que el
 * frontend puede usar para decidir que mostrar, y el estado HTTP que le corresponde.
 */
public abstract class TruchoProdeException extends RuntimeException {

    protected TruchoProdeException(String mensaje) {
        super(mensaje);
    }

    public abstract String codigo();

    public abstract int estadoHttp();
}
