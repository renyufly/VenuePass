# VenuePass

API 请求、响应与手工测试步骤见 [documents/API.md](documents/API.md)。

Spring Boot event booking and admission service, adapted from the three-module sky-take-out backend. It keeps the Controller–Service–MyBatis mapper layout, result envelope, Redis configuration approach, and scheduled expiry pattern. Food delivery, WeChat, OSS, shopping cart, and WebSocket code are omitted.

## Technology versions

| Component | Version |
| --- | --- |
| Java / Eclipse Temurin | 25 |
| Maven (container build) | 3.10.0 |
| Spring Boot / Spring MVC | 4.1.2 / 7.0.10 |
| Spring Security | 7.1.2 |
| Spring Data Redis | 4.1.2 |
| Spring Kafka / Kafka client | 4.1.2 / 4.2.2 |
| MyBatis starter / MyBatis core | 4.1.0 / 3.5.19 |
| Flyway / MySQL Connector/J | 12.4.0 / 9.7.0 |
| Jackson / Micrometer | 3.1.7 / 1.17.2 |
| JUnit Jupiter | 6.0.3 |
| MySQL / Redis / Kafka containers | 8.4.13 / 8.2.9 LTS / 4.2.2 |

Spring Boot manages framework, Kafka client, Flyway, Connector/J, Jackson, and Micrometer library versions; the table records the resolved versions from `mvn dependency:tree`. Kafka's broker and client have different compatible versions.

Run with `docker compose up --build` or `mvn -pl venuepass-server -am test` with Java 25 and Maven 3.10. Flyway creates the MySQL schema. The app requires MySQL, Redis, and Kafka; set the environment variables in `application.yml` when running outside Compose.

Register a customer with `POST /api/accounts` (`username`, `password`), then use HTTP Basic authentication. Compose provides `operator` and `checker` accounts via environment passwords. The operator creates venues, events, and sessions. Customers browse `/api/venues`, `/api/events`, and `/api/events/{id}/sessions` and submit `POST /api/reservations` with `sessionId` and `participantName`.

Normal reservations are created synchronously. For a hot session, the operator calls `POST /api/admin/sessions/{id}/preheat` before requests arrive. The submit response is `QUEUED`; poll `GET /api/reservations/queue/{sessionId}` for `CONFIRMED:{reservationId}` or `FAILED:{reason}`. Redis Lua atomically holds stock and one claim per user. Kafka consumers create reservations using the same conditional SQL inventory update as normal requests. A failed business check releases the Redis hold.

Reservations expire after 15 minutes unless paid. The operator records **an externally confirmed payment** at `POST /api/admin/reservations/{id}/payment` with `{ "reference": "..." }`. This project does not charge cards or connect to a payment provider. Cancellations of paid reservations create `REFUND_PENDING`; after an external refund, the operator records its reference at `/api/admin/reservations/{id}/refund`. Session cancellation marks all active reservations cancelled or refund pending. `POST /api/check-in` accepts `code` and `device` and records the checker identity. The report is `GET /api/admin/sessions/{id}/report`.

The source repository has no Kafka, Docker, Kubernetes, or Flyway implementation. These additions are limited to the event flow and local deployment. Redis inventory must be preheated explicitly. Committed hot-session cancellations leave a database release record that the scheduled task retries if Redis is unavailable.
