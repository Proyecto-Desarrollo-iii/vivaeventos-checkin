# vivaeventos-checkin

Microservicio de control de ingreso (checkin) para VivaEventos.

## Descripcion

Encargado de:
- Escaneo de QR en puertas
- Validacion de boletas contra `vivaeventos-tickets`
- Control de ingreso (prevencion de doble entrada)
- Modo offline via endpoint de sincronizacion batch

Mantiene su propia bitacora de validaciones (`ticket_validations`). Llama a `vivaeventos-tickets` por HTTP para:
- Consultar la boleta por QR (`GET /api/v1/issued-tickets/qr/{qr}`)
- Marcar la boleta como utilizada (`PUT /api/v1/issued-tickets/{id}/mark-used`)

## Puertos

- HTTP: `8086`
- Management/Actuator: `8087`

## Endpoints

| Metodo | Path | Descripcion |
|--------|------|-------------|
| POST   | `/api/v1/checkin/validate`                  | Validar una boleta en puerta (online) |
| POST   | `/api/v1/checkin/sync`                      | Sincronizar lote de validaciones realizadas offline |
| POST   | `/api/v1/checkin/retry-pending`             | Reintentar `mark-used` de validaciones pendientes de sincronizacion |
| GET    | `/api/v1/checkin/validations/ticket/{id}`   | Historial de validaciones por boleta |
| GET    | `/api/v1/checkin/validations/event/{id}`    | Historial de validaciones por evento |
| GET    | `/api/v1/checkin/stats/event/{id}`          | Agregados por evento (ingresados, rechazados, pendientes, breakdown por puerta) |

## Resiliencia y modo degradado

Cuando `vivaeventos-tickets` esta caido o lento, el circuit breaker (Resilience4j) abre y checkin entra en **modo degradado**:

- Si el QR ya tiene un `SUCCESS` local -> rechaza por `ALREADY_USED` (previene doble ingreso aun sin boleteria)
- Si el QR no se conoce localmente -> autoriza con `pending_mark_used = true` y mensaje "modo degradado, pendiente de sincronizacion"
- Un scheduler (cada 30s, configurable con `checkin.pending-sync.retry-interval-ms`) reintenta `PUT /mark-used` contra tickets cuando vuelve

Para deshabilitar el modo degradado: `checkin.degraded-mode.enabled=false`.

## Trazabilidad

Cada request genera o reusa el header `X-Correlation-Id`. El ID se propaga al log MDC, se persiste en `ticket_validations.correlation_id`, y se reenvia en las llamadas HTTP a tickets, permitiendo reconstruir un incidente punta a punta.

## Variables de entorno

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `JWT_SECRET` | `dGhpc0lzQVZlcnlTZWNyZXRLZXlGb3JWYWlhRXZlbnRvc1RoYXROZWVkczUw` | Clave HMAC para validar JWT |
| `TICKETS_SERVICE_URL` | `http://tickets:8085` | URL base del microservicio de tickets |

## Documentacion

Ver [MULTIREPO.md](../MULTIREPO.md)
