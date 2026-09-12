# TruchoProde — Diagrama de clases

Modelo de clases del backend, derivado de la especificación funcional
(`TruchoProde_Especificacion.md`) y del esquema `V1__esquema_inicial.sql`.

Convenciones de las relaciones:

| Notación | Significado | En la base |
|---|---|---|
| `*--` composición | la parte no existe sin el todo | FK con `ON DELETE CASCADE` |
| `-->` asociación | referencia, con vida propia | FK sin cascada |
| `..>` dependencia | la usa, no la contiene | — |
| `<\|--` herencia | generalización | — |
| `<\|..` realización | implementa una interfaz | — |

---

## 1. Dominio

Las siete entidades confirmadas más los dos enums y el cálculo del puntaje.
Las entidades son puras: sin anotaciones de infraestructura, como pide la arquitectura.

```mermaid
classDiagram
    direction TB

    class Usuario {
        -Long id
        -String nombreUsuario
        -String email
        -String contrasenaHash
        -Rol rol
        -Instant creadoEn
        +esAdmin() boolean
    }

    class Rol {
        <<enumeration>>
        JUGADOR
        ADMIN
    }

    class UsuarioAutenticado {
        <<record>>
        +Long id
        +String nombreUsuario
        +Rol rol
    }

    class TokenFirmado {
        <<record>>
        +String token
        +Instant expiraEn
    }

    class Autenticacion {
        <<record>>
        +String token
        +Instant expiraEn
        +Usuario usuario
    }

    class Grupo {
        -Long id
        -String nombre
        -String codigoInvitacion
        -Long creadorId
        -Instant creadoEn
        +fueCreadoPor(Long usuarioId) boolean
    }

    class Miembro {
        -Long grupoId
        -Long usuarioId
        -Instant seUnioEn
    }

    class Jornada {
        -Long id
        -Integer numero
        -String nombre
        -Instant creadaEn
    }

    class Partido {
        -Long id
        -Long jornadaId
        -String equipoLocal
        -String equipoVisitante
        -Instant comienzaEn
        -Short golesLocal
        -Short golesVisitante
        +tieneResultado() boolean
        +signo() Signo
        +yaComenzo(Instant ahora) boolean
    }

    class Prediccion {
        -Long id
        -Long usuarioId
        -Long partidoId
        -Short golesLocal
        -Short golesVisitante
        -Short puntos
        -Instant creadaEn
        -Instant actualizadaEn
        +signo() Signo
        +fuePuntuada() boolean
    }

    class Transferencia {
        -Long id
        -Long grupoId
        -Long emisorId
        -Long receptorId
        -int puntos
        -Instant creadaEn
        +involucraA(Long usuarioId) boolean
    }

    class Signo {
        <<enumeration>>
        LOCAL
        EMPATE
        VISITANTE
    }

    class CalculadorDePuntaje {
        <<servicio de dominio>>
        +EXACTO short$
        +ACIERTO_DE_SIGNO short$
        +ERRADO short$
        +puntosDe(Prediccion prediccion, Partido partido) short
    }

    Usuario ..> Rol : tiene
    Usuario "1" --> "0..*" Grupo : crea
    Usuario "1" --> "0..*" Miembro : es
    Grupo "1" *-- "0..*" Miembro : integrado por
    Grupo "1" *-- "0..*" Transferencia : registra
    Miembro "1" <-- "0..*" Transferencia : emisor
    Miembro "1" <-- "0..*" Transferencia : receptor
    Usuario "1" *-- "0..*" Prediccion : hace
    Partido "1" <-- "0..*" Prediccion : sobre
    Jornada "1" *-- "1..*" Partido : contiene
    Partido ..> Signo
    Prediccion ..> Signo
    CalculadorDePuntaje ..> Prediccion
    CalculadorDePuntaje ..> Partido
    CalculadorDePuntaje ..> Signo

    note for Prediccion "No tiene grupoId: es unica por (usuario, partido) y vale igual en todos los grupos del usuario."
    note for Miembro "No guarda score. El saldo se calcula: puntos por predicciones + recibidos - transferidos."
    note for Transferencia "grupoId + emisorId y grupoId + receptorId apuntan a Miembro: transferir fuera del grupo es imposible."
```

