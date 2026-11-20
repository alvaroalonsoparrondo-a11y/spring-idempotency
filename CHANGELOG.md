# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-11-19

### Added

- `ride-service`: `POST /api/v1/rides` books a ride and charges it through Stripe once per `Idempotency-Key` with
  idempify on PostgreSQL - the first answer replayed to every repeat, a key reused with another body refused
  `422`, a request still in flight refused `409`, the key forwarded to Stripe
- `ride-service`: recorded answers cached in Redis, an audit record per booking, the receipt job staged in the
  transaction of the charge through the oncebox outbox and published to Kafka, a reaper of expired idempotency
  records, RFC 9457 problem details, OpenAPI documentation and Swagger UI
- `receipt-service`: receipt jobs consumed once through the oncebox inbox, one receipt per ride, failed jobs moved
  to a dead letter topic
- `ride-simulator`: bookings that lose their first answer and retry under the same key
- Structured JSON logs with a correlation id per request, health probes, Prometheus metrics of the idempotent
  outcomes, the response cache, the outbox and the inbox
- Docker images, a compose stack with PostgreSQL, Redis, Kafka and stripe-mock, an observability profile with
  Prometheus and Grafana dashboards, a Gatling simulation
- CI with every test level on Testcontainers, coverage on Codecov and a load test smoke run; a release workflow
  publishing the images to GitHub Container Registry

[Unreleased]: https://github.com/dmitriy-iliyov/spring-idempotency/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/dmitriy-iliyov/spring-idempotency/releases/tag/v0.1.0
