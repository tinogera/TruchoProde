# Autenticación — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que un jugador pueda registrarse, loguearse con nombre de usuario o email, y que todo endpoint del backend sepa quién está pidiendo sin que el cliente pueda mentir sobre su identidad.

**Architecture:** JWT stateless con un filtro propio, sin `UserDetailsService`. El token lleva solo el `sub` con el `usuarioId`; el filtro resuelve el rol contra la base en cada request, así una promoción a ADMIN toma efecto en el request siguiente sin relogin. Los services son POJOs sin anotaciones, ensamblados con `@Bean` en una `@Configuration`, igual que la vertical de avisos.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Spring Security 7, jjwt 0.13.0, MyBatis 4.1.0, PostgreSQL 18.6, Flyway, Lombok, JUnit 5 + AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-12-autenticacion-design.md`

## Global Constraints

- Todo el código, los comentarios, los `@DisplayName` y los mensajes de commit van **en español**.
- Los commits van a nombre de tinogera. **Nunca** agregar `Co-Authored-By: Claude`, ni línea `Claude-Session:`, ni "Generated with Claude Code".
- Los comandos de Maven se corren **siempre con `./mvnw` desde `server/`**. `mvn` no está en el PATH. No usar `-o`/offline.
- Los tests de integración necesitan la base levantada: si aparece `Connection refused`, pedirle al usuario `podman start truchoprode-db` en una terminal del host. No es un bug del código.
- Acceso a datos con **MyBatis**, nunca JPA/Hibernate. Interfaz `@Mapper` en `com.truchoprode.mapper` y el XML en `src/main/resources/mapper/*.xml`.
- Flujo estricto `Controller → Service → Repository → Domain`. Ninguna capa se saltea, **el filtro de seguridad incluido**: habla con `UsuarioService`, no con `UsuarioMapper`.
- `com.truchoprode.domain` son entidades puras: Lombok sí, anotaciones de Spring/MyBatis/JPA no.
- Los services **no llevan `@Service`**: son POJOs con constructor, ensamblados como `@Bean` en una `@Configuration`. Patrón establecido en `config/AvisosConfig.java`.
- Los tests unitarios usan **dobles a mano** (clases estáticas privadas que implementan la interfaz), no Mockito. Patrón establecido en `service/AvisoDePrediccionServiceTest.java`.
- Los tests de integración llevan `@SpringBootTest`, `@ActiveProfiles("test")` y `@Transactional` (rollback al terminar), con `JdbcTemplate` para armar los datos.
- Aserciones con **AssertJ** (`assertThat`), nunca las de JUnit.
- El esquema **no se toca**: no hay migración nueva en esta vertical. `V1__esquema_inicial.sql` ya tiene todo. Y jamás se edita una migración ya aplicada.
- Ninguna credencial real entra en un archivo versionado. Al agregar una variable nueva hay que tocar los tres lugares: el `${...}` en `application.yml`, el valor en `server/.env` y el nombre en `server/.env.example`.
- Contraseña: mínimo 8, **máximo 72** caracteres. BCrypt ignora en silencio lo que pase de 72 bytes.
- Token: uno solo, **120 minutos**, sin refresh. Viaja en `Authorization: Bearer <token>`.
- El registro **siempre** crea rol `JUGADOR`. El rol no se acepta en el body.

### API verificada empíricamente (no cambiarla por lo que digan los tutoriales)

Estas firmas se compilaron y corrieron contra las dependencias reales del proyecto el 2026-09-12:

- jjwt 0.13 emite con `Jwts.builder().subject(...).issuedAt(...).expiration(...).signWith(clave).compact()` y lee con `Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload()`. **No** usar `Jwts.parserBuilder()` (API vieja, deprecada) ni `setSubject`/`setExpiration` (0.11).
- La clave se arma con `Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8))` y necesita un secreto de 32 bytes o más. El default de `application.yml` tiene 56.
- Al parsear, un token vencido tira `ExpiredJwtException`, uno con otra firma `SignatureException`, uno mal formado `MalformedJwtException` — las tres extienden `JwtException` — y un token vacío tira `IllegalArgumentException`, que **no** extiende `JwtException` y hay que capturar aparte.
- Boot 4 movió los packages de test: es `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, **no** `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` de Boot 3.
- Boot 4 usa **Jackson 3**, que cambió de package: el bean inyectable es
  `tools.jackson.databind.ObjectMapper`, **no** `com.fasterxml.jackson.databind.ObjectMapper`. Las
  dos versiones conviven en el classpath —Jackson 2 entra como transitiva de `jjwt-jackson`— pero la
  que Boot registra como bean es la 3, así que importar la de `com.fasterxml` falla con
  `NoSuchBeanDefinitionException`. En Jackson 3, `JsonNode.asText()` pasó a llamarse `asString()`.
- Spring Security 7 usa el DSL de lambdas: `csrf(csrf -> csrf.disable())`, `authorizeHttpRequests(a -> a.requestMatchers(...).permitAll().anyRequest().authenticated())`, `sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))`.

---

## Estructura de archivos

**Dominio** (`server/src/main/java/com/truchoprode/domain/`)
- `Rol.java` — enum `JUGADOR` / `ADMIN`. (Task 2)
- `Usuario.java` — entidad, Lombok `@Data`. (Task 2)
- `TokenFirmado.java` — record `(String token, Instant expiraEn)`. (Task 1)
- `Autenticacion.java` — record `(String token, Instant expiraEn, Usuario usuario)`, lo que devuelve registrar/login. (Task 3)
- `UsuarioAutenticado.java` — record `(Long id, String nombreUsuario, Rol rol)`, el principal del `SecurityContext`. (Task 5)

**Excepciones** (`server/src/main/java/com/truchoprode/exception/`) — package nuevo
- `TruchoProdeException.java` — abstracta, con `codigo()` y `estadoHttp()`. (Task 3)
- `ReglaDeNegocioException.java` — abstracta, `409`. (Task 3)
- `NoAutenticadoException.java` — abstracta, `401`. (Task 3)
- `NombreDeUsuarioTomadoException.java`, `EmailTomadoException.java`. (Task 3)
- `CredencialesInvalidasException.java`. (Task 4)

**Repositorio**
- `mapper/UsuarioMapper.java` + `src/main/resources/mapper/UsuarioMapper.xml`. (Task 2)

**Servicios** (`server/src/main/java/com/truchoprode/service/`)
- `JwtService.java` — firma y valida. No conoce base ni HTTP. (Task 1)
- `UsuarioService.java` — lee y crea usuarios. (Task 2)
- `AutenticacionService.java` — `registrar()` (Task 3) y `login()` (Task 4).

**Config** (`server/src/main/java/com/truchoprode/config/`)
- `AutenticacionConfig.java` — los `@Bean` de la vertical. (Task 5)
- `SecurityConfig.java` — la `SecurityFilterChain` y el CORS. (Task 5)
- `FiltroJwt.java` — `OncePerRequestFilter`. (Task 5)

**DTO y controller**
- `dto/RegistroRequest.java`, `dto/LoginRequest.java`, `dto/TokenResponse.java`, `dto/UsuarioResponse.java`. (Task 6)
- `controller/AuthController.java`. (Task 6)
- `controller/ManejadorDeErrores.java` — `@RestControllerAdvice`. (Task 6)

**Tests** (`server/src/test/java/com/truchoprode/`)
- `service/JwtServiceTest.java` (Task 1), `mapper/UsuarioMapperTest.java` (Task 2),
  `service/AutenticacionServiceRegistroTest.java` (Task 3), `service/AutenticacionServiceLoginTest.java` (Task 4),
  `config/SeguridadTest.java` (Task 5), `controller/AutenticacionEnHttpTest.java` (Task 6),
  `controller/DecisionesDeAutenticacionTest.java` (Task 7).

---

## Task 1: JwtService

**Files:**
- Create: `server/src/main/java/com/truchoprode/domain/TokenFirmado.java`
- Create: `server/src/main/java/com/truchoprode/service/JwtService.java`
- Test: `server/src/test/java/com/truchoprode/service/JwtServiceTest.java`

**Interfaces:**
- Consumes: nada (es la primera tarea).
- Produces:
  - `record TokenFirmado(String token, Instant expiraEn)`
  - `JwtService(String secreto, int expiracionMinutos, Clock reloj)`
  - `TokenFirmado JwtService.firmar(Long usuarioId)`
  - `Optional<Long> JwtService.usuarioDe(String token)`

El `Clock` se inyecta para que el vencimiento sea testeable: un test firma con un reloj de hace tres horas y el parser, que usa el reloj real, ve el token vencido. Sin eso, probar el vencimiento sería esperar dos horas.

- [ ] **Step 1: Escribir el test que falla**

`server/src/test/java/com/truchoprode/service/JwtServiceTest.java`:

```java
package com.truchoprode.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.truchoprode.domain.TokenFirmado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Firma y validacion de tokens")
class JwtServiceTest {

    private static final String SECRETO = "un-secreto-de-prueba-largo-de-mas-de-32-bytes-seguro";
    private static final String OTRO_SECRETO = "otro-secreto-distinto-igual-de-largo-para-la-prueba";

    private final JwtService jwt = new JwtService(SECRETO, 120, Clock.systemUTC());

    @Test
    @DisplayName("un token recien firmado devuelve el id del usuario")
    void elTokenDevuelveElUsuario() {
        TokenFirmado firmado = jwt.firmar(42L);

        assertThat(jwt.usuarioDe(firmado.token())).contains(42L);
    }

    @Test
    @DisplayName("el token dice cuando vence, 120 minutos despues de emitirse")
    void diceCuandoVence() {
        Instant emitido = Instant.parse("2026-09-12T18:00:00Z");
        JwtService conRelojFijo = new JwtService(SECRETO, 120, Clock.fixed(emitido, ZoneOffset.UTC));

        TokenFirmado firmado = conRelojFijo.firmar(42L);

        assertThat(firmado.expiraEn()).isEqualTo(emitido.plus(120, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("rechaza un token vencido")
    void rechazaElVencido() {
        Clock haceTresHoras =
                Clock.fixed(Instant.now().minus(3, ChronoUnit.HOURS), ZoneOffset.UTC);
        String vencido = new JwtService(SECRETO, 120, haceTresHoras).firmar(42L).token();

        assertThat(jwt.usuarioDe(vencido)).isEmpty();
    }

    @Test
    @DisplayName("rechaza un token firmado con otro secreto")
    void rechazaOtraFirma() {
        String ajeno = new JwtService(OTRO_SECRETO, 120, Clock.systemUTC()).firmar(42L).token();

        assertThat(jwt.usuarioDe(ajeno)).isEmpty();
    }

    @Test
    @DisplayName("rechaza basura sin explotar")
    void rechazaBasura() {
        assertThat(jwt.usuarioDe("no-es-un-token")).isEqualTo(Optional.empty());
        assertThat(jwt.usuarioDe("")).isEqualTo(Optional.empty());
        assertThat(jwt.usuarioDe(null)).isEqualTo(Optional.empty());
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `./mvnw test -Dtest=JwtServiceTest` (desde `server/`)
Expected: FALLA al compilar — no existen `JwtService` ni `TokenFirmado`.

- [ ] **Step 3: Escribir la implementación mínima**

`server/src/main/java/com/truchoprode/domain/TokenFirmado.java`:

```java
package com.truchoprode.domain;

import java.time.Instant;

/** Un token ya firmado y el momento en que deja de servir. */
public record TokenFirmado(String token, Instant expiraEn) {}
```

`server/src/main/java/com/truchoprode/service/JwtService.java`:

```java
package com.truchoprode.service;

