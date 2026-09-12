package com.truchoprode.config;

import com.truchoprode.domain.UsuarioAutenticado;
import com.truchoprode.service.JwtService;
import com.truchoprode.service.UsuarioService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Traduce el header Authorization en un usuario autenticado.
 *
 * <p>Va a la base a buscar el usuario en vez de confiar en lo que dice el token. Cuesta una
 * consulta por request y compra dos cosas: el rol siempre fresco —promover a ADMIN con un UPDATE
 * toma efecto en el request siguiente, sin esperar a que venza el token— y que el token de un
 * usuario borrado deje de servir al instante.
 *
 * <p>Si algo no cierra no tira una excepcion: deja el contexto vacio y sigue. De ahi en adelante la
 * cadena de Spring Security se encarga de responder 401.
 */
public class FiltroJwt extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwt;
    private final UsuarioService usuarios;

    public FiltroJwt(JwtService jwt, UsuarioService usuarios) {
        this.jwt = jwt;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest pedido, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {

        String cabecera = pedido.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith(PREFIJO)) {
            jwt.usuarioDe(cabecera.substring(PREFIJO.length()))
                    .flatMap(usuarios::porId)
                    .ifPresent(usuario -> {
                        UsuarioAutenticado autenticado = new UsuarioAutenticado(
                                usuario.getId(), usuario.getNombreUsuario(), usuario.getRol());
                        var autoridades =
                                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol()));
                        SecurityContextHolder.getContext()
                                .setAuthentication(new UsernamePasswordAuthenticationToken(
                                        autenticado, null, autoridades));
                    });
        }

        cadena.doFilter(pedido, respuesta);
    }
}
