package com.truchoprode.exception;

public class EmailTomadoException extends ReglaDeNegocioException {

    public EmailTomadoException(String email) {
        super("Ya hay una cuenta registrada con el mail '" + email + "'");
    }

    @Override
    public String codigo() {
        return "EMAIL_TOMADO";
    }
}
