# TruchoProde
> Documento de especificación del proyecto

---

## 1. Descripción del proyecto

TruchoProde es una aplicación web de prode deportivo para grupos de amigos con una capa de metagame político. Además de predecir los resultados de los partidos de cada jornada, los jugadores pueden transferirse puntos entre sí de forma secreta, abriendo la puerta a alianzas, traiciones y estrategias para manipular el ranking.

---

## 2. Mecánica del juego

### 2.1 Prode base

- TruchoProde publica los partidos, agrupados en jornadas. **Son los mismos para todos los jugadores.**
  Las jornadas, los partidos y los resultados los carga el **administrador de la plataforma** (un rol
  global, no un admin por grupo): son datos únicos para toda la aplicación, no de cada grupo.
- Cada jugador carga **una sola predicción por partido**, no una por grupo. Esa predicción se compara
  después contra la de los demás en cada grupo al que pertenece.
- Puntaje de cada partido:

  | Caso | Puntos |
  |---|---|
  | Acertó el resultado exacto (ej. predijo 2-1 y salió 2-1) | **3** |
  | Acertó quién ganó pero no el marcador (predijo 3-0, salió 2-1) | **2** |
  | Erró quién ganó | **0** |

  El empate sigue la misma regla: predecir 1-1 y que salga 1-1 son 3 puntos; predecir 2-2 y que salga
  1-1 son 2, porque acertó el empate.

- Los puntos ganados por predicciones son **idénticos en todos tus grupos** (una predicción, un puntaje).
  Lo que hace que los rankings difieran entre grupos son las transferencias.
- Se puede cargar y editar la predicción **hasta que empieza el partido**. Cada partido cierra solo:
  no hay un cierre por jornada.

### 2.2 Grupos

- Un usuario puede pertenecer a **varios grupos** a la vez.
- El score es **por grupo**: en cada uno tenés tu propio saldo y tu propia posición.
- Los puntos **no se acumulan ni se mezclan** entre grupos.
- Solo se puede transferir entre miembros del mismo grupo.

### 2.3 El truco — Sistema de transferencias

- **No hay cupo.** Podés transferir la cantidad que quieras, a quien quieras, **en cualquier momento**
  (no hay ventana atada al estado de la jornada).
- El único límite es tu saldo en ese grupo: solo podés dar puntos que tengas.
- Los puntos que transferís **salen de tu score** en ese grupo y entran al del receptor, en el mismo grupo.
- Si pedís transferir más de lo que tenés, **no se rechaza**: se recorta automáticamente al máximo
  disponible y quedás en 0.
- Si tu saldo ya es 0, la operación **no hace nada** (no se registra una transferencia de 0 puntos).
- Las transferencias son **secretas**: solo el emisor y el receptor las ven.

### 2.4 Fórmula de puntos

```
score_en_grupo = puntos_por_predicciones + recibidos_en_ese_grupo − transferidos_en_ese_grupo
```

`puntos_por_predicciones` es global (surge de tus predicciones, que son únicas). Los otros dos términos
son propios de cada grupo. Es un acumulado histórico que nunca se resetea.

### 2.5 Avisos por mail

Una hora antes de que arranquen, TruchoProde le manda un mail a quien todavía no cargó su predicción.

- **Una tanda es un mail.** Si a las 15:00 arrancan ocho partidos y no cargaste ninguno, llega un solo
  mail con los ocho, no ocho mails.
- Le llega a quien **es miembro de algún grupo**: una cuenta que se creó y nunca entró a ninguno no
  recibe nada.
- Cualquiera puede apagarlos desde su perfil.
- Nunca se avisa dos veces por el mismo partido, aunque el sistema revise cada pocos minutos.

Ejemplo de lo que llega:

```
Asunto: Te faltan 2 predicciones (arrancan 15:00)

Hola santino,

Arrancan a las 15:00 y todavía no cargaste tus predicciones:

  Boca - River
  Racing - Independiente

Cargalas acá: http://localhost:5173/predicciones

Para dejar de recibir estos avisos, apagalos desde tu perfil.
```

---

## 3. Stack tecnológico

### 3.1 Backend

| Tecnología | Descripción |
|---|---|
| Java + Spring Boot | Framework principal del backend |
| PostgreSQL | Base de datos relacional |
| MyBatis | Acceso a datos y mapeo SQL |
| Spring Security + JWT | Autenticación y autorización |
| Flyway | Migraciones de base de datos |
| Maven | Gestión de dependencias |

### 3.2 Frontend

| Tecnología | Descripción |
|---|---|
| React + JavaScript | Framework de UI |
| Vite | Bundler y servidor de desarrollo |
| Fetch API | Consumo del backend REST |

---

## 4. Estructura del proyecto

```
TruchoProde/
├── Doc/
├── front/                        React + Vite
│   └── src/{pages,components,services}
└── server/                       Spring Boot
    ├── pom.xml
    └── src/main/
        ├── java/com/truchoprode/{controller,service,repository,domain,dto,config,mapper}
        └── resources/{application.yml, db/migration}
```

---

## 5. Flujo de la arquitectura backend

```
Controller → Service → Repository → Domain
```

- **Controller**: recibe el request HTTP y delega al service.
- **Service**: contiene la lógica de negocio.
- **Repository**: accede a la base de datos usando MyBatis.
- **Domain**: entidades puras del negocio, sin dependencia de infraestructura.
- **DTO**: objetos de transferencia de datos entre capas y hacia el frontend.
- **Config**: configuración de Spring Security, JWT y CORS.

---

## 6. Entidades principales

### Usuario
Jugador del sistema. Tiene nombre de usuario, email, contraseña hasheada, **rol** y una preferencia
para recibir o no los avisos por mail.
El rol es de plataforma: `JUGADOR` (el caso normal) o `ADMIN`, el único que puede crear jornadas,
cargar partidos y registrar resultados. No hay un rol de administrador dentro de cada grupo.

### Grupo
Un prode independiente. Tiene su propio ranking y sus propias transferencias.

### Miembro
Relación usuario–grupo. **Acá vive el score**, porque los puntos son por grupo y no se mezclan.

### Jornada
Agrupa los partidos de una fecha. Es global: la misma para todos los grupos.

### Partido
Pertenece a una jornada. Tiene los dos equipos y el resultado final (goles de cada lado).

### Prediccion
El marcador que un jugador predijo para un partido. **Es única por usuario y partido**, no por grupo.
Se puntúa al cargarse el resultado.

### Transferencia
Movimiento de puntos entre dos miembros **del mismo grupo**. Secreta para el resto.

### AvisoPrediccion
Constancia de que a un usuario ya se le avisó por un partido. Existe para que el recordatorio salga
una sola vez.

> **Pendiente:** los grupos no tienen roles propios más allá del creador. Falta definir si el creador
> conserva alguna atribución (expulsar miembros, renombrar el grupo) o si es solo un dato histórico.

---

## 7. Vistas del frontend

- Login / Registro
- Ranking general
- Predicciones de la jornada actual
- Panel de transferencias (enviar y ver historial propio)
- Panel de administrador de la plataforma (solo rol `ADMIN`): crear jornada, cargar partidos y resultados
- Perfil: apagar o encender los avisos por mail
