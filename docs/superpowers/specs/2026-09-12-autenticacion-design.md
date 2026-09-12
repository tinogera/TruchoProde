# Autenticación — diseño

Fecha: 2026-09-12
Estado: aprobado, sin implementar

## 1. Por qué esta vertical va primero

Hoy el backend no tiene un solo controller. Existe la vertical de avisos por mail —que le
escribe a los usuarios que no cargaron su predicción— pero no hay forma de crear un usuario
ni de cargar una predicción.

Todo lo que falta (predicciones, grupos, transferencias) necesita saber quién es el que pide.
Sin autenticación habría que inventar un `usuarioId` de mentira en cada endpoint y volver a
tocarlos todos después. Por eso va primero: es la pieza que desbloquea las otras tres.

## 2. Decisiones tomadas

| Decisión | Qué se eligió | Por qué |
|---|---|---|
| Identificador de login | Un solo campo que acepta nombre de usuario **o** email | Los dos son únicos en V1. Se resuelve en una consulta con `lower(nombre_usuario) = ? OR lower(email) = ?`, que pega en los dos índices únicos ya existentes |
| Primer ADMIN | `UPDATE` manual en la base | Ningún endpoint puede regalar privilegios. Cero código y cero superficie de ataque, al precio de un paso manual documentado |
| Vencimiento del token | Uno solo, 120 minutos, sin refresh | YAGNI: el refresh duplica endpoints, obliga a guardar y revocar tokens en la base, y para un prode entre amigos no compra casi nada |
| Transporte | Header `Authorization: Bearer` | Es el camino que Spring Security y jjwt esperan sin pelearse, y no necesita protección CSRF porque el navegador no lo manda solo |
| Integración con Spring Security | Filtro JWT propio, stateless, sin `UserDetailsService` | El `DaoAuthenticationProvider` resuelve un problema que no tenemos (múltiples providers, form login, sesiones). Además, `UserDetails` en la entidad de dominio choca con la regla de que `domain` no lleva infraestructura |
| Origen del rol | El filtro lo lee de la base en cada request | Si el rol viajara en el token, promoverse a ADMIN con un `UPDATE` no tendría efecto hasta relogin. Una consulta por request es un costo que en este proyecto ni se mide |
| Después del registro | Queda logueado: el registro devuelve token | Un pedido menos en el front y una pantalla menos de fricción |

## 3. El esquema no cambia

No hace falta migración. `V1__esquema_inicial.sql` ya tiene `nombre_usuario`, `email`,
`contrasena_hash` y `rol` con su `CHECK (rol IN ('JUGADOR','ADMIN'))`, más los dos índices
únicos sobre `lower()`. Esta vertical es todo código.

BCrypt produce hashes de 60 caracteres y la columna admite 100.

## 4. Componentes

### Dominio (`com.truchoprode.domain`)

Entidades puras, sin anotaciones de infraestructura.

- `Usuario` — `id`, `nombreUsuario`, `email`, `contrasenaHash`, `rol`, `creadoEn`. Ya figura en
  el diagrama de clases pero todavía no existe como archivo.
- `Rol` — enum `JUGADOR` / `ADMIN`.
- `UsuarioAutenticado` — record con `id` y `rol`. Es el principal que queda en el
  `SecurityContext` y **la pieza que reemplaza al `Long usuarioId` que hoy figura como
  parámetro de los controllers en el diagrama**. Los controllers lo reciben con
  `@AuthenticationPrincipal`, así el id del usuario nunca llega desde el cliente.

### Repositorio (`com.truchoprode.mapper`)

- `UsuarioMapper` + `src/main/resources/mapper/UsuarioMapper.xml`:
  `insertar`, `buscarPorNombreOEmail`, `buscarPorId`, `existeNombreDeUsuario`, `existeEmail`.

### Servicios (`com.truchoprode.service`)

- `JwtService` — firma y valida. No conoce la base ni HTTP. Lee `truchoprode.jwt.secret` y
  `truchoprode.jwt.expiracion-minutos`, que ya están en `application.yml`. Recibe un `Clock`
  inyectado para que el vencimiento sea testeable.
- `UsuarioService` — buscar por id, buscar por nombre-o-email, verificar existencia.
- `AutenticacionService` — `registrar()` (valida que no exista, hashea, inserta siempre con rol
  `JUGADOR`) y `login()` (busca, compara el hash, firma el token).

`UsuarioService` está separado de `AutenticacionService` a propósito: el filtro necesita
"buscame este usuario" pero no tiene nada que ver con registrar ni loguear. En una sola clase,
el filtro dependería de los métodos de login sin usarlos.

### Config (`com.truchoprode.config`)