**Por qué esas relaciones**

- `Grupo *-- Miembro` es composición: borrado el grupo, la membresía no significa nada.
  `Usuario --> Miembro` es asociación: el usuario sobrevive a cualquier grupo.
- `Usuario *-- Prediccion` es composición y `Prediccion --> Partido` asociación: la predicción
  es del jugador; el partido es un dato global que existe aunque nadie prediga.
- `Transferencia` apunta dos veces a `Miembro`, no a `Usuario`. Ese es el punto: la relación
  con el grupo es lo que hace verificable la regla de "solo dentro del mismo grupo".
- `Miembro` no tiene `score`. El saldo es derivado, así que es una operación de servicio,
  no un atributo.

---

## 2. Capas de aplicación

Flujo `Controller → Service → Mapper`, con MyBatis en el borde.

```mermaid
classDiagram
    direction LR

    class PrediccionController {
        <<REST>>
        +guardar(UsuarioAutenticado usuario, PrediccionRequest cuerpo) PrediccionResponse
        +deJornada(UsuarioAutenticado usuario, Long jornadaId) List~PrediccionResponse~
    }
    class TransferenciaController {
        <<REST>>
        +transferir(UsuarioAutenticado emisor, TransferenciaRequest cuerpo) TransferenciaResponse
        +historialPropio(Long grupoId, UsuarioAutenticado usuario) List~TransferenciaResponse~
    }
    class RankingController {
        <<REST>>
        +ranking(Long grupoId, UsuarioAutenticado solicitante) List~FilaRanking~
    }
    class AdminController {
        <<REST>>
        +crearJornada(JornadaRequest cuerpo) JornadaResponse
        +cargarResultado(Long partidoId, ResultadoRequest cuerpo) void
    }

    class PrediccionService {
        +guardar(Long usuarioId, Long partidoId, short local, short visitante) Prediccion
        +deUsuarioEnJornada(Long usuarioId, Long jornadaId) List~Prediccion~
    }
    class PuntajeService {
        +cargarResultado(Long partidoId, short local, short visitante) void
        +puntosPorPredicciones(Long usuarioId) int
    }
    class TransferenciaService {
        +transferir(Long grupoId, Long emisorId, Long receptorId, int puntos) Optional~Transferencia~
        +historialDe(Long grupoId, Long usuarioId) List~Transferencia~
    }
    class RankingService {
        +saldoDe(Long grupoId, Long usuarioId) int
        +ranking(Long grupoId) List~FilaRanking~
    }
    class GrupoService {
        +crear(Long creadorId, String nombre) Grupo
        +unirsePorCodigo(Long usuarioId, String codigo) Miembro
        +agregarPorNombreDeUsuario(Long grupoId, String nombreUsuario) Miembro
    }

    class PrediccionMapper {
        <<interface>>
        +insertarOActualizar(Prediccion prediccion) void
        +buscarPorUsuarioYPartido(Long usuarioId, Long partidoId) Prediccion
        +porPartido(Long partidoId) List~Prediccion~
        +sumaPuntosDeUsuario(Long usuarioId) int
        +actualizarPuntos(Long id, short puntos) void
    }
    class TransferenciaMapper {
        <<interface>>
        +insertar(Transferencia transferencia) void
        +sumaEnviada(Long grupoId, Long usuarioId) int
        +sumaRecibida(Long grupoId, Long usuarioId) int
        +historial(Long grupoId, Long usuarioId) List~Transferencia~
    }
    class MiembroMapper {
        <<interface>>
        +insertar(Miembro miembro) void
        +esMiembro(Long grupoId, Long usuarioId) boolean
        +porGrupo(Long grupoId) List~Miembro~
    }
    class PartidoMapper {
        <<interface>>
        +insertar(Partido partido) void
        +guardarResultado(Long id, short local, short visitante) void
        +porJornada(Long jornadaId) List~Partido~
    }

    PrediccionController --> PrediccionService
    TransferenciaController --> TransferenciaService
    RankingController --> RankingService
    AdminController --> PuntajeService

    PrediccionService --> PrediccionMapper
    PuntajeService --> PrediccionMapper
    PuntajeService --> PartidoMapper
    TransferenciaService --> TransferenciaMapper
    TransferenciaService --> MiembroMapper
    TransferenciaService ..> RankingService : consulta el saldo
    RankingService --> PrediccionMapper
    RankingService --> TransferenciaMapper
    RankingService --> MiembroMapper
    GrupoService --> MiembroMapper

    note for TransferenciaService "Recorta al saldo disponible en vez de rechazar. Saldo 0 devuelve Optional.empty(): no se registra nada."
    note for RankingService "Unico lugar donde se arma el saldo: global por predicciones + recibidos - transferidos."

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

    note for PrediccionController "El usuario sale del token, nunca de un parametro: si el id lo mandara el cliente, cualquiera podria predecir o transferir en nombre de otro."
    note for FiltroJwt "Consulta el usuario por id en cada request en vez de leer el rol del token: promover a ADMIN toma efecto en el request siguiente, sin relogin."
```

