package com.truchoprode.dto;

import com.truchoprode.domain.Rol;
import com.truchoprode.domain.Usuario;
import com.truchoprode.domain.UsuarioAutenticado;

/** Lo unico del usuario que sale hacia afuera. El hash no esta ni como campo. */
public record UsuarioResponse(Long id, String nombreUsuario, Rol rol) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombreUsuario(), usuario.getRol());
    }

    public static UsuarioResponse de(UsuarioAutenticado usuario) {
        return new UsuarioResponse(usuario.id(), usuario.nombreUsuario(), usuario.rol());
    }
}
