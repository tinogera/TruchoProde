package com.truchoprode.controller;

import com.truchoprode.domain.UsuarioAutenticado;
import com.truchoprode.dto.LoginRequest;
import com.truchoprode.dto.RegistroRequest;
import com.truchoprode.dto.TokenResponse;
import com.truchoprode.dto.UsuarioResponse;
import com.truchoprode.service.AutenticacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Recibe HTTP y delega. Sin logica de negocio. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AutenticacionService autenticacion;

    public AuthController(AutenticacionService autenticacion) {
        this.autenticacion = autenticacion;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse registro(@Valid @RequestBody RegistroRequest cuerpo) {
        return TokenResponse.de(
                autenticacion.registrar(cuerpo.nombreUsuario(), cuerpo.email(),
                        cuerpo.contrasena()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest cuerpo) {
        return TokenResponse.de(autenticacion.login(cuerpo.usuarioOEmail(), cuerpo.contrasena()));
    }

    /**
     * Quien soy, segun el token. El usuario sale del contexto, nunca de un parametro: si el id
     * viniera del cliente, cualquiera podria pedir los datos de otro.
     */
    @GetMapping("/yo")
    public UsuarioResponse yo(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return UsuarioResponse.de(usuario);
    }
}