---

## 3. Avisos por mail

El recordatorio de predicciones pendientes. Es la primera vertical implementada de punta a punta,
y la única parte del sistema que arranca sola: no la dispara una petición HTTP sino el reloj.

```mermaid
classDiagram
    direction LR

    class PlanificadorDeAvisos {
        <<@Scheduled cada 5 min>>
        +avisarPendientes() void
    }

    class AvisoDePrediccionService {
        -int anticipacionMinutos
        +avisarPendientes(Instant ahora) int
    }

    class AgrupadorDeTandas {
        <<utilitario>>
        +agrupar(List~AvisoPendiente~ pendientes) List~TandaDeAvisos~$
    }

    class RedactorDeAvisos {
        -ZoneId zona
        -String urlBase
        +redactar(TandaDeAvisos tanda) MensajeDeAviso
    }

    class EnviadorDeAvisos {
        <<interface>>
        +enviar(MensajeDeAviso mensaje) void
    }

    class EnviadorPorLog {
        +enviar(MensajeDeAviso mensaje) void
    }

    class EnviadorPorMail {
        -JavaMailSender javaMailSender
        -String remitente
        +enviar(MensajeDeAviso mensaje) void
    }

    class AvisoMapper {
        <<interface>>
        +pendientesEntre(Instant desde, Instant hasta) List~AvisoPendiente~
        +marcarEnviado(Long usuarioId, Long partidoId) void
    }

    class AvisoPendiente {
        -Long usuarioId
        -String email
        -String nombreUsuario
        -Long partidoId
        -String equipoLocal
        -String equipoVisitante
        -Instant comienzaEn
    }

    class TandaDeAvisos {
        <<record>>
        -Long usuarioId
        -String email
        -String nombreUsuario
        -Instant comienzaEn
        -List~AvisoPendiente~ partidos
    }

    class MensajeDeAviso {
        <<record>>
        -String destinatario
        -String asunto
        -String cuerpo
    }

    PlanificadorDeAvisos --> AvisoDePrediccionService : dispara
    AvisoDePrediccionService --> AvisoMapper
    AvisoDePrediccionService --> RedactorDeAvisos
    AvisoDePrediccionService --> EnviadorDeAvisos
    AvisoDePrediccionService ..> AgrupadorDeTandas
    EnviadorDeAvisos <|.. EnviadorPorLog
    EnviadorDeAvisos <|.. EnviadorPorMail
    AvisoMapper ..> AvisoPendiente : devuelve
    AgrupadorDeTandas ..> TandaDeAvisos : arma
    TandaDeAvisos "1" *-- "1..*" AvisoPendiente
    RedactorDeAvisos ..> MensajeDeAviso : escribe
    EnviadorDeAvisos ..> MensajeDeAviso : despacha

    note for EnviadorDeAvisos "Dos implementaciones elegidas por configuracion. El default es la de log: nunca sale un mail real sin pedirlo."
    note for AvisoDePrediccionService "Envia primero y marca despues. Si el mail falla no queda marcado y se reintenta en la corrida siguiente."
```

**Por qué está partido así**

- `PlanificadorDeAvisos` solo mira el reloj y llama al service. Toda la lógica quedó afuera del
  `@Scheduled`, que es lo que permite probarla sin esperar cinco minutos.