import com.truchoprode.domain.TokenFirmado;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;

/**
 * Firma y valida los tokens. No conoce la base ni HTTP: solo sabe convertir un id de usuario en
 * un token y volver atras.
 *
 * <p>El token lleva unicamente el id en el "sub". El rol NO viaja adentro a proposito: lo lee el
 * filtro contra la base en cada request, asi una promocion a ADMIN toma efecto en el request
 * siguiente en vez de esperar a que el token venza.
 *
 * <p>El reloj se recibe en el constructor para que el vencimiento se pueda testear sin esperar.
 */
public class JwtService {

    private final SecretKey clave;
    private final int expiracionMinutos;
    private final Clock reloj;

    public JwtService(String secreto, int expiracionMinutos, Clock reloj) {
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expiracionMinutos = expiracionMinutos;
        this.reloj = reloj;
    }

    public TokenFirmado firmar(Long usuarioId) {
        Instant ahora = reloj.instant();
        Instant vence = ahora.plus(expiracionMinutos, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(vence))
                .signWith(clave)
                .compact();
        return new TokenFirmado(token, vence);
    }

    /**
     * Vacio si el token no sirve por cualquier motivo: firma ajena, vencido, mal formado o nulo.
     * Quien llama no necesita distinguir el caso; todos terminan en el mismo 401.
     */
    public Optional<Long> usuarioDe(String token) {
        try {
            Claims cuerpo = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(cuerpo.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException cubre el token nulo o vacio, que no es un JwtException.
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `./mvnw test -Dtest=JwtServiceTest`
Expected: PASA, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/truchoprode/domain/TokenFirmado.java \
        server/src/main/java/com/truchoprode/service/JwtService.java \
        server/src/test/java/com/truchoprode/service/JwtServiceTest.java
git commit -m "Firmar y validar tokens JWT con el id del usuario

El token lleva solo el id en el sub. El rol no viaja adentro a proposito: lo
lee el filtro contra la base, asi una promocion a ADMIN toma efecto en el
request siguiente sin esperar a que el token venza.

El reloj se inyecta en el constructor para poder testear el vencimiento sin
esperar dos horas: el test firma con un reloj de hace tres horas y el parser,
que usa el reloj real, ve el token vencido."
```

---

## Task 2: Acceso a usuarios (dominio, mapper y service)

**Files:**
- Create: `server/src/main/java/com/truchoprode/domain/Rol.java`
- Create: `server/src/main/java/com/truchoprode/domain/Usuario.java`
- Create: `server/src/main/java/com/truchoprode/mapper/UsuarioMapper.java`
- Create: `server/src/main/resources/mapper/UsuarioMapper.xml`
- Create: `server/src/main/java/com/truchoprode/service/UsuarioService.java`
- Test: `server/src/test/java/com/truchoprode/mapper/UsuarioMapperTest.java`

**Interfaces:**
- Consumes: nada de Task 1.
- Produces:
  - `enum Rol { JUGADOR, ADMIN }`
  - `Usuario` con `getId/setId(Long)`, `getNombreUsuario/setNombreUsuario(String)`, `getEmail/setEmail(String)`, `getContrasenaHash/setContrasenaHash(String)`, `getRol/setRol(Rol)`, `getCreadoEn/setCreadoEn(Instant)` (Lombok `@Data`)
  - `UsuarioMapper.insertar(Usuario)` — completa el `id` generado sobre el objeto recibido
  - `UsuarioMapper.buscarPorId(Long)` → `Usuario` o `null`
  - `UsuarioMapper.buscarPorNombreOEmail(String)` → `Usuario` o `null`
  - `UsuarioMapper.existeNombreDeUsuario(String)` → `boolean`
  - `UsuarioMapper.existeEmail(String)` → `boolean`
  - `UsuarioService(UsuarioMapper)`, con `porId(Long)` → `Optional<Usuario>`, `porNombreOEmail(String)` → `Optional<Usuario>`, `nombreDeUsuarioTomado(String)` → `boolean`, `emailTomado(String)` → `boolean`, `crear(Usuario)` → `void`

MyBatis mapea la columna `rol` (VARCHAR) al enum `Rol` con su `EnumTypeHandler` por defecto, que usa `name()`. Y `map-underscore-to-camel-case` ya está activado en `application.yml`, así que `nombre_usuario` cae solo en `nombreUsuario`.

- [ ] **Step 1: Escribir el test que falla**

`server/src/test/java/com/truchoprode/mapper/UsuarioMapperTest.java`:

```java
package com.truchoprode.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truchoprode.domain.Rol;
import com.truchoprode.domain.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corre contra la base real y deja todo como estaba: @Transactional hace rollback al terminar.
 * Necesita el contenedor levantado (podman start truchoprode-db).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Acceso a usuarios")
class UsuarioMapperTest {

    @Autowired private UsuarioMapper mapper;

    private Usuario nuevo(String nombre, String email) {
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario(nombre);
        usuario.setEmail(email);
        usuario.setContrasenaHash("$2a$10$hashfalsoquenoimportaparaestetest");
        usuario.setRol(Rol.JUGADOR);
        return usuario;
    }

    @Test
    @DisplayName("al insertar completa el id generado")
    void insertarCompletaElId() {
        Usuario usuario = nuevo("santino", "santino@ejemplo.com");

        mapper.insertar(usuario);

        assertThat(usuario.getId()).isNotNull();
    }

    @Test
    @DisplayName("lo recupera por id con todos sus datos")
    void loRecuperaPorId() {
        Usuario usuario = nuevo("pedro", "pedro@ejemplo.com");
        mapper.insertar(usuario);

        Usuario traido = mapper.buscarPorId(usuario.getId());

        assertThat(traido.getNombreUsuario()).isEqualTo("pedro");
        assertThat(traido.getEmail()).isEqualTo("pedro@ejemplo.com");
        assertThat(traido.getContrasenaHash()).isEqualTo(usuario.getContrasenaHash());
        assertThat(traido.getRol()).isEqualTo(Rol.JUGADOR);
        assertThat(traido.getCreadoEn()).isNotNull();
    }

    @Test
    @DisplayName("la misma consulta lo encuentra por nombre de usuario y por email")
    void loEncuentraPorNombreYPorEmail() {
        Usuario usuario = nuevo("lucia", "lucia@ejemplo.com");
        mapper.insertar(usuario);

        assertThat(mapper.buscarPorNombreOEmail("lucia").getId()).isEqualTo(usuario.getId());
        assertThat(mapper.buscarPorNombreOEmail("lucia@ejemplo.com").getId())
                .isEqualTo(usuario.getId());
    }

    @Test
    @DisplayName("lo encuentra sin importar las mayusculas")
    void loEncuentraIgnorandoMayusculas() {
        Usuario usuario = nuevo("tinogera", "tino@ejemplo.com");
        mapper.insertar(usuario);

        assertThat(mapper.buscarPorNombreOEmail("TinoGera").getId()).isEqualTo(usuario.getId());
        assertThat(mapper.buscarPorNombreOEmail("Tino@Ejemplo.COM").getId())
                .isEqualTo(usuario.getId());
    }

    @Test
    @DisplayName("devuelve null si no hay nadie con ese nombre ni ese mail")
    void devuelveNullSiNoExiste() {
        assertThat(mapper.buscarPorNombreOEmail("nadie")).isNull();
    }

    @Test
    @DisplayName("dice si el nombre o el mail ya estan tomados, ignorando mayusculas")
    void diceSiYaEstanTomados() {
        mapper.insertar(nuevo("ocupado", "ocupado@ejemplo.com"));

        assertThat(mapper.existeNombreDeUsuario("ocupado")).isTrue();
        assertThat(mapper.existeNombreDeUsuario("OcUpAdO")).isTrue();
        assertThat(mapper.existeNombreDeUsuario("libre")).isFalse();
        assertThat(mapper.existeEmail("OCUPADO@ejemplo.com")).isTrue();
        assertThat(mapper.existeEmail("libre@ejemplo.com")).isFalse();
    }

    @Test
    @DisplayName("la base rechaza el nombre repetido aunque cambie la capitalizacion")
    void laBaseRechazaElNombreRepetido() {
        mapper.insertar(nuevo("repetido", "uno@ejemplo.com"));

        assertThatThrownBy(() -> mapper.insertar(nuevo("REPETIDO", "dos@ejemplo.com")))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `./mvnw test -Dtest=UsuarioMapperTest`
Expected: FALLA al compilar — no existen `Rol`, `Usuario` ni `UsuarioMapper`.
Si en cambio falla con `Connection refused`, la base no está levantada: pedirle al usuario `podman start truchoprode-db`.

- [ ] **Step 3: Escribir la implementación mínima**

`server/src/main/java/com/truchoprode/domain/Rol.java`:

```java
package com.truchoprode.domain;

/**
 * Rol de plataforma, no de grupo: ADMIN es quien crea jornadas y carga partidos y resultados.
 * Los grupos no tienen roles propios.
 */
public enum Rol {
    JUGADOR,
    ADMIN
}
```

`server/src/main/java/com/truchoprode/domain/Usuario.java`:

```java
package com.truchoprode.domain;

import java.time.Instant;
import lombok.Data;

/** Un jugador registrado. El score no vive aca: es por grupo y vive en Miembro. */
@Data
public class Usuario {

    private Long id;
    private String nombreUsuario;
    private String email;
    private String contrasenaHash;
    private Rol rol;
    private Instant creadoEn;
}
```

`server/src/main/java/com/truchoprode/mapper/UsuarioMapper.java`:

```java
package com.truchoprode.mapper;

import com.truchoprode.domain.Usuario;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UsuarioMapper {

    /** Inserta y completa el id generado sobre el objeto recibido. */
    void insertar(Usuario usuario);

    /** Null si no existe. */
    Usuario buscarPorId(@Param("id") Long id);

    /** Una sola consulta para las dos formas de loguearse. Null si no existe. */
    Usuario buscarPorNombreOEmail(@Param("identificador") String identificador);

    boolean existeNombreDeUsuario(@Param("nombreUsuario") String nombreUsuario);

    boolean existeEmail(@Param("email") String email);
}
```

`server/src/main/resources/mapper/UsuarioMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.truchoprode.mapper.UsuarioMapper">

    <!--
      El rol se inserta explicitamente aunque la base ya tenga el default en JUGADOR. Si se dejara
      al default, el objeto que quedo en memoria volveria con rol null y la respuesta del registro
      diria rol: null en vez de JUGADOR.
    -->
    <insert id="insertar" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO usuario (nombre_usuario, email, contrasena_hash, rol)
        VALUES (#{nombreUsuario}, #{email}, #{contrasenaHash}, #{rol})
    </insert>

    <select id="buscarPorId" resultType="Usuario">
        SELECT id, nombre_usuario, email, contrasena_hash, rol, creado_en
          FROM usuario
         WHERE id = #{id}
    </select>

    <!--
      Las dos formas de loguearse en una sola consulta. El lower() no es cosmetico: es el mismo
      que usan los indices unicos de V1, asi que la busqueda los aprovecha en vez de recorrer
      la tabla entera.
    -->
    <select id="buscarPorNombreOEmail" resultType="Usuario">
        SELECT id, nombre_usuario, email, contrasena_hash, rol, creado_en
          FROM usuario
         WHERE lower(nombre_usuario) = lower(#{identificador})
            OR lower(email) = lower(#{identificador})
    </select>

    <select id="existeNombreDeUsuario" resultType="boolean">
        SELECT EXISTS (SELECT 1 FROM usuario WHERE lower(nombre_usuario) = lower(#{nombreUsuario}))
    </select>

    <select id="existeEmail" resultType="boolean">
        SELECT EXISTS (SELECT 1 FROM usuario WHERE lower(email) = lower(#{email}))
    </select>
</mapper>
```

`server/src/main/java/com/truchoprode/service/UsuarioService.java`:

```java
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
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `./mvnw test -Dtest=UsuarioMapperTest`
Expected: PASA, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/truchoprode/domain/Rol.java \
        server/src/main/java/com/truchoprode/domain/Usuario.java \
        server/src/main/java/com/truchoprode/mapper/UsuarioMapper.java \
        server/src/main/resources/mapper/UsuarioMapper.xml \
        server/src/main/java/com/truchoprode/service/UsuarioService.java \
        server/src/test/java/com/truchoprode/mapper/UsuarioMapperTest.java
git commit -m "Leer y crear usuarios con MyBatis

Una sola consulta resuelve las dos formas de loguearse: el lower() del WHERE
es el mismo de los indices unicos de V1, asi que los aprovecha en vez de
recorrer la tabla.

UsuarioService queda separado de la autenticacion porque el filtro de
seguridad necesita buscar usuarios pero no tiene nada que ver con registrar
ni loguear.

El test verifica ademas que la base rechaza el nombre repetido con otra
capitalizacion: es una garantia de V1, no algo que el codigo tenga que
recordar."
```

---

## Task 3: Excepciones y registro

**Files:**
- Create: `server/src/main/java/com/truchoprode/exception/TruchoProdeException.java`
- Create: `server/src/main/java/com/truchoprode/exception/ReglaDeNegocioException.java`
- Create: `server/src/main/java/com/truchoprode/exception/NoAutenticadoException.java`
- Create: `server/src/main/java/com/truchoprode/exception/NombreDeUsuarioTomadoException.java`
- Create: `server/src/main/java/com/truchoprode/exception/EmailTomadoException.java`
- Create: `server/src/main/java/com/truchoprode/domain/Autenticacion.java`
- Create: `server/src/main/java/com/truchoprode/service/AutenticacionService.java`
- Test: `server/src/test/java/com/truchoprode/service/AutenticacionServiceRegistroTest.java`

**Interfaces:**
- Consumes: `JwtService.firmar(Long)` → `TokenFirmado` (Task 1); `UsuarioService` completo y `Usuario`, `Rol` (Task 2).
- Produces:
  - `abstract class TruchoProdeException extends RuntimeException` con `String codigo()` y `int estadoHttp()` abstractos
  - `abstract class ReglaDeNegocioException extends TruchoProdeException` con `estadoHttp()` = 409
  - `abstract class NoAutenticadoException extends TruchoProdeException` con `estadoHttp()` = 401
  - `NombreDeUsuarioTomadoException(String nombreUsuario)`, código `NOMBRE_DE_USUARIO_TOMADO`
  - `EmailTomadoException(String email)`, código `EMAIL_TOMADO`
  - `record Autenticacion(String token, Instant expiraEn, Usuario usuario)`
  - `AutenticacionService(UsuarioService, PasswordEncoder, JwtService)`
  - `Autenticacion AutenticacionService.registrar(String nombreUsuario, String email, String contrasena)`

`ReglaDeNegocioException` devuelve `409` porque son conflictos con el estado actual del recurso. Las verticales que vengan después pueden sobreescribir `estadoHttp()` en una hija si les corresponde otro código.

- [ ] **Step 1: Escribir el test que falla**

`server/src/test/java/com/truchoprode/service/AutenticacionServiceRegistroTest.java`:

```java
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
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `./mvnw test -Dtest=AutenticacionServiceRegistroTest`
Expected: FALLA al compilar — no existen las excepciones, `Autenticacion` ni `AutenticacionService`.

- [ ] **Step 3: Escribir la implementación mínima**

`server/src/main/java/com/truchoprode/exception/TruchoProdeException.java`:

```java
package com.truchoprode.exception;

/**
 * Raiz de los errores propios del dominio. Cada una sabe dos cosas: un codigo estable que el
 * frontend puede usar para decidir que mostrar, y el estado HTTP que le corresponde.
 */
public abstract class TruchoProdeException extends RuntimeException {

    protected TruchoProdeException(String mensaje) {
        super(mensaje);
    }

    public abstract String codigo();

    public abstract int estadoHttp();
}
```

`server/src/main/java/com/truchoprode/exception/ReglaDeNegocioException.java`:

```java
package com.truchoprode.exception;

/**
 * Se pidio algo que choca con el estado actual: de ahi el 409. Una hija puede sobreescribir el
 * estado si le corresponde otro.
 */
public abstract class ReglaDeNegocioException extends TruchoProdeException {

    protected ReglaDeNegocioException(String mensaje) {
        super(mensaje);
    }

    @Override
    public int estadoHttp() {
        return 409;
    }
}
```

`server/src/main/java/com/truchoprode/exception/NoAutenticadoException.java`:

```java
package com.truchoprode.exception;

/**
 * "No se quien sos": 401. Es distinto de NoAutorizadoException, que sera el 403 de "se quien sos
 * y no te alcanza". Confundir las dos es el error clasico de esta parte.
 */
public abstract class NoAutenticadoException extends TruchoProdeException {

    protected NoAutenticadoException(String mensaje) {
        super(mensaje);
    }

    @Override
    public int estadoHttp() {
        return 401;
    }
}
```

`server/src/main/java/com/truchoprode/exception/NombreDeUsuarioTomadoException.java`:

```java
package com.truchoprode.exception;

/**
 * A diferencia del login, el registro si dice cual de los dos datos choca. No es una filtracion:
 * en TruchoProde los nombres de usuario son publicos por diseno, porque a un grupo se entra
 * escribiendo el nombre de alguien. Sin este mensaje el registro seria inusable.
 */
public class NombreDeUsuarioTomadoException extends ReglaDeNegocioException {

    public NombreDeUsuarioTomadoException(String nombreUsuario) {
        super("El nombre de usuario '" + nombreUsuario + "' ya esta tomado");
    }

    @Override
    public String codigo() {
        return "NOMBRE_DE_USUARIO_TOMADO";
    }
}
```

`server/src/main/java/com/truchoprode/exception/EmailTomadoException.java`:

```java
package com.truchoprode.exception;

public class EmailTomadoException extends ReglaDeNegocioException {

    public EmailTomadoException(String email) {
        super("Ya hay una cuenta registrada con el mail '" + email + "'");
    }

    @Override
    public String codigo() {
        return "EMAIL_TOMADO";
    }
}
```

`server/src/main/java/com/truchoprode/domain/Autenticacion.java`:

```java
package com.truchoprode.domain;

import java.time.Instant;

/** Lo que devuelven el registro y el login: el token, cuando vence, y de quien es. */
public record Autenticacion(String token, Instant expiraEn, Usuario usuario) {}
```

`server/src/main/java/com/truchoprode/service/AutenticacionService.java`:

```java
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
```

**Ojo:** el chequeo previo de "ya está tomado" no reemplaza al índice único de V1, lo complementa. Dos registros simultáneos con el mismo nombre pueden pasar los dos chequeos y uno va a chocar contra la base. Está bien: la base es la que garantiza la unicidad, y el chequeo existe para dar un `409` claro en el caso normal en vez de un error de constraint.

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `./mvnw test -Dtest=AutenticacionServiceRegistroTest`
Expected: PASA, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/truchoprode/exception/ \
        server/src/main/java/com/truchoprode/domain/Autenticacion.java \
        server/src/main/java/com/truchoprode/service/AutenticacionService.java \
        server/src/test/java/com/truchoprode/service/AutenticacionServiceRegistroTest.java
git commit -m "Registrar un jugador, siempre con rol JUGADOR

El rol no es parametro del registro: nace JUGADOR y el primer ADMIN se
promueve con un UPDATE a mano, asi ningun endpoint puede regalar privilegios.

Estrena el package exception con la jerarquia que el diagrama de clases ya
tenia disenada, y le agrega la rama NoAutenticadoException (401), que es
distinta de NoAutorizadoException (403): una es 'no se quien sos' y la otra
'se quien sos y no te alcanza'.

El chequeo de 'ya esta tomado' no reemplaza al indice unico de V1: esta para
dar un 409 claro en el caso normal. La unicidad la sigue garantizando la base."
```

---

## Task 4: Login

**Files:**
- Create: `server/src/main/java/com/truchoprode/exception/CredencialesInvalidasException.java`
- Modify: `server/src/main/java/com/truchoprode/service/AutenticacionService.java` (agregar `login`)
- Test: `server/src/test/java/com/truchoprode/service/AutenticacionServiceLoginTest.java`

**Interfaces:**
- Consumes: todo lo de Task 3.
- Produces:
  - `CredencialesInvalidasException()` — sin parámetros, código `CREDENCIALES_INVALIDAS`, estado `401`
  - `Autenticacion AutenticacionService.login(String usuarioOEmail, String contrasena)`

- [ ] **Step 1: Escribir el test que falla**

`server/src/test/java/com/truchoprode/service/AutenticacionServiceLoginTest.java`:

```java
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
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `./mvnw test -Dtest=AutenticacionServiceLoginTest`
Expected: FALLA al compilar — no existen `CredencialesInvalidasException` ni `login`.

- [ ] **Step 3: Escribir la implementación mínima**

`server/src/main/java/com/truchoprode/exception/CredencialesInvalidasException.java`:

```java
package com.truchoprode.exception;

/**
 * Un solo error para los dos casos —el usuario no existe y la contrasena esta mal— y a proposito:
 * si fueran distintos, el login serviria para averiguar que cuentas existen.
 */
public class CredencialesInvalidasException extends NoAutenticadoException {

    public CredencialesInvalidasException() {
        super("Usuario o contrasena incorrectos");
    }

    @Override
    public String codigo() {
        return "CREDENCIALES_INVALIDAS";
    }
}
```

En `AutenticacionService`, agregar el import de `CredencialesInvalidasException`, la constante del
señuelo y el método `login` (el resto de la clase queda igual):

```java
    /**
     * Hash de descarte con el que se compara cuando el usuario no existe. Sin esto, el login
     * responderia mas rapido ante un usuario inexistente que ante una contrasena equivocada, y ese
     * tiempo delataria exactamente lo mismo que el mensaje de error se cuida de no decir.
     */
    private static final String SENUELO =
            "$2a$10$nUS9UM0NI.4EfCNrgeE8KOkiUQtgRhaVCXeLsqiXFC87ffju2cPDu";

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
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `./mvnw test -Dtest=AutenticacionServiceLoginTest`
Expected: PASA, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/truchoprode/exception/CredencialesInvalidasException.java \
        server/src/main/java/com/truchoprode/service/AutenticacionService.java \
        server/src/test/java/com/truchoprode/service/AutenticacionServiceLoginTest.java
git commit -m "Loguearse con nombre de usuario o mail, indistinto

Un solo error para los dos casos, usuario inexistente y contrasena mal: si
fueran distintos, el login serviria para averiguar que cuentas existen.

Por el mismo motivo compara contra un hash de descarte cuando el usuario no
existe. Sin eso el login responderia mas rapido ante un usuario inexistente
que ante una contrasena equivocada, y ese tiempo delataria justo lo que el
mensaje se cuida de no decir."
```

---

## Task 5: El filtro y la cadena de seguridad

**Files:**
- Create: `server/src/main/java/com/truchoprode/domain/UsuarioAutenticado.java`
- Create: `server/src/main/java/com/truchoprode/config/FiltroJwt.java`
- Create: `server/src/main/java/com/truchoprode/config/AutenticacionConfig.java`
- Create: `server/src/main/java/com/truchoprode/config/SecurityConfig.java`
- Test: `server/src/test/java/com/truchoprode/config/SeguridadTest.java`

**Interfaces:**
- Consumes: `JwtService` (Task 1), `UsuarioService` (Task 2), `AutenticacionService` (Tasks 3-4).
- Produces:
  - `record UsuarioAutenticado(Long id, String nombreUsuario, Rol rol)` — el principal del `SecurityContext`
  - `FiltroJwt(JwtService, UsuarioService)` extends `OncePerRequestFilter`
  - Beans: `PasswordEncoder`, `Clock`, `JwtService`, `UsuarioService`, `AutenticacionService`, `SecurityFilterChain`

La spec decía que `UsuarioAutenticado` llevara `id` y `rol`; le agrego `nombreUsuario` porque el
filtro ya trajo el usuario completo de la base, así que es gratis, y `GET /api/auth/yo` lo necesita
para responder sin volver a consultar.

**`FiltroJwt` no se declara como `@Bean`.** Si un `Filter` es un bean, Boot lo registra además en la
cadena de servlets, fuera de la de Security, y se ejecuta en el lugar equivocado. Se construye con
`new` dentro del método que arma la `SecurityFilterChain`.

- [ ] **Step 1: Escribir el test que falla**

`server/src/test/java/com/truchoprode/config/SeguridadTest.java`:

```java
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
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `./mvnw test -Dtest=SeguridadTest`
Expected: FALLA al compilar — no existen `UsuarioAutenticado`, `FiltroJwt`, `AutenticacionConfig` ni `SecurityConfig`.

- [ ] **Step 3: Escribir la implementación mínima**

`server/src/main/java/com/truchoprode/domain/UsuarioAutenticado.java`:

```java
package com.truchoprode.domain;

/**
 * Quien esta haciendo el pedido, ya verificado. Es lo que los controllers reciben con
 * @AuthenticationPrincipal, y la razon por la que el id del usuario nunca llega desde el cliente:
 * si viniera en el cuerpo o en la URL, cualquiera podria predecir o transferir en nombre de otro.
 */
public record UsuarioAutenticado(Long id, String nombreUsuario, Rol rol) {}
```

`server/src/main/java/com/truchoprode/config/FiltroJwt.java`:

```java
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
 * <p>Si algo no cierra no tira una excepcion: deja el contexto vacio y sigue. De ahi en adelante
 * la cadena de Spring Security se encarga de responder 401.
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
```

`server/src/main/java/com/truchoprode/config/AutenticacionConfig.java`:

```java
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
```

`server/src/main/java/com/truchoprode/config/SecurityConfig.java`:

```java
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
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `./mvnw test -Dtest=SeguridadTest`
Expected: PASA, 5 tests.

- [ ] **Step 5: Correr toda la suite para confirmar que no se rompió nada**

Run: `./mvnw test`
Expected: `BUILD SUCCESS`. Los 31 tests de avisos siguen pasando, más los nuevos de esta vertical.

La cadena ahora exige token en todo lo que no sea `/api/auth/**`, así que si algún test de avisos
tocara HTTP empezaría a dar 401 — no lo hace, ninguno usa `MockMvc`, pero conviene confirmarlo acá.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/java/com/truchoprode/domain/UsuarioAutenticado.java \
        server/src/main/java/com/truchoprode/config/FiltroJwt.java \
        server/src/main/java/com/truchoprode/config/AutenticacionConfig.java \
        server/src/main/java/com/truchoprode/config/SecurityConfig.java \
        server/src/test/java/com/truchoprode/config/SeguridadTest.java
git commit -m "Autenticar cada pedido con el token, contra la base

El filtro va a la base a buscar el usuario en vez de confiar en lo que dice
el token. Cuesta una consulta por request y compra dos cosas: el rol siempre
fresco, asi promover a ADMIN con un UPDATE toma efecto en el request
siguiente, y que el token de un usuario borrado deje de servir al instante.

La cadena es stateless y CSRF queda apagado porque no aplica: ese ataque se
apoya en que el navegador mande la credencial sola, y un header Authorization
no se manda solo.

Dos detalles que se pagan caro si se hacen distinto: el filtro se construye
con new y no como @Bean, porque un Filter que es bean lo registra Boot tambien
fuera de la cadena de Security; y el authenticationEntryPoint hace que un
pedido sin token responda 401 en vez del 403 que da por defecto."
```

---

## Task 6: Los endpoints

**Files:**
- Create: `server/src/main/java/com/truchoprode/dto/RegistroRequest.java`
- Create: `server/src/main/java/com/truchoprode/dto/LoginRequest.java`
- Create: `server/src/main/java/com/truchoprode/dto/UsuarioResponse.java`
- Create: `server/src/main/java/com/truchoprode/dto/TokenResponse.java`
- Create: `server/src/main/java/com/truchoprode/controller/AuthController.java`
- Create: `server/src/main/java/com/truchoprode/controller/ManejadorDeErrores.java`
- Modify: `server/src/main/resources/application.yml` (activar `problemdetails`)
- Delete: `server/src/main/java/com/truchoprode/controller/.gitkeep`, `server/src/main/java/com/truchoprode/dto/.gitkeep`
- Test: `server/src/test/java/com/truchoprode/controller/AutenticacionEnHttpTest.java`

**Interfaces:**
- Consumes: `AutenticacionService.registrar/login` → `Autenticacion` (Tasks 3-4), `UsuarioAutenticado` (Task 5), `TruchoProdeException` (Task 3).
- Produces: los tres endpoints de la spec.

- [ ] **Step 1: Escribir el test que falla**

`server/src/test/java/com/truchoprode/controller/AutenticacionEnHttpTest.java`:

```java
package com.truchoprode.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Registro y login por HTTP")
class AutenticacionEnHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;

    private String cuerpoDeRegistro(String nombre, String email, String contrasena)
            throws Exception {
        return json.writeValueAsString(
                java.util.Map.of("nombreUsuario", nombre, "email", email,
                        "contrasena", contrasena));
    }

    private String registrar(String nombre) throws Exception {
        String respuesta = mockMvc
                .perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRegistro(nombre, nombre + "@ejemplo.com", "clave1234")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(respuesta).get("token").asString();
    }

    @Test
    @DisplayName("el registro devuelve 201 con el token y el usuario")
    void elRegistroDevuelveToken() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRegistro("santino", "santino@ejemplo.com", "clave1234")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiraEn").isNotEmpty())
                .andExpect(jsonPath("$.usuario.nombreUsuario").value("santino"))
                .andExpect(jsonPath("$.usuario.rol").value("JUGADOR"));
    }

    @Test
    @DisplayName("el registro nunca devuelve el hash de la contrasena")
    void elRegistroNoFiltraElHash() throws Exception {
        String respuesta = mockMvc
                .perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRegistro("discreto", "discreto@ejemplo.com", "clave1234")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(respuesta)
                .doesNotContain("contrasena")
                .doesNotContain("$2a$");
    }

    @Test
    @DisplayName("el nombre repetido da 409 con su codigo")
    void elNombreRepetidoDa409() throws Exception {
        registrar("repetido");

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRegistro("repetido", "otro@ejemplo.com", "clave1234")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOMBRE_DE_USUARIO_TOMADO"));
    }

    @Test
    @DisplayName("el cuerpo invalido da 400")
    void elCuerpoInvalidoDa400() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRegistro("x", "no-es-un-mail", "corta")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("la contrasena de mas de 72 caracteres da 400 en vez de recortarse callada")
    void laContrasenaLarguisimaDa400() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRegistro("largo", "largo@ejemplo.com", "a".repeat(73))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el login anda con el nombre y con el mail")
    void elLoginAndaConLosDos() throws Exception {
        registrar("santino");

        for (String identificador : new String[] {"santino", "santino@ejemplo.com"}) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(java.util.Map.of(
                                    "usuarioOEmail", identificador, "contrasena", "clave1234"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }
    }

    @Test
    @DisplayName("las credenciales mal dan 401 sin decir cual de los dos fallo")
    void lasCredencialesMalDan401() throws Exception {
        registrar("santino");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "usuarioOEmail", "santino", "contrasena", "otraclave"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    @DisplayName("yo sin token da 401")
    void yoSinTokenDa401() throws Exception {
        mockMvc.perform(get("/api/auth/yo")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("yo con token devuelve quien soy")
    void yoConTokenDiceQuienSoy() throws Exception {
        String token = registrar("santino");

        mockMvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreUsuario").value("santino"))
                .andExpect(jsonPath("$.rol").value("JUGADOR"));
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `./mvnw test -Dtest=AutenticacionEnHttpTest`
Expected: FALLA al compilar — no existen los DTOs ni el controller.

- [ ] **Step 3: Escribir la implementación mínima**

`server/src/main/java/com/truchoprode/dto/RegistroRequest.java`:

```java
package com.truchoprode.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El rol no esta ni como campo: todo el que se registra nace JUGADOR y no hay forma de pedir otra
 * cosa desde afuera.
 */
public record RegistroRequest(
        @NotBlank
        @Size(min = 3, max = 30)
        @Pattern(
                regexp = "[A-Za-z0-9._]+",
                message = "solo letras, numeros, punto y guion bajo, sin espacios")
        String nombreUsuario,
        @NotBlank @Email @Size(max = 255) String email,
        // El maximo de 72 no es arbitrario: BCrypt ignora en silencio lo que pase de 72 bytes, asi
        // que sin el tope una contrasena larguisima se recortaria sin avisarle a nadie.
        @NotBlank @Size(min = 8, max = 72) String contrasena) {}
```

`server/src/main/java/com/truchoprode/dto/LoginRequest.java`:

```java
package com.truchoprode.dto;

import jakarta.validation.constraints.NotBlank;

/** Un solo campo para las dos formas de entrar: nombre de usuario o mail. */
public record LoginRequest(@NotBlank String usuarioOEmail, @NotBlank String contrasena) {}
```

`server/src/main/java/com/truchoprode/dto/UsuarioResponse.java`:

```java
package com.truchoprode.dto;

import com.truchoprode.domain.Rol;
import com.truchoprode.domain.Usuario;
import com.truchoprode.domain.UsuarioAutenticado;

/** Lo unico del usuario que sale hacia afuera. El hash no esta ni como campo. */
public record UsuarioResponse(Long id, String nombreUsuario, Rol rol) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombreUsuario(), usuario.getRol());
    }

    public static UsuarioResponse de(UsuarioAutenticado usuario) {
        return new UsuarioResponse(usuario.id(), usuario.nombreUsuario(), usuario.rol());
    }
}
```

`server/src/main/java/com/truchoprode/dto/TokenResponse.java`:

```java
package com.truchoprode.dto;

import com.truchoprode.domain.Autenticacion;
import java.time.Instant;

/**
 * expiraEn es el instante de vencimiento en ISO-8601 UTC, no una cantidad de minutos: asi el front
 * lo compara contra el reloj del navegador sin tener que saber cuando se emitio el token.
 */
public record TokenResponse(String token, Instant expiraEn, UsuarioResponse usuario) {

    public static TokenResponse de(Autenticacion autenticacion) {
        return new TokenResponse(
                autenticacion.token(),
                autenticacion.expiraEn(),
                UsuarioResponse.de(autenticacion.usuario()));
    }
}
```

`server/src/main/java/com/truchoprode/controller/AuthController.java`:

```java
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
        return TokenResponse.de(
                autenticacion.login(cuerpo.usuarioOEmail(), cuerpo.contrasena()));
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
```

`server/src/main/java/com/truchoprode/controller/ManejadorDeErrores.java`:

```java
package com.truchoprode.controller;

import com.truchoprode.exception.TruchoProdeException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los errores del dominio a ProblemDetail. El "codigo" es lo que el frontend mira para
 * decidir que mostrar: el texto del mensaje puede cambiar, el codigo no.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(TruchoProdeException.class)
    public ProblemDetail deDominio(TruchoProdeException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(e.estadoHttp()), e.getMessage());
        problema.setProperty("codigo", e.codigo());
        return problema;
    }
}
```

En `server/src/main/resources/application.yml`, agregar bajo `spring:` (respetando la indentación
de dos espacios que ya usa el archivo):

```yaml
  # Devuelve los errores de validacion como ProblemDetail (RFC 7807) en vez del formato viejo.
  mvc:
    problemdetails:
      enabled: true
```

Y borrar los `.gitkeep` de las carpetas que ahora tienen código:

```bash
git rm server/src/main/java/com/truchoprode/controller/.gitkeep \
       server/src/main/java/com/truchoprode/dto/.gitkeep
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `./mvnw test -Dtest=AutenticacionEnHttpTest`
Expected: PASA, 9 tests.

- [ ] **Step 5: Correr toda la suite**

Run: `./mvnw test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/java/com/truchoprode/dto/ \
        server/src/main/java/com/truchoprode/controller/ \
        server/src/main/resources/application.yml \
        server/src/test/java/com/truchoprode/controller/AutenticacionEnHttpTest.java
git commit -m "Exponer registro, login y quien-soy por HTTP

Tres endpoints: POST /api/auth/registro devuelve 201 con el token, POST
/api/auth/login lo devuelve con 200, y GET /api/auth/yo dice quien sos segun
el token.

El usuario de /yo sale del contexto de seguridad, nunca de un parametro: si
el id viniera del cliente, cualquiera podria pedir los datos de otro.

El rol no existe como campo en RegistroRequest, asi no hay forma de pedir
ADMIN desde afuera. Y la contrasena tiene tope de 72 porque BCrypt ignora en
silencio lo que pase de ese largo: mejor un 400 que un recorte invisible.

Los errores del dominio salen como ProblemDetail con un campo codigo estable,
que es lo que el frontend va a mirar en vez del texto del mensaje."
```

---

## Task 7: Blindar las decisiones y actualizar la documentación

**Files:**
- Test: `server/src/test/java/com/truchoprode/controller/DecisionesDeAutenticacionTest.java`
- Modify: `CLAUDE.md`
- Modify: `Doc/TruchoProde_DiagramaClases.md`

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: nada de código nuevo.

Los dos tests de esta tarea no prueban plomería: prueban las dos decisiones de diseño que más fácil
se rompen sin querer más adelante.

- [ ] **Step 1: Escribir el test que falla**

`server/src/test/java/com/truchoprode/controller/DecisionesDeAutenticacionTest.java`:

```java
package com.truchoprode.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.truchoprode.domain.Autenticacion;
import com.truchoprode.service.AutenticacionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estos dos tests existen para que las decisiones no se deshagan sin que nadie se de cuenta. Si
 * alguien "optimiza" el filtro metiendo el rol adentro del token, el segundo se pone rojo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Las decisiones de la autenticacion")
class DecisionesDeAutenticacionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AutenticacionService autenticacion;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("el token de un usuario borrado deja de servir")
    void elTokenDeUnBorradoNoSirve() throws Exception {
        Autenticacion alta = autenticacion.registrar("fantasma", "fantasma@ejemplo.com",
                "clave1234");
        jdbc.update("DELETE FROM usuario WHERE id = ?", alta.usuario().getId());

        mockMvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + alta.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("el rol se lee de la base: promover a ADMIN no necesita un token nuevo")
    void elRolSaleDeLaBase() throws Exception {
        Autenticacion alta = autenticacion.registrar("asciende", "asciende@ejemplo.com",
                "clave1234");
        String tokenDeCuandoEraJugador = alta.token();

        mockMvc.perform(get("/api/auth/yo")
                        .header("Authorization", "Bearer " + tokenDeCuandoEraJugador))
                .andExpect(jsonPath("$.rol").value("JUGADOR"));

        jdbc.update("UPDATE usuario SET rol = 'ADMIN' WHERE id = ?", alta.usuario().getId());

        // El MISMO token de antes, sin volver a loguearse.
        mockMvc.perform(get("/api/auth/yo")
                        .header("Authorization", "Bearer " + tokenDeCuandoEraJugador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }
}
```

- [ ] **Step 2: Correr el test**

Run: `./mvnw test -Dtest=DecisionesDeAutenticacionTest`
Expected: PASA, 2 tests. Si el segundo falla diciendo `JUGADOR` donde espera `ADMIN`, el filtro está
sacando el rol del token en vez de la base: eso es el bug que este test existe para atrapar.

- [ ] **Step 3: Actualizar el CLAUDE.md**

Tres cambios:

1. En la lista de packages de "Arquitectura backend", agregar la línea del package nuevo:

```markdown
- **Exception** (`...exception`): la jerarquía de errores del dominio. Cada una sabe su `codigo()`
  estable y su `estadoHttp()`. `ManejadorDeErrores` las traduce a `ProblemDetail`.
```

2. En la sección "Rol de administrador (confirmado)", agregar cómo nace el primer ADMIN:

```markdown
- El registro **siempre** crea `JUGADOR`: el rol no es un campo del request. El primer ADMIN se
  promueve a mano, con la base levantada:

  ```sql
  UPDATE usuario SET rol = 'ADMIN' WHERE nombre_usuario = 'tinogera';
  ```

  Toma efecto en el request siguiente, sin volver a loguearse, porque el filtro lee el rol de la
  base y no del token.
```

3. En "Estado del proyecto", reemplazar exactamente este texto:

```markdown
**Todavía no existe:** nada de autenticación, ni el CRUD de grupos, jornadas, partidos, predicciones y
transferencias. No hay un solo controller. El `AvisoMapper` es el único mapper.
```

