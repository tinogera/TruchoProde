package com.truchoprode.exception;

/**
 * A diferencia del login, el registro si dice cual de los dos datos choca. No es una filtracion: en
 * TruchoProde los nombres de usuario son publicos por diseno, porque a un grupo se entra
 * escribiendo el nombre de alguien. Sin este mensaje el registro seria inusable.
 */
public class NombreDeUsuarioTomadoException extends ReglaDeNegocioException {

    public NombreDeUsuarioTomadoException(String nombreUsuario) {
        super("El nombre de usuario '" + nombreUsuario + "' ya esta tomado");
    }

    @Override
    public String codigo() {
        return "NOMBRE_DE_USUARIO_TOMADO";
    }
}