- `SecurityConfig` — `SecurityFilterChain` stateless, `PasswordEncoder` (BCrypt), CORS leído de
  `truchoprode.cors.origenes-permitidos`, CSRF apagado, y las reglas: `/api/auth/registro` y
  `/api/auth/login` abiertos, todo el resto autenticado.
- `FiltroJwt` — `OncePerRequestFilter`. Valida el token con `JwtService`, pide el usuario a
  `UsuarioService` y arma el `Authentication` con la autoridad `ROLE_ADMIN` o `ROLE_JUGADOR`,
  de modo que después el `AdminController` se proteja con `@PreAuthorize("hasRole('ADMIN')")`.

El filtro habla con `UsuarioService`, no directo con el mapper, para no saltear la capa de
servicio que impone el flujo `Controller → Service → Repository → Domain`.

### Excepciones (`com.truchoprode.exception`)

El diagrama de clases dibuja la jerarquía `TruchoProdeException` pero no dice en qué package
vive, y el `CLAUDE.md` no lista ninguno para excepciones. Esta vertical lo fija en
`com.truchoprode.exception`: no van en `domain`, que por regla del proyecto son entidades
puras. Es un package nuevo, así que hay que sumarlo a la lista de la sección "Arquitectura
backend" del `CLAUDE.md`.

Acá nacen `TruchoProdeException` (abstracta, con `codigo()` y `estadoHttp()`),
`NoAutenticadoException`, `CredencialesInvalidasException`, `ReglaDeNegocioException`,
`NombreDeUsuarioTomadoException` y `EmailTomadoException`. Las demás de la jerarquía las irán
agregando las verticales que las necesiten.

### DTOs (`com.truchoprode.dto`)

`RegistroRequest`, `LoginRequest`, `TokenResponse`, `UsuarioResponse`. Ninguna entidad de
dominio sale por el controller y `contrasenaHash` no aparece en ningún response.

## 5. Contratos HTTP

### `POST /api/auth/registro` — abierto

```json
{ "nombreUsuario": "tinogera", "email": "alguien@gmail.com", "contrasena": "..." }
```

`201` con `{ token, expiraEn, usuario: { id, nombreUsuario, rol } }`.

El rol no se acepta en el body, ni siquiera para ignorarlo: todo el que se registra nace
`JUGADOR`.

### `POST /api/auth/login` — abierto

```json
{ "usuarioOEmail": "tinogera", "contrasena": "..." }
```

`200` con `{ token, expiraEn, usuario: { id, nombreUsuario, rol } }`. El front necesita el rol
para saber si muestra la pantalla de admin.

`expiraEn` es el instante de vencimiento en ISO-8601 UTC (por ejemplo
`2026-09-12T21:30:00Z`), no una cantidad de minutos ni un epoch: así el front puede comparar
contra el reloj del navegador sin tener que saber cuándo se emitió el token.

### `GET /api/auth/yo` — autenticado

`200` con el usuario del token. Es lo que el front llama al recargar la página para saber si el
token guardado sigue sirviendo y quién es. Como el filtro va a la base, el rol viene fresco.

### Flujo de un request autenticado

El filtro saca el header `Authorization`, valida firma y vencimiento, toma el `sub` como
`usuarioId`, pide el usuario a `UsuarioService` y deja un `UsuarioAutenticado` en el
`SecurityContext`. Si algo falla —sin header, firma inválida, token vencido, o el usuario ya no
existe en la base— no completa la autenticación y la cadena termina en `401`. El caso del
usuario borrado sale gratis justo por consultar la base.

### Validación

Con `jakarta.validation` en los DTOs (`spring-boot-starter-validation` ya está en el `pom.xml`).

- `nombreUsuario`: 3 a 30 caracteres (30 es el límite de la columna en V1), letras, números,
  punto y guion bajo, sin espacios.
- `email`: `@Email`, hasta 255.
- `contrasena`: mínimo 8, **máximo 72**. El tope no es capricho: BCrypt ignora en silencio todo
  lo que pase de 72 bytes, así que sin el límite una contraseña larguísima se recortaría sin
  avisarle a nadie.

## 6. Errores

Colgados de la jerarquía `TruchoProdeException` que ya está en el diagrama de clases, más un
`@RestControllerAdvice` que los traduce a `ProblemDetail`.

| Caso | Excepción | HTTP |
|---|---|---|
| Nombre de usuario ya tomado | `NombreDeUsuarioTomadoException` (rama `ReglaDeNegocio`) | `409` |
| Email ya registrado | `EmailTomadoException` (rama `ReglaDeNegocio`) | `409` |
| Usuario o contraseña incorrectos | `CredencialesInvalidasException` (rama `NoAutenticado`) | `401` |
| Token ausente, vencido o inválido | lo corta el filtro | `401` |
| Body inválido | `MethodArgumentNotValidException` | `400` |

