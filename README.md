# MedSim Backend

Backend-часть платформы `MedSim` для учебной ролевой симуляции процессов медицинской организации.

## Что это за сервис

Сейчас backend отвечает за базовую инфраструктуру проекта:

- запуск `Spring Boot` приложения;
- подключение к `PostgreSQL`;
- применение миграций через `Flyway`;
- базовый сценарий входа игрока в игровую сессию;
- сохранение игроков и игровых сессий в БД.

На текущем этапе реализован минимальный рабочий поток:

1. Игрок вводит имя, должность и код сессии на фронте.
2. Фронт вызывает `POST /api/player-sessions/join`.
3. Backend:
   - создаёт или переиспользует игрока;
   - создаёт или переиспользует игровую сессию;
   - создаёт связь игрока с сессией;
   - возвращает данные для стартовой комнаты ожидания.

## Технологии

- `Java 21`
- `Spring Boot`
- `Spring Web`
- `Spring Validation`
- `Spring Security`
- `Spring Data JPA`
- `Flyway`
- `PostgreSQL`
- `H2` для тестов
- `Maven`
- `Docker Compose`

## Структура проекта

```text
src/main/java/ru/vstu/medsim
├── config
│   └── SecurityConfig.java
├── player
│   ├── PlayerSessionController.java
│   ├── PlayerSessionService.java
│   ├── domain
│   ├── dto
│   └── repository
└── MedsimBackApplication.java
```

```text
src/main/resources
├── application.yml
└── db/migration
    ├── V1__init_auth_schema.sql
    └── V2__add_unique_constraint_to_players.sql
```

## Текущая модель данных

Сейчас в базе используются таблицы:

- `users`
  Для системных пользователей, таких как ведущий и суперпользователь.
- `players`
  Для обычных участников игры.
- `game_sessions`
  Для игровых сессий/комнат.
- `session_participants`
  Для связи игрока с конкретной игровой сессией.

Дополнительно:

- на уровне БД есть защита от дублей игроков по паре
  `display_name + hospital_position`;
- на уровне БД есть уникальность связи
  `game_session_id + player_id`.

## Переменные окружения

Используются переменные из `.env`:

```env
DB_NAME=medsim
DB_USERNAME=medsim_user
DB_PASSWORD=medsim_password
DB_PORT=5433
SERVER_PORT=8080
DB_URL=jdbc:postgresql://localhost:5433/medsim
```

Важно: в локальном окружении проекта БД вынесена на порт `5433`, потому что на машине может уже работать другой `PostgreSQL` на `5432`.

## Как запустить

### 1. Поднять PostgreSQL

```bash
cd /Users/mihailbykadorov/Desktop/MedSim/MedSim_back
cp .env.example .env
docker compose --env-file .env up -d
```

### 2. Запустить backend

```bash
cd /Users/mihailbykadorov/Desktop/MedSim/MedSim_back
set -a
source .env
set +a
mvn spring-boot:run
```

После запуска backend доступен на:

- `http://localhost:8080`

## Тесты

Запуск тестов:

```bash
cd /Users/mihailbykadorov/Desktop/MedSim/MedSim_back
mvn test
```

Что сейчас покрыто тестами:

- успешный вход игрока в сессию;
- повторный вход того же игрока в ту же сессию без дубля;
- валидация пустых полей.

## Текущий endpoint

### `POST /api/player-sessions/join`

Пример запроса:

```json
{
  "displayName": "Анна Петрова",
  "hospitalPosition": "Главная медсестра",
  "sessionCode": "WARD-12"
}
```

Пример ответа:

```json
{
  "participantId": 1,
  "playerId": 1,
  "sessionId": 1,
  "sessionCode": "WARD-12",
  "sessionName": "Сессия WARD-12",
  "sessionStatus": "LOBBY",
  "displayName": "Анна Петрова",
  "hospitalPosition": "Главная медсестра",
  "gameRole": null,
  "joinedAt": "2026-03-14T10:00:00"
}
```

## Текущее ограничение

Сейчас код сессии работает как код комнаты:

- если сессия с таким кодом есть, игрок подключается к ней;
- если нет, она создаётся автоматически.

Это временная минимальная модель. В дальнейшем логичнее перейти к сценарию:

- ведущий создаёт сессию;
- система выдаёт код;
- игроки подключаются только к уже существующей сессии.

## Ближайшие шаги

- экран и API ведущего;
- просмотр подключившихся игроков по сессии;
- назначение игровых ролей;
- старт игры;
- дальнейшая реализация таймера, чата, заявок и канбан-доски.
