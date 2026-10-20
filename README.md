![CI](https://github.com/virakchantrak/springboot-khla-khlouk-game/actions/workflows/ci.yml/badge.svg)
# 🎲 ហ្គេមខ្លាឃ្លោក (Khla Khlouk)

A Spring Boot backend for **Khla Khlouk**, the traditional Khmer dice game. Players bet play-money on six animals, an admin rolls three dice, and winners are paid automatically. The game runs over a REST API and pushes live updates to players over WebSocket.

> The original game rules in Khmer are at the bottom of this file.

## Features

- Player accounts with JWT login, a play-money wallet and a ledger entry for every bet and payout
- Admin-run games: create, open betting, roll
- Real-time game events over WebSocket (STOMP)
- Security hardening: role checks, rate limits, an audit log, sessions that end on password change
- A consistent JSON error format
- Database migrations with Flyway, and tests at several levels

## How the game works

Each game has three dice. Every die shows one of six symbols: `TIGER`, `CRAB`, `FISH`, `DEER`, `ROOSTER`, `SHRIMP`.

**Lifecycle:** `WAITING` → `BETTING` → `FINISHED`

1. An admin creates a game (`WAITING`) and opens betting (`BETTING`).
2. Players place bets: a symbol and an amount (more than 0, at most 2 decimals). A player can place several bets in one game. The stake leaves their balance immediately.
3. The admin rolls the dice. The dice use a cryptographically secure random generator. The game becomes `FINISHED` and winning bets are paid.

**Payout** depends on how many of the three dice show the bet symbol. The stake is returned together with the winnings:

| Dice showing your symbol | You receive |
|---|---|
| 0 | nothing (stake lost) |
| 1 | 2 × stake (stake + 1× winnings) |
| 2 | 3 × stake (stake + 2× winnings) |
| 3 | 4 × stake (stake + 3× winnings) |

New players start with a balance of `1000.00` (configurable). Money is play-money only: there is no deposit or withdrawal.

## Tech stack

- Java 25, Spring Boot 4.1.1, Gradle
- Spring Web, Spring Data JPA (Hibernate), Spring Security, Spring WebSocket (STOMP), Bean Validation
- PostgreSQL with Flyway migrations
- JWT authentication (jjwt)
- JUnit 5, Mockito, Spring MockMvc

## Getting started

### Prerequisites

- JDK 25
- PostgreSQL

### 1. Create the database

```sql
CREATE DATABASE khla_khlouk;
```

Flyway creates the tables the first time the app starts.

### 2. Configure

The app reads its settings from environment variables, or from a `.env` file in the project folder (the file is ignored by Git, so never commit it). Example `.env`:

```
DB_URL=jdbc:postgresql://localhost:5432/khla_khlouk
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password
JWT_SECRET=<base64 string of at least 32 bytes>
```

Generate a `JWT_SECRET` (PowerShell):

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

or with OpenSSL: `openssl rand -base64 48`. The app refuses to start if the secret is missing, is not Base64, or is shorter than 32 bytes.

| Setting | Required | Default | Meaning |
|---|---|---|---|
| `DB_URL` | yes | | JDBC URL of the database |
| `DB_USERNAME` | yes | | Database user |
| `DB_PASSWORD` | yes | | Database password |
| `JWT_SECRET` | yes | | Base64 signing key, at least 32 bytes |
| `APP_ALLOWED_ORIGINS` | no | `http://localhost:3000,http://localhost:5173` | Browser origins allowed for REST (CORS) and WebSocket. Exact origins only: no `*`, no path, no trailing slash |
| `APP_PLAYER_STARTING_BALANCE` | no | `1000.00` | Balance of a new player |
| `APP_REGISTRATION_MAX_PER_IP_PER_HOUR` | no | `5` | Registrations allowed per IP address per hour |

### 3. Run

```bash
./gradlew bootRun
```

(On Windows PowerShell: `./gradlew bootRun`; in cmd: `gradlew.bat bootRun`.) The API is then at `http://localhost:8080`.

### 4. Create the first admin

Everyone registers as a normal player. There is deliberately no API to become an admin. Register an account, then promote it in the database:

```sql
UPDATE players SET role = 'ADMIN' WHERE username = 'your-username';
```

Admins can also play. Roles are read from the database on every request, so the change takes effect immediately.

## API

All paths start with `/api/v1`. Send the token as `Authorization: Bearer <accessToken>`. Tokens last one hour.

| Method | Path | Who | Description |
|---|---|---|---|
| POST | `/players` | public | Register `{username, password}` |
| POST | `/auth/login` | public | Log in `{username, password}`, returns `{accessToken}` |
| PUT | `/auth/password` | player | Change password `{currentPassword, newPassword}`. Ends all existing sessions |
| GET | `/players/me` | logged in | Your profile and balance |
| GET | `/players/{id}` | admin | Any player's profile |
| GET | `/players/{id}/bets` | owner only | Your bet history (paged) |
| GET | `/wallet/transactions` | player | Your wallet ledger: `DEPOSIT`/`BET`/`PAYOUT` entries (paged) |
| POST | `/games` | admin | Create a game |
| POST | `/games/{id}/betting` | admin | Open betting |
| POST | `/games/{id}/bets` | player | Place a bet `{symbol, amount}` |
| POST | `/games/{id}/roll` | admin | Roll the dice and settle all bets |
| GET | `/games` | logged in | List games, newest first (paged) |
| GET | `/games/{id}` | logged in | Game details including the number and total of bets |
| GET | `/games/{id}/result` | logged in | Dice result of a finished game |

**Rules for input**

- Username: 3 to 50 characters from `A-Z a-z 0-9 _ . -`. Names are unique ignoring case (`Alice` and `alice` are the same account), but login also ignores case.
- Password: at least 6 characters and at most 72 bytes.

**Paging:** `?page=0&size=10&sort=createdAt,desc`. The maximum page size is 100. Paged responses look like `{content, page, size, totalElements, totalPages}`.

**Errors** always have the same shape:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "errors": { "username": "Username is required" }
}
```

`errors` holds per-field messages for validation errors and is empty otherwise. Common codes:

| Status | Codes |
|---|---|
| 400 | `VALIDATION_ERROR`, `BAD_REQUEST`, `INVALID_SORT`, `INSUFFICIENT_BALANCE` |
| 401 | `UNAUTHORIZED` (no token), `INVALID_TOKEN` (bad or expired token) |
| 403 | `ACCESS_DENIED` |
| 404 | `NOT_FOUND`, `GAME_NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 409 | `USERNAME_ALREADY_EXISTS`, `CONCURRENT_UPDATE`, `DATA_CONFLICT` |
| 429 | `TOO_MANY_LOGIN_ATTEMPTS`, `TOO_MANY_PASSWORD_ATTEMPTS`, `TOO_MANY_REGISTRATIONS` |

