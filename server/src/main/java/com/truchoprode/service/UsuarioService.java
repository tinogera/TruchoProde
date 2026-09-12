package com.truchoprode.service;

import com.truchoprode.domain.Usuario;
import com.truchoprode.mapper.UsuarioMapper;
import java.util.Optional;

/**
 * Lee y crea usuarios. Esta separado de AutenticacionService a proposito: el filtro de seguridad
 * necesita "buscame este usuario" pero no tiene nada que ver con registrar ni loguear, asi que no
 * deberia depender de esos metodos.
 */
public class UsuarioService {

    private final UsuarioMapper mapper;

    public UsuarioService(UsuarioMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<Usuario> porId(Long id) {
        return Optional.ofNullable(mapper.buscarPorId(id));
    }

    public Optional<Usuario> porNombreOEmail(String identificador) {
        return Optional.ofNullable(mapper.buscarPorNombreOEmail(identificador));
    }

    public boolean nombreDeUsuarioTomado(String nombreUsuario) {
        return mapper.existeNombreDeUsuario(nombreUsuario);
    }

    public boolean emailTomado(String email) {
        return mapper.existeEmail(email);
    }

    public void crear(Usuario usuario) {
        mapper.insertar(usuario);
    }
}
