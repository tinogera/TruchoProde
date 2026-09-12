package com.truchoprode.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truchoprode.domain.Autenticacion;
import com.truchoprode.domain.Usuario;
import com.truchoprode.exception.CredencialesInvalidasException;
import com.truchoprode.mapper.UsuarioMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("Login")
class AutenticacionServiceLoginTest {

    /** Mismo doble que el test de registro: compara ignorando mayusculas, como los indices de V1. */
    private static final class MapperFalso implements UsuarioMapper {
        private final List<Usuario> guardados = new ArrayList<>();
        private long proximoId = 1;

        @Override
        public void insertar(Usuario usuario) {
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

    private void registrarSantino() {
        service.registrar("santino", "santino@ejemplo.com", "clave1234");
    }

    @Test
    @DisplayName("entra con el nombre de usuario")
    void entraConElNombreDeUsuario() {
        registrarSantino();

        Autenticacion resultado = service.login("santino", "clave1234");

        assertThat(resultado.token()).isNotBlank();
        assertThat(resultado.usuario().getNombreUsuario()).isEqualTo("santino");
    }

    @Test
    @DisplayName("entra con el mail")
    void entraConElMail() {
        registrarSantino();

        Autenticacion resultado = service.login("santino@ejemplo.com", "clave1234");

        assertThat(resultado.usuario().getNombreUsuario()).isEqualTo("santino");
    }

    @Test
    @DisplayName("entra sin importar las mayusculas del nombre")
    void entraIgnorandoMayusculas() {
        registrarSantino();

        assertThat(service.login("SanTino", "clave1234").token()).isNotBlank();
    }

    @Test
    @DisplayName("rechaza la contrasena incorrecta")
    void rechazaLaContrasenaIncorrecta() {
        registrarSantino();

        assertThatThrownBy(() -> service.login("santino", "otraclave"))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    @DisplayName("el usuario que no existe da el MISMO error que la contrasena mal")
    void elInexistenteDaElMismoError() {
        registrarSantino();

        assertThatThrownBy(() -> service.login("nadie", "clave1234"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(new CredencialesInvalidasException().getMessage());
    }

    @Test
    @DisplayName("el mensaje no dice cual de los dos datos fallo")
    void elMensajeNoDelataNada() {
        String mensaje = new CredencialesInvalidasException().getMessage();

        assertThat(mensaje).doesNotContainIgnoringCase("no existe");
        assertThat(new CredencialesInvalidasException().estadoHttp()).isEqualTo(401);
    }
}
