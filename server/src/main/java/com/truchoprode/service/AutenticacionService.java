package com.truchoprode.service;

import com.truchoprode.domain.Autenticacion;
import com.truchoprode.domain.Rol;
import com.truchoprode.domain.TokenFirmado;
import com.truchoprode.domain.Usuario;
import com.truchoprode.exception.CredencialesInvalidasException;
import com.truchoprode.exception.EmailTomadoException;
import com.truchoprode.exception.NombreDeUsuarioTomadoException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Las dos operaciones de la puerta de entrada: registrarse y loguearse. */
public class AutenticacionService {

    /**
     * Hash de descarte con el que se compara cuando el usuario no existe. Sin esto, el login
     * responderia mas rapido ante un usuario inexistente que ante una contrasena equivocada, y ese
     * tiempo delataria exactamente lo mismo que el mensaje de error se cuida de no decir.
     */
    private static final String SENUELO =
            "$2a$10$nUS9UM0NI.4EfCNrgeE8KOkiUQtgRhaVCXeLsqiXFC87ffju2cPDu";

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

    public Autenticacion login(String usuarioOEmail, String contrasena) {
        Usuario usuario = usuarios.porNombreOEmail(usuarioOEmail).orElse(null);

        if (usuario == null) {
            codificador.matches(contrasena, SENUELO);
            throw new CredencialesInvalidasException();
        }
        if (!codificador.matches(contrasena, usuario.getContrasenaHash())) {
            throw new CredencialesInvalidasException();
        }

        return autenticar(usuario);
    }

    private Autenticacion autenticar(Usuario usuario) {
        TokenFirmado firmado = jwt.firmar(usuario.getId());
        return new Autenticacion(firmado.token(), firmado.expiraEn(), usuario);
    }
}