## Real-time events (WebSocket)

Connect with STOMP to `ws://localhost:8080/ws` and send your token in the `Authorization` header of the CONNECT frame. Then subscribe to one game:

```
/topic/games/{gameId}
```

Example with `@stomp/stompjs`:

```js
const client = new Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: { Authorization: `Bearer ${token}` },
  onConnect: () => {
    client.subscribe(`/topic/games/${gameId}`, (message) => {
      console.log(JSON.parse(message.body));
    });
  },
});
client.activate();
```

Events are sent only after the database change has been committed:

| `type` | When | Extra fields |
|---|---|---|
| `BETTING_OPENED` | an admin opened betting | |
| `BET_PLACED` | a player placed a bet | `totalBets`, `totalBetAmount` |
| `GAME_FINISHED` | the dice were rolled | `dice1`, `dice2`, `dice3` |

Every event also has `gameId` and `status`. Fields that don't apply are `null`.

The connection is **read-only for clients**: sending messages is rejected, you can only subscribe to one exact game id (no wildcards), and a connection without a valid token is refused.

## Security

- Passwords are hashed with BCrypt. Tokens are signed JWTs that last one hour. Changing a password ends every existing session.
- Roles are checked on the server for every request. Players can only read their own bets and wallet.
- Rate limits (per server, in memory): login 5 attempts per 15 minutes for each IP and username and 20 per IP; password change 5 per 15 minutes per player; registration 5 per hour per IP.
- The `SECURITY_AUDIT` logger records logins, blocked attempts, denied access, rejected tokens and WebSocket frames, password changes and admin actions. It never logs passwords or tokens. Treat its output as private: it contains IP addresses and usernames.
- Bets and rolls are protected against races with row locks and optimistic locking.

