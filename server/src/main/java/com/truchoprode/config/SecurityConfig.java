package com.truchoprode.config;

import com.truchoprode.service.JwtService;
import com.truchoprode.service.UsuarioService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * La cadena es stateless: no hay sesion ni cookie, el token viaja en el header en cada pedido.
 *
 * <p>CSRF queda apagado porque no aplica: el ataque se apoya en que el navegador mande la
 * credencial sola, y un header Authorization no se manda solo.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(
            HttpSecurity http,
            JwtService jwt,
            UsuarioService usuarios,
            @Value("${truchoprode.cors.origenes-permitidos}") String origenes)
            throws Exception {

        // Se construye con new y NO como @Bean: un Filter que es bean lo registra Boot tambien en
        // la cadena de servlets, fuera de la de Security, y correria en el lugar equivocado.
        FiltroJwt filtro = new FiltroJwt(jwt, usuarios);

        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(fuenteCors(origenes)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers("/api/auth/registro", "/api/auth/login")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(filtro, UsernamePasswordAuthenticationFilter.class)
                // Sin esto, un pedido sin token contesta 403. Lo correcto es 401: todavia no
                // sabemos quien es, no es que sepamos y no le alcance.
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }

    private CorsConfigurationSource fuenteCors(String origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origenes.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        return fuente;
    }
}
