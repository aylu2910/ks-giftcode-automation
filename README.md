# KS Gift Code Automation

Automated gift code redemption service for Kingshot. Fetches active codes from the public API and redeems them for registered players using browser automation.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.1 |
| Framework | Spring Boot 4.0 |
| Runtime | Java 21 |
| Browser Automation | Playwright 1.44 |
| Database | H2 (file-based) |
| Migrations | Flyway |
| Async | Kotlin Coroutines 1.8 |
| Observability | Micrometer + Prometheus + Loki + Grafana |
| API Docs | SpringDoc OpenAPI |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    RedemptionService                        │
│         (Scheduler + ApplicationReadyEvent)                 │
└─────────────────┬───────────────────────────────────────────┘
                  │
    ┌─────────────┼─────────────┐
    ▼             ▼             ▼
┌────────┐  ┌───────────┐  ┌────────────────────┐
│ Player │  │ GiftCode  │  │ RedemptionLog      │
│ Service│  │ APIClient │  │ Repository         │
└────────┘  └───────────┘  └────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│                   WorkerOrchestrator                        │
│            (Parallel Playwright workers)                    │
└─────────────────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────┐
│                  RedemptionAutomator                        │
│           (Browser automation per user)                     │
└─────────────────────────────────────────────────────────────┘
```

## Features

- **Scheduled Runs**: Configurable cron schedule (default: every 3 minutes)
- **Parallel Workers**: Splits users across N Playwright browser instances
- **Smart Skipping**: Skips already-redeemed codes and recently claim-limited codes
- **Retry Logic**: Exponential backoff for transient failures (server busy, timeouts)
- **Hard Stop**: Stops all workers when a code is invalid/expired
- **Player Caching**: `@Cacheable` to avoid repeated DB queries
- **Redemption Logging**: Full audit trail in `redemption_log` table

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/players` | List all registered players |
| POST | `/api/players` | Add players (validates via KS API) |
| DELETE | `/api/players/{id}` | Remove a player |
| POST | `/api/automation/run` | Trigger manual redemption run |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Prometheus metrics |
| GET | `/swagger-ui.html` | API documentation |

## Configuration

### application.properties

```properties
# Scheduler (cron expression)
kingshot.scheduler.cron=0 */3 * * * *

# Worker parallelism
kingshot.automation.workers=10

# Playwright timeout (ms)
kingshot.automation.timeout-ms=5000

# Retry config
kingshot.retry.max-attempts=3
kingshot.retry.initial-delay-ms=2000
kingshot.retry.backoff-multiplier=2
```

## Redemption Status Flow

| Status | Behavior |
|--------|----------|
| `SUCCESS` | Code redeemed, move to next |
| `ALREADY_REDEEMED` | Skip, already claimed |
| `INVALID_CODE` | Hard stop all workers |
| `EXPIRED` | Hard stop all workers |
| `CLAIM_LIMIT_REACHED` | Hard stop, skip code for 1h |
| `SERVER_BUSY` | Retry with backoff |
| `INVALID_PLAYER` | Skip user, continue |

## Running Locally

```bash
# Build
./gradlew bootJar

# Run
./gradlew bootRun

# H2 Console available at http://localhost:8080/h2-console
```

## Docker

```bash
# Build and run with observability stack
docker-compose up -d

# Services:
# - App:        http://localhost:8080
# - Prometheus: http://localhost:9090
# - Grafana:    http://localhost:3000 (admin/admin)
# - Loki:       http://localhost:3100
```

## Database Schema

Managed by Flyway migrations in `src/main/resources/db/migration/`:

- `V1__initial_schema.sql` - Players and redemption logs
- `V2__add_claim_limit_reached_status.sql` - Added CLAIM_LIMIT_REACHED status

## Metrics

Exposed via Micrometer at `/actuator/prometheus`:

- `redemption.run.duration` - Duration of each scheduled run
- `worker.execution.duration` - Duration per worker (tagged by worker_id)