When running behind a reverse proxy, the rate limits see the proxy's address until forwarded headers are configured (`server.forward-headers-strategy`, only if your proxy sets `X-Forwarded-For` itself). Always serve the API over HTTPS.

## Tests

```bash
./gradlew test
```

Most tests need no database: unit tests with Mockito and controller slice tests (security rules, validation, error format). The integration tests start the real app against a **separate test database**, so create one and add its settings to `.env` or your environment:

```sql
CREATE DATABASE khla_khlouk_test;
```

```
TEST_DB_URL=jdbc:postgresql://localhost:5432/khla_khlouk_test
TEST_DB_USERNAME=your_db_user
```

The password comes from `DB_PASSWORD`. The integration tests roll back their changes; the live WebSocket test runs on a real port and deletes its own rows afterwards.

## Project structure

Code is organized by feature under `kh.virakchantrak.KhlaKhlouk`:

| Package | Contents |
|---|---|
| `auth` | login, JWT, security configuration, rate limiting, audit log |
| `player` | registration and profiles |
| `game` | game lifecycle, dice, payouts |
| `bet` | placing bets and bet history |
| `wallet` | the wallet ledger |
| `websocket` | event publishing and WebSocket security |
| `config` | CORS and WebSocket configuration |
| `common` | error handling and shared response types |

Database migrations are in `src/main/resources/db/migration`. Never edit a migration that has been applied: add a new numbered file instead.

---

## 🕹️ របៀបដែលហ្គេមដំណើរការ

### ១. ការរៀបចំ
* **ក្តារហ្គេម៖** ហ្គេមនេះប្រើក្តារពិសេស ឬកម្រាលដែលមានរូបភាពចំនួន ៦ ផ្សេងគ្នា រួមមាន៖
  * 🐅 **ខ្លា**
  * 🦀 **ក្តាម**
  * 🐟 **ត្រី**
  * 🦌 **ក្តាន់**
  * 🐓 **មាន់ជល់**
  * 🦐 **បង្គា**
* **គ្រាប់ឡុកឡាក់៖** គ្រាប់ឡុកឡាក់ចំនួន ៣ ដែលមានមុខបង្ហាញរូបតំណាងសត្វទាំង ៦ ដូចនៅលើក្តារ។

### ២. ការលេងហ្គេម
* **អ្នកកាត់៖** មនុស្សម្នាក់ដើរតួជាអ្នកកាត់ (មេ) ដើម្បីដាក់គ្រាប់ឡុកឡាក់នៅក្នុងចាន ឬចានដែលមានគម្រប រួចអង្រួនវា។
* **ការភ្នាល់៖** អ្នកលេងដាក់ប្រាក់ភ្នាល់ ឬថូខឹន (Tokens) របស់ពួកគេលើរូបសត្វនៅលើក្តារដែលពួកគេគិតថានឹងលេចឡើងបែរមុខឡើងលើ។

### ៣. លទ្ធផល
* **ការបើក៖** អ្នកកាត់បើកគម្របដើម្បីបង្ហាញគ្រាប់ឡុកឡាក់។
* **ការឈ្នះ៖** ប្រសិនបើរូបសត្វដែលអ្នកលេងបានជ្រើសរើសលេចឡើងនៅលើគ្រាប់ឡុកឡាក់ ពួកគេនឹងឈ្នះ។ ចំនួនប្រាក់រង្វាន់ដែលទទួលបាន គឺអាស្រ័យទៅតាមចំនួនគ្រាប់ឡុកឡាក់ដែលចេញរូបសត្វនោះ (ចេញ ១ គ្រាប់ ស៊ី ១ ដង, ចេញ ២ គ្រាប់ ស៊ី ២ ដង, ចេញ ៣ គ្រាប់ ស៊ី ៣ ដង)។