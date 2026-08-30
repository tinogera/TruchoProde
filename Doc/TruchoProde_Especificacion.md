# TruchoProde
> Documento de especificación del proyecto

---

## 1. Descripción del proyecto

TruchoProde es una aplicación web de prode deportivo para grupos de amigos con una capa de metagame político. Además de predecir los resultados de los partidos de cada jornada, los jugadores pueden transferirse puntos entre sí de forma secreta, abriendo la puerta a alianzas, traiciones y estrategias para manipular el ranking.

---

## 2. Mecánica del juego

### 2.1 Prode base

- Cada jornada los jugadores predicen los resultados de los partidos.
- Según cuántos aciertos tengan, suman puntos a su score acumulado.
- El ranking refleja la suma total de puntos a lo largo de todo el torneo.
- Existe un rol de administrador que crea jornadas, carga partidos y registra resultados.
- Con los resultados cargados, el sistema calcula automáticamente los puntos de cada predicción.

### 2.2 El truco — Sistema de transferencias

- Cada jornada, cada jugador dispone de un cupo de **3 puntos por compañero** para transferir.
- Si hay 4 jugadores, podés transferir hasta 3 puntos a cada uno de los otros 3 (9 en total por jornada).
- Los puntos que transferís **salen de tu score**. Si tenés 47 y das 3, quedás en 44.
- El que recibe suma esos puntos a su score. Si tenía 40, queda en 43.
- El cupo **no se acumula**: si en la jornada 3 no transferís nada, en la jornada 4 seguís teniendo cupo de 3, no de 6.
- Las transferencias son **secretas**: solo el emisor y el receptor las ven.

### 2.3 Fórmula de puntos

```
puntos_totales = puntos_ganados_por_predicciones + puntos_recibidos − puntos_transferidos
```

Es un acumulado histórico que nunca se resetea. Es lo que aparece en el ranking.

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
truchoprode/
├── backend/
│   ├── src/main/java/com/truchoprode/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── domain/
│   │   ├── dto/
│   │   ├── config/
│   │   └── mapper/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/     ← Flyway
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── pages/
│   │   ├── components/
│   │   └── services/
│   └── vite.config.js
└── README.md
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
Jugador o administrador del sistema. Tiene nombre, email, contraseña hasheada y rol.

### Jornada
Agrupa los partidos de una fecha. Tiene estado (abierta / cerrada).

### Partido
Pertenece a una jornada. Tiene equipos y resultado final cargado por el admin.

### Prediccion
Resultado que un jugador predijo para un partido. Se puntúa al cerrar la jornada.

### Transferencia
Movimiento de puntos entre dos jugadores en una jornada. Secreta para el resto.

---

## 7. Vistas del frontend

- Login / Registro
- Ranking general
- Predicciones de la jornada actual
- Panel de transferencias (enviar y ver historial propio)
- Panel de administrador: crear jornada, cargar partidos y resultados