   por:

```markdown
**Todavía no existe:** el CRUD de grupos, jornadas, partidos, predicciones y transferencias. Los
únicos endpoints son los tres de `/api/auth`.
```

   Y agregar un ítem a la lista de "Hecho y verificado":

```markdown
- La autenticación: `JwtService`, `UsuarioService`, `AutenticacionService`, `UsuarioMapper` (+ XML),
  `FiltroJwt`, `SecurityConfig`, `AutenticacionConfig`, el package `exception` y `AuthController`
  con `/api/auth/registro`, `/api/auth/login` y `/api/auth/yo`.
```

   Ajustar además el número en `**Hecho y verificado** (./mvnw test → 31 tests, ...)` al total que
   imprima el `./mvnw test` del Step 5. **No copiar un número de este plan: poner el que salga.**

- [ ] **Step 4: Actualizar el diagrama de clases**

En `Doc/TruchoProde_DiagramaClases.md`, sección "2. Capas de aplicación", cambiar estas cinco firmas
(están todas en el mismo bloque `mermaid`, en las clases de controller):

| Dice | Pasa a decir |
|---|---|
| `+guardar(Long usuarioId, PrediccionRequest cuerpo) PrediccionResponse` | `+guardar(UsuarioAutenticado usuario, PrediccionRequest cuerpo) PrediccionResponse` |
| `+deJornada(Long usuarioId, Long jornadaId) List~PrediccionResponse~` | `+deJornada(UsuarioAutenticado usuario, Long jornadaId) List~PrediccionResponse~` |
| `+transferir(Long emisorId, TransferenciaRequest cuerpo) TransferenciaResponse` | `+transferir(UsuarioAutenticado emisor, TransferenciaRequest cuerpo) TransferenciaResponse` |
| `+historialPropio(Long grupoId, Long usuarioId) List~TransferenciaResponse~` | `+historialPropio(Long grupoId, UsuarioAutenticado usuario) List~TransferenciaResponse~` |
| `+ranking(Long grupoId, Long solicitanteId) List~FilaRanking~` | `+ranking(Long grupoId, UsuarioAutenticado solicitante) List~FilaRanking~` |

Las firmas de los **services** no cambian: siguen recibiendo `Long usuarioId`. El que traduce el
principal a un id es el controller; los services no tienen por qué saber que existe HTTP.

Agregar en ese mismo bloque la nota que explica el cambio:

```
    note for PrediccionController "El usuario sale del token, nunca de un parametro: si el id lo mandara el cliente, cualquiera podria predecir o transferir en nombre de otro."
```

Y las clases de la capa de autenticación con sus relaciones:

```
    class AuthController {
        <<REST>>
        +registro(RegistroRequest cuerpo) TokenResponse
        +login(LoginRequest cuerpo) TokenResponse
        +yo(UsuarioAutenticado usuario) UsuarioResponse
    }
    class AutenticacionService {
        +registrar(String nombreUsuario, String email, String contrasena) Autenticacion
        +login(String usuarioOEmail, String contrasena) Autenticacion
    }
    class UsuarioService {
        +porId(Long id) Optional~Usuario~
        +porNombreOEmail(String identificador) Optional~Usuario~
        +nombreDeUsuarioTomado(String nombreUsuario) boolean
        +emailTomado(String email) boolean
        +crear(Usuario usuario) void
    }
    class JwtService {
        +firmar(Long usuarioId) TokenFirmado
        +usuarioDe(String token) Optional~Long~
    }
    class UsuarioMapper {
        <<interface>>
        +insertar(Usuario usuario) void
        +buscarPorId(Long id) Usuario
        +buscarPorNombreOEmail(String identificador) Usuario
        +existeNombreDeUsuario(String nombreUsuario) boolean
        +existeEmail(String email) boolean
    }
    class FiltroJwt {
        <<filter>>
        +doFilterInternal(HttpServletRequest pedido, HttpServletResponse respuesta, FilterChain cadena) void
    }

    AuthController --> AutenticacionService
    AutenticacionService --> UsuarioService
    AutenticacionService --> JwtService
    UsuarioService --> UsuarioMapper
    FiltroJwt --> JwtService
    FiltroJwt --> UsuarioService
```

En la sección de excepciones, agregar las clases nuevas y sus relaciones:

```
    class NoAutenticadoException {
        +estadoHttp() int
    }
    class CredencialesInvalidasException {
        +codigo() String
    }
    class NombreDeUsuarioTomadoException {
        +codigo() String
    }
    class EmailTomadoException {
        +codigo() String
    }

    TruchoProdeException <|-- NoAutenticadoException
    NoAutenticadoException <|-- CredencialesInvalidasException
    ReglaDeNegocioException <|-- NombreDeUsuarioTomadoException
    ReglaDeNegocioException <|-- EmailTomadoException

    note for NoAutenticadoException "401 es 'no se quien sos'. NoAutorizadoException es 403: 'se quien sos y no te alcanza'."
```

En la sección "1. Dominio", agregar `TokenFirmado`, `Autenticacion` y `UsuarioAutenticado` con sus
campos. `Usuario` ya lista `contrasenaHash` y `rol`, así que ahí no hay nada que tocar.

En la tabla "5. Del diagrama al esquema", agregar la fila:

```markdown
| `Usuario.rol : Rol` leído en cada request | el filtro consulta `usuario` por id en vez de confiar en el token |
```


- [ ] **Step 5: Correr toda la suite y confirmar el total**

Run: `./mvnw test`
Expected: `BUILD SUCCESS`, con los 31 tests de avisos más los 36 de esta vertical.

Si da `Connection refused`, la base no está levantada: pedirle al usuario `podman start truchoprode-db`.

- [ ] **Step 6: Commit**

```bash
git add server/src/test/java/com/truchoprode/controller/DecisionesDeAutenticacionTest.java \
        CLAUDE.md Doc/TruchoProde_DiagramaClases.md
git commit -m "Blindar las dos decisiones de la autenticacion con tests

Dos tests que no prueban plomeria sino decisiones: el token de un usuario
borrado deja de servir, y promover a ADMIN con un UPDATE se ve reflejado con
el mismo token de antes. Si alguien mas adelante mete el rol adentro del
token para ahorrarse la consulta, el segundo se pone rojo.

Corrige ademas el diagrama de clases, donde los controllers recibian el
usuarioId como parametro: ese dato no puede venir del cliente porque
permitiria predecir o transferir en nombre de otro. Ahora sale del contexto
de seguridad.

El CLAUDE.md documenta el package exception y el UPDATE con el que nace el
primer ADMIN."
```

---

## Al terminar

Queda pendiente, y es la vertical siguiente, no parte de esta: el frontend de login y registro. El
mail de avisos linkea a `${AVISOS_URL_BASE}/predicciones`, que sigue sin existir.

Para probar a mano con el backend levantado (`./mvnw spring-boot:run`):

```bash
curl -s -X POST localhost:8080/api/auth/registro -H 'Content-Type: application/json' \
  -d '{"nombreUsuario":"tinogera","email":"gerardisantino4@gmail.com","contrasena":"clave1234"}'
```

Y con el token que devuelve:

```bash
curl -s localhost:8080/api/auth/yo -H "Authorization: Bearer <token>"
```
