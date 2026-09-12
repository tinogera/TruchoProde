package com.truchoprode.service;

import com.truchoprode.domain.Autenticacion;
import com.truchoprode.domain.Rol;
import com.truchoprode.domain.TokenFirmado;
import com.truchoprode.domain.Usuario;
import com.truchoprode.exception.EmailTomadoException;
import com.truchoprode.exception.NombreDeUsuarioTomadoException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Las dos operaciones de la puerta de entrada: registrarse y loguearse. */
public class AutenticacionService {

    private final UsuarioService usuarios;
    private final PasswordEncoder codificador;
    private final JwtService jwt;

    public AutenticacionService(
            UsuarioService usuarios, PasswordEncoder codificador, JwtService jwt) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.jwt = jwt;
    }

    /**
     * El rol no es un parametro: todo el que se registra nace JUGADOR. El primer ADMIN se promueve
     * con un UPDATE a mano, asi ningun endpoint puede regalar privilegios.
     */
    public Autenticacion registrar(String nombreUsuario, String email, String contrasena) {
        if (usuarios.nombreDeUsuarioTomado(nombreUsuario)) {
            throw new NombreDeUsuarioTomadoException(nombreUsuario);
        }
        if (usuarios.emailTomado(email)) {
            throw new EmailTomadoException(email);
        }

        Usuario usuario = new Usuario();
        usuario.setNombreUsuario(nombreUsuario);
        usuario.setEmail(email);
        usuario.setContrasenaHash(codificador.encode(contrasena));
        usuario.setRol(Rol.JUGADOR);
        usuarios.crear(usuario);

        return autenticar(usuario);
    }

    private Autenticacion autenticar(Usuario usuario) {
        TokenFirmado firmado = jwt.firmar(usuario.getId());
        return new Autenticacion(firmado.token(), firmado.expiraEn(), usuario);
    }
}
