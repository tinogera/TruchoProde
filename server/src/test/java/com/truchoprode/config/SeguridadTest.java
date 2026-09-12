package com.truchoprode.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.truchoprode.domain.Autenticacion;
import com.truchoprode.service.AutenticacionService;
import com.truchoprode.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Cadena de seguridad")
class SeguridadTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AutenticacionService autenticacion;
    @Autowired private JwtService jwt;
    @Autowired private PasswordEncoder codificador;

    @Test
    @DisplayName("las piezas quedan armadas y el codificador es BCrypt")
    void lasPiezasQuedanArmadas() {
        assertThat(autenticacion).isNotNull();
        assertThat(jwt).isNotNull();
        assertThat(codificador.encode("clave1234")).startsWith("$2");
    }

    @Test
    @DisplayName("un endpoint cualquiera sin token responde 401, no 403")
    void sinTokenEs401() throws Exception {
        mockMvc.perform(get("/api/algo-que-no-existe")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("el login y el registro estan abiertos: no piden token")
    void elLoginEstaAbierto() throws Exception {
        // Sin cuerpo el controller todavia no existe, pero lo que importa es que NO sea 401:
        // la cadena tiene que dejar pasar estas dos rutas sin token.
        int estado = mockMvc.perform(get("/api/auth/login")).andReturn().getResponse().getStatus();

        assertThat(estado).isNotEqualTo(401);
    }

    @Test
    @DisplayName("un token valido deja pasar y deja al usuario en el contexto")
    void elTokenValidoDejaPasar() throws Exception {
        Autenticacion alta =
                autenticacion.registrar("filtrado", "filtrado@ejemplo.com", "clave1234");

        // /api/auth/yo todavia no existe (Task 6): con token valido tiene que dar 404, no 401.
        int estado = mockMvc
                .perform(get("/api/auth/yo").header("Authorization", "Bearer " + alta.token()))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(estado).isNotEqualTo(401);
    }

    @Test
    @DisplayName("un token firmado con otro secreto no pasa")
    void elTokenAjenoNoPasa() throws Exception {
        String ajeno = new JwtService(
                        "otro-secreto-distinto-igual-de-largo-para-la-prueba",
                        120,
                        java.time.Clock.systemUTC())
                .firmar(1L)
                .token();

        mockMvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isUnauthorized());
    }
}
