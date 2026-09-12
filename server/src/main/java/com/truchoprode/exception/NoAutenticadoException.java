package com.truchoprode.exception;

/**
 * "No se quien sos": 401. Es distinto de NoAutorizadoException, que sera el 403 de "se quien sos y
 * no te alcanza". Confundir las dos es el error clasico de esta parte.
 */
public abstract class NoAutenticadoException extends TruchoProdeException {

    protected NoAutenticadoException(String mensaje) {
        super(mensaje);
    }

    @Override
    public int estadoHttp() {
        return 401;
    }
}
