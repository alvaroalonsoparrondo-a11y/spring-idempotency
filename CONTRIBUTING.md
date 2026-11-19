# Contributing to spring-idempotency

Thanks for taking the time to contribute. Bug reports, documentation fixes, tests and code changes are all
welcome, and so are first-time contributors: if something in this guide is unclear, that is a bug in the guide.

## Contents

- [Your first contribution](#your-first-contribution)
- [Taking an issue](#taking-an-issue)
- [Setting up](#setting-up)
- [Project structure](#project-structure)
- [Building and testing](#building-and-testing)
- [Troubleshooting](#troubleshooting)
- [Conventions for every change](#conventions-for-every-change)
- [Conventions for the services' core](#conventions-for-the-services-core)
- [Pull requests and review](#pull-requests-and-review)
- [Reporting bugs and proposing changes](#reporting-bugs-and-proposing-changes)
- [Security issues](#security-issues)

## Your first contribution

1. Find an issue labelled `good first issue` and comment that you are taking it.
2. Fork the repository and set it up as described in [Setting up](#setting-up).
3. Run `./mvnw verify` once before changing anything, so you know the build works on your machine.
4. Make the change. Every issue says what is in scope and how to check the result; look at a neighbouring
   class or test and follow it.
5. Run `./mvnw verify` again and open a pull request with `Fixes #<issue number>` in the description.

Stuck? Ask in the issue. A question there is always better than a pull request that went in the wrong direction,
and nobody expects you to know the codebase.

## Taking an issue

- **Comment on the issue and it is yours.** No plan or approval is needed; the maintainer assigns it to you.
- **Issues are labelled by difficulty.** `good first issue` is small and self-contained; `help wanted` needs more
  context and sometimes a short design note in the issue.
- **Keep it moving.** If there is no pull request or update for two weeks, the issue goes back to the pool so that
  someone else can pick it up. Need more time? Just say so in a comment.
- **Stay in scope.** What an issue lists under "Out of scope" is left out of the pull request; it is usually an
  issue of its own.

## Setting up

Prerequisites:

- **JDK 21** - the build targets Java 21 and fails on older JDKs.
- **Docker** - integration and end-to-end tests start PostgreSQL, Redis, Kafka and stripe-mock with
  Testcontainers. Without Docker the build still passes, only those tests are skipped.
- Maven is not required, use the bundled wrapper `./mvnw`.

Fork the repository on GitHub, then:

```shell
git clone git@github.com:<your-account>/spring-idempotency.git
cd spring-idempotency
git remote add upstream git@github.com:dmitriy-iliyov/spring-idempotency.git
git checkout -b fix/<short-description>
```

**IDE.** Open the root `pom.xml` as a Maven project. No annotation processing or plugin is needed: the code has
no Lombok and no generated sources.

**Keeping your fork up to date:**

```shell
git fetch upstream
git rebase upstream/main
git push --force-with-lease
```

## Project structure

| Module | Purpose |
|---|---|
| `rides-contract` | The receipt job event and the shared log field names |
| `ride-service` | The booking API, Stripe, the idempotency store, the outbox, the reaper |
| `tests/ride-service-e2e-tests` | End-to-end tests of ride-service |
| `receipt-service` | Receipts, consumed through the inbox |
| `tests/receipt-service-e2e-tests` | End-to-end tests of a booking through to its receipt |
| `ride-simulator` | Bookings with lost answers and retries under the same key |
| `tests/load-tests` | Gatling simulation of bookings with repeated keys |

Base package: `io.github.idempotency.<service>`, one package per feature (`booking`, `stripe`, `audit`, `web`,
...). Request and response records live next to the controller that uses them.

Idempotency keys come from [idempify](https://github.com/dmitriy-iliyov/idempify): the claim, the recorded
answers, the fingerprint and the problem types under `https://idempify.io/errors/`. The outbox and the inbox come
from [oncebox](https://github.com/dmitriy-iliyov/oncebox). A defect in either belongs in its own repository; an
issue here can link to it.

## Building and testing

Build everything and run every test level, the coverage check included:

```shell
./mvnw verify
```

One module, or one test class:

```shell
./mvnw -pl ride-service verify
./mvnw -pl ride-service test -Dtest=RideServiceUnitTest
```

An end-to-end module runs against the packaged services, so build it together with what it depends on:

```shell
./mvnw -pl :ride-service-e2e-tests -am verify
./mvnw -pl receipt-service,:receipt-service-e2e-tests -am verify
```

`ride-service` and `rides-contract` fail the build below **80% line coverage** (JaCoCo), `receipt-service` and
`ride-simulator` below 70%. Tests that need Docker are skipped on a machine without it, but CI runs all of them, so
a change touching the database, Redis or Kafka is worth checking with Docker running.

To run a service by hand, start the infrastructure with `docker compose up -d` and run it with the `local`
profile (ride-service) or the defaults (receipt-service); both reach PostgreSQL, Redis and Kafka on localhost.
Elsewhere, point them at their infrastructure through `RIDE_DB_URL`, `RIDE_REDIS_HOST`,
`RIDE_KAFKA_BOOTSTRAP_SERVERS`, `STRIPE_API_KEY`, `STRIPE_API_BASE` and `RECEIPT_DB_URL`,
`RECEIPT_KAFKA_BOOTSTRAP_SERVERS`. ride-service listens on 8080 and receipt-service on 8081; Flyway creates the
tables of each service, idempify and oncebox create their own.

## Troubleshooting

- **Integration tests are skipped although Docker is installed** - Docker is not running, or Testcontainers
  cannot find it. Check `docker info`. With Colima or Rancher Desktop, point Testcontainers at the socket:
  `export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and
  `export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
- **The first build is slow** - Maven downloads its dependencies and Docker pulls the PostgreSQL, Redis, Kafka
  and stripe-mock images once; later builds reuse them.
- **The IDE shows errors that Maven does not** - reimport the root `pom.xml`; see [Setting up](#setting-up).
- **`Port 8080 was already in use`** when running a service - another process holds the port; start the service
  with `SERVER_PORT=<free port>`.
- **The build fails on coverage** - the message names the module. Your change added code without tests for it;
  add them at the level the code belongs to (see the table below).

## Conventions for every change

Code, comments, javadoc and messages are written in English. Keep a change focused on one thing; unrelated
refactoring belongs in a pull request of its own.

**Formatting.** Four-space indent, lines up to about 120 characters, explicit local types (no `var`), no Lombok,
`"...%s".formatted(...)` rather than `String.format`. Arguments that do not fit on one line go one per line at an
indent of eight.

**Tests.** A bug fix comes with a test that fails without the fix; new behaviour comes with tests for it.

| Level | Suffix | `@DisplayName` prefix | What runs |
|---|---|---|---|
| Unit | `*UnitTest` | `UT` | one class, its collaborators mocked |
| Multi-thread | `*MultiThreadTest` | `MT` | a guarantee under concurrency, parties released together |
| Integration | `*IntegrationTest` | `IT` | a framework slice, the application context, or real infrastructure in Testcontainers |
| End-to-end | `*EndToEndTest` | `E2E` | the packaged services started as in production |

- Methods are named `method_whenCondition_shouldResult`, and the display name spells it out.
- The body is split by `// given`, `// when`, `// then` comments; the object under test is called `tested`.
- Assertions are AssertJ; a stub that needs state is a small hand-written class, not a mock with logic.
- No `Thread.sleep` and no dependency on the wall clock: time comes from a `Clock`, asynchronous effects are
  awaited through the module's `Waits` helper. End-to-end tests use fresh identifiers in every case.

**Javadoc and comments.** A comment says what the signature cannot: the contract, what `null` means, the
invariant behind a value, or the reason for a decision that would otherwise look like a mistake. It does not
restate the name of a method.

## Conventions for the services' core

Read this part when your change touches the API, the error responses, the configuration, the database or the
idempify and oncebox wiring.

- **Controllers stay thin**: they bind and validate the request and hand it over to one service call. The
  OpenAPI annotations of a controller live on its `*Api` interface.
- **Errors** reach the client as RFC 9457 problem details. A new error gets its own `type` in `ProblemTypes` and
  its own method in the problem factory; a validation failure lists the offending fields in `errors`; a 500 never
  carries the cause.
- **Configuration** of the application lives in validated properties records (`rides.*`, `stripe.*`), so a wrong
  value stops the startup naming the property. Settings of the libraries stay under `idempify.*` and `oncebox.*`.
- **Schema.** A change is a new migration; a migration that was released is never edited.
- **The charge lock.** The booking runs under an idempotency lease of `ChargeLock.MINUTES`, and the service
  refuses to start if the Stripe timeouts, retries and the connection wait could add up to more
  (`ChargeLockVerifier`). A change to any of them keeps `ChargeLockUnitTest` and `BookingConfigIntegrationTest`
  green.

## Pull requests and review

1. Push your branch to your fork and open a pull request against `main`.
2. Title it like a commit: `type(lowercase summary)`, for example `fix(a repeat is answered under its own key)`
   or `test(the reaper keeps a record still in process)`. Types: `feat`, `fix`, `test`, `docs`, `chore`, `ci`,
   `build`.
3. In the description, say what changed and why, and link the issue (`Fixes #123`).

Pull requests are **squash-merged**: the title becomes the commit message, so the commits inside your branch can
be named however you like.

A pull request is merged when:

- the build is green and coverage stays above the threshold;
- new code has tests at the level it belongs to;
- it changes one thing, and what is out of scope of its issue is left out;
- review comments are resolved.

Expect a first review within a few days. Review comments are suggestions to discuss, not orders: if you disagree,
say why. Do not edit `CHANGELOG.md` - the maintainer writes it at release time from the merged pull requests.

## Reporting bugs and proposing changes

**A bug report** includes:

- what you did - the request with its headers, or the receipt job;
- what you expected and what happened instead;
- the logs or the stack trace, with the `X-Correlation-Id` of the request;
- the versions of Java and Docker, and the commit or release you ran.

**A feature or a larger change** starts as an issue describing the use case. Agreeing on the approach before
writing the code saves both sides a rewrite.

## Security issues

Do not report a vulnerability in a public issue. Use **Report a vulnerability** on the repository's Security tab,
which opens a private advisory visible only to the maintainer.

By contributing you agree that your contribution is licensed under the [Apache License 2.0](LICENSE) and that you
follow the [Code of Conduct](CODE_OF_CONDUCT.md).
