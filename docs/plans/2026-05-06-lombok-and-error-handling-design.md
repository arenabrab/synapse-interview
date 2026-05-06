# Lombok + Error Handling Design

**Date:** 2026-05-06
**Scope:** Production code + one new test class

## Change 1: Lombok

### Annotations applied

| Class | Annotations | Change |
|-------|-------------|--------|
| `OrderController` | `@RequiredArgsConstructor`, `@Slf4j` | Remove explicit constructor |
| `OrderRoutingService` | `@RequiredArgsConstructor`, `@Slf4j` | Remove explicit constructor |
| `DataLoader` | `@Slf4j` only | Keep explicit constructor (`@Value` incompatibility with `@RequiredArgsConstructor`) |
| `ZipMatcher` | `@UtilityClass` | Remove private no-arg constructor; class becomes implicitly `final` |

### Logging

- `DataLoader` — `log.info` after each CSV load: `"Loaded {} products"` and `"Loaded {} suppliers"`
- `OrderRoutingService.route()` — `log.debug` on entry with order ID; `log.info` on result (feasible with supplier count, or infeasible with error count)

### Non-changes
- All model types are records — Lombok does not apply
- `ProductRepository`, `SupplierRepository` — no constructor, no logging needed

## Change 2: Graceful error handling

### New class: `GlobalExceptionHandler`
- Package: `controller`
- Annotation: `@RestControllerAdvice`
- Two `@ExceptionHandler` methods:
  1. `HttpMessageNotReadableException` → HTTP 400, `RoutingResponse.failure("Request body is missing or malformed.")`
  2. `HttpMediaTypeNotSupportedException` → HTTP 415, `RoutingResponse.failure("Content-Type must be application/json.")`

Both return the same `{ "feasible": false, "errors": [...] }` shape as validation failures, keeping the API contract consistent.

### New test class: `GlobalExceptionHandlerTest`
- `@WebMvcTest(OrderController.class)` with `@Import(GlobalExceptionHandler.class)`
- Tests: missing body → 400 + feasible:false, malformed JSON → 400 + feasible:false, wrong content-type → 415 + feasible:false

## Success criteria
- All existing 21 tests pass
- 3 new tests for `GlobalExceptionHandlerTest` pass
- `curl -X POST http://localhost:8080/api/orders/route -H "Content-Type: application/json" -d '{}'` returns `{"feasible":false,"errors":[...]}` (not a Spring error page)
- `curl -X POST http://localhost:8080/api/orders/route` (no body, no content-type) returns `{"feasible":false,...}` at 400/415
