# Java Modernization Design

**Date:** 2026-05-06
**Scope:** Production code only (`src/main/java`)
**Approach:** Option B — `var` + stream pipelines where natural

## Changes

### 1. `var` throughout production local variables
Apply `var` to every local variable declaration where the RHS makes the type unambiguous. Affects `OrderRoutingService`, `DataLoader`, and `ZipMatcher`. No behavioral change.

### 2. `DataLoader` — for-loops → stream pipelines
Both `loadProducts` and `loadSuppliers` iterate `parser` with an enhanced for-loop and call `repository.save(...)` inside. Replace each with `parser.stream().map(...).forEach(repo::save)`, keeping parsing logic inline in the map lambda.

### 3. `ZipMatcher.parse()` — imperative accumulator → stream pipeline
The current implementation builds an `ArrayList` inside a for-loop. Replace with:
```java
Arrays.stream(rawServiceZips.split(","))
    .map(String::trim)
    .map(ZipMatcher::parseToken)   // private helper for the range/discrete branch
    .toList();
```
Extract a private `parseToken(String token)` helper so the map step stays readable.

### 4. Fix double `findById` in `OrderRoutingService.route()`
The eligibility loop calls `findById` per item, then a second `productCache` stream calls `findById` again for each item. Restructure: store the resolved `Product` alongside eligible suppliers during the first loop (e.g. a local record or simple pairing), then derive `productCache` from that without re-querying.

### 5. `Collectors.toUnmodifiableSet()` in `DataLoader.loadSuppliers`
The `categories` set is stored on the `Supplier` record and never mutated. Use `Collectors.toUnmodifiableSet()` instead of `Collectors.toSet()` to make the immutability intent explicit.

## Non-changes (intentional)
- `route()`'s eligibility for-loop: retains enhanced for-each (with `var`) because it uses early returns on failure — a stream pipeline here would require awkward error-collection machinery.
- `greedyRoute()`'s inner `computeIfAbsent` loop: retains for-each; forcing into streams does not improve readability.
- Test files: deferred to a later pass.

## Success criteria
- All 21 existing tests continue to pass (`./gradlew test`)
- No new logic introduced — pure style/idiom changes
