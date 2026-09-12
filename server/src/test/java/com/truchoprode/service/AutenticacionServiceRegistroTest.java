package com.truchoprode.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truchoprode.domain.Autenticacion;
import com.truchoprode.domain.Rol;
import com.truchoprode.domain.Usuario;
import com.truchoprode.exception.EmailTomadoException;
import com.truchoprode.exception.NombreDeUsuarioTomadoException;
import com.truchoprode.mapper.UsuarioMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("Registro de un jugador")
class AutenticacionServiceRegistroTest {

    /** Guarda los usuarios en una lista y compara ignorando mayusculas, como los indices de V1. */
    private static final class MapperFalso implements UsuarioMapper {
        private final List<Usuario> guardados = new ArrayList<>();
        private long proximoId = 1;

        @Override
        public void insertar(Usuario usuario) {
            // No toca el rol a proposito: lo setea el service, y si el doble lo rellenara taparia
            // el caso de que la implementacion real se olvide de hacerlo.
            usuario.setId(proximoId++);
            guardados.add(usuario);
        }

        @Override
        public Usuario buscarPorId(Long id) {
            return guardados.stream().filter(u -> u.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public Usuario buscarPorNombreOEmail(String identificador) {
            return guardados.stream()
                    .filter(u -> igual(u.getNombreUsuario(), identificador)
                            || igual(u.getEmail(), identificador))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public boolean existeNombreDeUsuario(String nombreUsuario) {
            return guardados.stream().anyMatch(u -> igual(u.getNombreUsuario(), nombreUsuario));
        }

        @Override
        public boolean existeEmail(String email) {
            return guardados.stream().anyMatch(u -> igual(u.getEmail(), email));
        }

        private boolean igual(String uno, String otro) {
            return uno.toLowerCase(Locale.ROOT).equals(otro.toLowerCase(Locale.ROOT));
        }
    }

    private final MapperFalso mapper = new MapperFalso();
    private final PasswordEncoder codificador = new BCryptPasswordEncoder();

    private final AutenticacionService service = new AutenticacionService(
            new UsuarioService(mapper),
            codificador,
            new JwtService("un-secreto-de-prueba-largo-de-mas-de-32-bytes-seguro", 120,
                    Clock.systemUTC()));

    @Test
    @DisplayName("devuelve un token usable y el usuario creado")
    void devuelveTokenYUsuario() {
        Autenticacion resultado = service.registrar("santino", "santino@ejemplo.com", "clave1234");

        assertThat(resultado.token()).isNotBlank();
        assertThat(resultado.expiraEn()).isAfter(java.time.Instant.now());
        assertThat(resultado.usuario().getNombreUsuario()).isEqualTo("santino");
    }

    @Test
    @DisplayName("guarda la contrasena hasheada, nunca en claro")
    void guardaLaContrasenaHasheada() {
        service.registrar("santino", "santino@ejemplo.com", "clave1234");

        String guardado = mapper.guardados.get(0).getContrasenaHash();
        assertThat(guardado).isNotEqualTo("clave1234");
        assertThat(codificador.matches("clave1234", guardado)).isTrue();
    }

    @Test
    @DisplayName("el que se registra siempre nace JUGADOR")
    void siempreNaceJugador() {
        Autenticacion resultado = service.registrar("santino", "santino@ejemplo.com", "clave1234");

        assertThat(resultado.usuario().getRol()).isEqualTo(Rol.JUGADOR);
    }

    @Test
    @DisplayName("rechaza el nombre de usuario ya tomado")
    void rechazaElNombreTomado() {
        service.registrar("santino", "santino@ejemplo.com", "clave1234");

        assertThatThrownBy(() -> service.registrar("santino", "otro@ejemplo.com", "clave1234"))
                .isInstanceOf(NombreDeUsuarioTomadoException.class);
    }

    @Test
    @DisplayName("rechaza el nombre tomado aunque cambie la capitalizacion")
    void rechazaElNombreTomadoConOtraCapitalizacion() {
        service.registrar("tinogera", "tino@ejemplo.com", "clave1234");

        assertThatThrownBy(() -> service.registrar("TinoGera", "otro@ejemplo.com", "clave1234"))
                .isInstanceOf(NombreDeUsuarioTomadoException.class);
    }

    @Test
    @DisplayName("rechaza el mail ya registrado, ignorando mayusculas")
    void rechazaElMailRepetido() {
        service.registrar("santino", "santino@ejemplo.com", "clave1234");

        assertThatThrownBy(() -> service.registrar("otro", "SANTINO@ejemplo.com", "clave1234"))
                .isInstanceOf(EmailTomadoException.class);
    }

    @Test
    @DisplayName("los dos conflictos del registro responden 409")
    void losConflictosSon409() {
        assertThat(new NombreDeUsuarioTomadoException("x").estadoHttp()).isEqualTo(409);
        assertThat(new EmailTomadoException("x").estadoHttp()).isEqualTo(409);
    }
}