- `AgrupadorDeTandas` y `RedactorDeAvisos` son funciones puras: sin base, sin mail, sin Spring. Son
  los dos únicos lugares donde vive la regla de "una tanda es un mail" y el texto que lee la persona.
- `EnviadorDeAvisos` existe para que el service **no sepa** si el mail sale por SMTP o a la consola.
  El default es el de log, así que mandar un mail real es una decisión explícita de configuración.
- `AvisoPendiente` no es una tabla: es la fila del cruce entre partidos y usuarios que devuelve la
  consulta.

---

## 4. Excepciones de dominio

La única herencia real del modelo. El resto del dominio es plano a propósito:
no hay dos entidades que compartan comportamiento como para justificar una superclase.

```mermaid
classDiagram
    direction TB

    class RuntimeException {
        <<java.lang>>
    }
    class TruchoProdeException {
        <<abstract>>
        #String mensaje
        +codigo() String*
        +estadoHttp() int*
    }
    class RecursoNoEncontradoException {
        +codigo() String
        +estadoHttp() int
    }
    class NoAutorizadoException {
        +estadoHttp() int
    }
    class NoEsMiembroDelGrupoException {
        -Long grupoId
        +codigo() String
    }
    class RequiereRolAdminException {
        +codigo() String
    }
    class ReglaDeNegocioException {
        +estadoHttp() int
    }
    class PartidoYaComenzoException {
        -Long partidoId
        +codigo() String
    }
    class ResultadoInvalidoException {
        +codigo() String
    }

    RuntimeException <|-- TruchoProdeException
    TruchoProdeException <|-- RecursoNoEncontradoException
    TruchoProdeException <|-- NoAutorizadoException
    TruchoProdeException <|-- ReglaDeNegocioException
    NoAutorizadoException <|-- NoEsMiembroDelGrupoException
    NoAutorizadoException <|-- RequiereRolAdminException
    ReglaDeNegocioException <|-- PartidoYaComenzoException
    ReglaDeNegocioException <|-- ResultadoInvalidoException

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

    note for NoEsMiembroDelGrupoException "El secreto de las transferencias es autorizacion, no UI: el backend corta aca."
    note for NoAutenticadoException "401 es 'no se quien sos'. NoAutorizadoException es 403: 'se quien sos y no te alcanza'. Confundirlas es el error clasico."
    note for CredencialesInvalidasException "Un solo error para el usuario inexistente y la contrasena mal: si fueran distintos, el login serviria para averiguar que cuentas existen."
```

---

## 5. Del diagrama al esquema

| En el diagrama | En `V1__esquema_inicial.sql` |
|---|---|
| `Prediccion` sin `grupoId` | `UNIQUE (usuario_id, partido_id)` |
| `Transferencia --> Miembro` (dos veces) | FKs compuestas a `miembro (grupo_id, usuario_id)` |
| `Transferencia.puntos : int` positivo | `CHECK (puntos > 0)` |
| `Miembro` sin `score` | `miembro` sin columna de saldo |
| `Grupo *-- Miembro` | `ON DELETE CASCADE` |
| `Miembro <-- Transferencia` | sin cascada: el historial es inmutable |
| `Usuario.rol : Rol` | `CHECK (rol IN ('JUGADOR','ADMIN'))` |
| `Partido.golesLocal : Short` | anulable, con `CHECK` de resultado completo |
| aviso enviado una sola vez | `aviso_prediccion` con PK `(usuario_id, partido_id)` (V2) |
| preferencia de avisos | `usuario.quiere_avisos BOOLEAN NOT NULL DEFAULT TRUE` (V2) |
| `FiltroJwt` lee el rol en cada request | consulta `usuario` por id; el token solo lleva el `sub` |
| `Usuario.contrasenaHash` con BCrypt | `contrasena_hash VARCHAR(100)`: BCrypt ocupa 60 |

---

## Pendientes

- Si el creador del grupo conserva atribuciones (expulsar, renombrar) o es solo un dato histórico.

Confirmado desde entonces: se puede predecir **hasta que empieza el partido**, y por eso
`Partido.yaComenzo(...)` se queda en el modelo.
