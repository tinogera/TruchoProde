package com.truchoprode.exception;

/**
 * Un solo error para los dos casos —el usuario no existe y la contrasena esta mal— y a proposito:
 * si fueran distintos, el login serviria para averiguar que cuentas existen.
 */
public class CredencialesInvalidasException extends NoAutenticadoException {

    public CredencialesInvalidasException() {
        super("Usuario o contrasena incorrectos");
    }

    @Override
    public String codigo() {
        return "CREDENCIALES_INVALIDAS";
    }
}
