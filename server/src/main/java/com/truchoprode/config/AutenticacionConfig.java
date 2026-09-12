package com.truchoprode.config;

import com.truchoprode.mapper.UsuarioMapper;
import com.truchoprode.service.AutenticacionService;
import com.truchoprode.service.JwtService;
import com.truchoprode.service.UsuarioService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Arma las piezas de la autenticacion, con el mismo criterio que AvisosConfig. */
@Configuration
public class AutenticacionConfig {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder codificadorDeContrasenas() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtService jwtService(
            @Value("${truchoprode.jwt.secret}") String secreto,
            @Value("${truchoprode.jwt.expiracion-minutos}") int expiracionMinutos,
            Clock reloj) {
        return new JwtService(secreto, expiracionMinutos, reloj);
    }

    @Bean
    public UsuarioService usuarioService(UsuarioMapper mapper) {
        return new UsuarioService(mapper);
    }

    @Bean
    public AutenticacionService autenticacionService(
            UsuarioService usuarios, PasswordEncoder codificador, JwtService jwt) {
        return new AutenticacionService(usuarios, codificador, jwt);
    }
}
