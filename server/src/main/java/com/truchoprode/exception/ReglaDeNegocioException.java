package com.truchoprode.exception;

/**
 * Se pidio algo que choca con el estado actual: de ahi el 409. Una hija puede sobreescribir el
 * estado si le corresponde otro.
 */
public abstract class ReglaDeNegocioException extends TruchoProdeException {

    protected ReglaDeNegocioException(String mensaje) {
        super(mensaje);
    }

    @Override
    public int estadoHttp() {
        return 409;
    }
}