Esta vertical **agrega una rama nueva** a la jerarquía del diagrama: `NoAutenticadoException`
con estado `401`, hermana de `NoAutorizadoException`. La distinción importa y es la que más se
confunde: `401` es "no sé quién sos", `403` es "sé quién sos y no te alcanza". Las dos hijas que
ya existen en `NoAutorizado` (`NoEsMiembroDelGrupoException`, `RequiereRolAdminException`) son
`403` puras; credenciales inválidas no pertenece a esa rama.

## 7. Dos detalles de seguridad, explícitos

- El `401` del login dice **"usuario o contraseña incorrectos"** sin aclarar cuál de los dos
  falló, para no convertir el login en un detector de qué cuentas existen. Y compara el hash
  **incluso cuando el usuario no existe**, contra un hash descartable, porque si no, el tiempo
  de respuesta delata lo mismo que el mensaje que acabamos de ocultar.
- El registro, en cambio, **sí** dice "ese nombre ya está tomado", y eso es coherente, no una
  contradicción: en TruchoProde los nombres de usuario son públicos por diseño —a un grupo se
  entra escribiendo el nombre de alguien—. No hay nada que ocultar, y sin ese mensaje el
  registro sería inusable.

## 8. Testing

TDD, con la misma división que la vertical de avisos: unitarios puros y de integración contra
el PostgreSQL real. Los de integración llevan `@ActiveProfiles("test")` y necesitan la base
levantada.

### `JwtServiceTest` — unitario, sin Spring

Firma un token y recupera el `usuarioId`; rechaza un token firmado con otro secreto; rechaza
uno vencido (con el `Clock` inyectado); rechaza basura mal formada.

### `AutenticacionServiceTest` — unitario, con el mapper falseado

- el hash guardado no es la contraseña en claro
- nombre ya tomado y email ya tomado, cada uno con su excepción
- **nombre tomado con otra capitalización** (`TinoGera` contra `tinogera`) también se rechaza:
  el índice único de V1 es sobre `lower()`, y el service debe fallar con un error claro antes de
  que explote la base con una violación de constraint
- login por nombre de usuario y login por email, los dos devuelven token
- contraseña incorrecta y usuario inexistente devuelven **la misma** excepción

### `UsuarioMapperTest` — integración con la base real

Inserta y recupera; encuentra por nombre y por email con la misma consulta; encuentra ignorando
mayúsculas; y el duplicado explota por el índice único —verifica la garantía de V1 en lugar de
asumirla—.

### `AutenticacionEnHttpTest` — integración web, de punta a punta

Registro `201`; nombre repetido `409`; body inválido `400`; login `200`; credenciales
incorrectas `401`; `GET /yo` sin token `401`, con token válido `200`, con token de otro secreto
`401`.

Más los dos que prueban decisiones y no plomería:

- **Token válido de un usuario borrado de la base → `401`.** Es lo que justifica que el filtro
  consulte la base.
- **El rol se lee de la base, no del token:** se crea un JUGADOR, se loguea, se lo promueve a
  ADMIN con un `UPDATE`, y con **el mismo token de antes** `GET /yo` ya responde `ADMIN`. Este
  test es la decisión de la sección 2 convertida en algo que se rompe si alguien más adelante
  "optimiza" metiendo el rol en el claim.

## 9. Fuera de alcance

Deliberadamente afuera, para no inflar la vertical: rate limiting en el login, recuperación de
contraseña, verificación de email y logout del lado del servidor (con un token de 120 minutos
sin refresh, el logout es borrarlo en el front).

## 10. Impacto en el diagrama de clases

`Doc/TruchoProde_DiagramaClases.md` hay que actualizarlo en dos puntos:

1. Los controllers que ya están diseñados reciben el `usuarioId` como parámetro
   (`guardar(Long usuarioId, ...)`, `transferir(Long emisorId, ...)`). Con autenticación real
   ese dato **no puede venir del cliente**: si viniera, cualquiera podría cargar predicciones o
   transferir puntos en nombre de otro. Pasa a resolverse con `@AuthenticationPrincipal
   UsuarioAutenticado`.
2. Sumar la capa de autenticación (`AuthController`, `AutenticacionService`, `UsuarioService`,
   `JwtService`, `UsuarioMapper`, `FiltroJwt`, `SecurityConfig`) y la rama
   `NoAutenticadoException`.

## 11. Orden de implementación

`JwtService` → `UsuarioMapper` → `AutenticacionService` → `SecurityConfig` + `FiltroJwt` →
`AuthController`.

Cada uno en RED → GREEN antes de pasar al siguiente, así `./mvnw test` queda verde en cada paso.
