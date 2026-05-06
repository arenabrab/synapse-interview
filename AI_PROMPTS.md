# AI Prompts

This document logs the significant prompts I used while building this
service, in chronological order. Trivial prompts (autocomplete, syntax
lookup, formatting) are omitted. For each entry I note the context, the
prompt itself (or its substance), and what I took from the response.

Tooling used: Claude (claude.ai/code)

---

## 1. Initial design exploration

**Context:** Starting the project. Wanted a sanity check on overall
approach before writing code — specifically whether AI/MCP belonged in
the solution, whether to use DuckDB vs. in-memory, and how to structure
data access.

**Prompt (summary):** Described the assessment requirements and asked
for a high-level approach, including specific questions about AI
involvement, DuckDB, and HashMap-based ingestion.

**Outcome:** Confirmed my instinct to skip AI/MCP. Adopted the
recommendation to use in-memory maps behind a repository interface
rather than DuckDB. Surfaced an open question about how the supplier
CSV represents zip-code service areas — flagged to resolve before
designing the indexes.

---

## 2. Project initialization / CLAUDE.md

**Context:** Fresh Spring Boot skeleton already scaffolded. Wanted to
initialize the repo for AI-assisted development and capture the tech
stack for future sessions.

**Prompt (summary):** Asked Claude Code to analyze the codebase and
generate a CLAUDE.md with build commands and architecture notes.

**Outcome:** CLAUDE.md created covering Gradle commands (build, run,
single-test), tech stack (Spring Boot 4.0.6, Java 25, Lombok, Actuator,
Docker Compose integration), and behavioral notes about DevTools and the
compose.yaml hook.

---

## 3. CSV structure analysis and API contract design

**Context:** Added products.csv and suppliers.csv to the resources folder.
Needed to understand the data shapes before designing anything, then nail
down the request/response contract with the sample JSON provided by the
assessment.

**Prompt (summary):** Shared the two CSV files and the sample request
JSON. Asked Claude to identify data quirks and flag design implications.
Then shared the sample success/failure response shapes from the brief.

**Outcome:** Identified three zip formats in the supplier CSV (discrete
list, single range, multiple ranges) and the leading-zero stripping issue
(e.g. `2164-2213` represents `02164–02213`). Confirmed `can_mail_order`
column is only meaningful when the request's `mail_order` flag is `true`.
Locked in the response contract: `{ feasible, routing[] }` on success,
`{ feasible, errors[] }` on failure, with `fulfillment_mode` of `"local"`
or `"mail_order"` per routed item.

---

## 4. Implementation planning

**Context:** Full spec known (CSVs, request shape, response shape, mail-order
logic, consolidation requirement). Needed a task-by-task implementation plan
before writing any code.

**Prompt (summary):** Asked Claude to produce a complete implementation plan
covering all files, TDD steps with full code, exact commands, and CI/CD setup
for GitHub Actions → GHCR via `bootBuildImage`.

**Outcome:** Six-task plan covering: (1) dependencies + domain models,
(2) ZipMatcher TDD, (3) repositories + DataLoader, (4) routing service TDD
with greedy set-cover consolidation, (5) controller + MockMvc tests,
(6) GitHub Actions workflow + README. Plan saved to
`docs/superpowers/plans/2026-05-05-order-router.md`.

---


## 5. Task 4 � Request/Response models and OrderRoutingService

**Context:** Models (Product, ZipRange, Supplier), ZipMatcher, repositories,
and DataLoader were already in place. Needed to add the five request/response
record types and implement the core routing logic with greedy set-cover
consolidation, following TDD.

**Prompt (summary):** Asked Claude Code to create the five model records
(OrderRequest, OrderItem, RoutingResponse, SupplierAssignment, RoutedItem),
write OrderRoutingServiceTest first, confirm it failed to compile, then
implement OrderRoutingService so all 10 tests pass.

**What happened / lessons learned:**
- The MINGW64 bash path (/c/Users/...) and the Windows path (C:\Users\...)
  resolve to different filesystem views in this environment: Node.js writes
  and bash writes were hitting different locations. PowerShell was the only
  reliable way to write to the path that Gradle (a Windows JVM process) reads.
- The system linter strips one level of backslash escaping on every file
  write via bash heredocs, so  in a heredoc becomes  on disk,
  which is an illegal Java escape. The fix: avoid the problem entirely by
  using  instead of , or write files via PowerShell.
- Use PowerShell.exe for any file writes that Windows JVM processes (Gradle,
  javac) will subsequently read, especially when the content contains
  backslash escape sequences.

**Outcome:** 10 tests passing (validationFailsOnEmptyItems,
validationFailsOnNullItems, validationFailsOnInvalidZip,
validationCollectsBothErrors, unknownProductReturnsFeasibleFalse,
noEligibleSupplierReturnsFeasibleFalse, localRoutingSuccess,
mailOrderRoutingSuccess, consolidationUsesFewestSuppliers,
routedItemContainsAllFields). BUILD SUCCESSFUL.

---

## 6. Implementation execution and Spring Boot 4.x discoveries

**Context:** Executing the six-task implementation plan using Claude Code's
subagent-driven development workflow (fresh subagent per task, spec + quality
review after each).

**Prompt (summary):** Dispatched subagents to implement each task in order,
reviewed outputs between tasks, and ran a final holistic code review across
the full implementation.

**What happened / lessons learned:**
- Spring Boot 4.x relocated several test APIs compared to 3.x. The controller
  test required `import tools.jackson.databind.ObjectMapper` (Jackson 3.x
  namespace) and `import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
  rather than the Spring Boot 3.x paths. The final code reviewer (trained on 3.x
  knowledge) initially flagged these as wrong; the passing test suite confirmed
  they are correct for this environment.
- `gradlew` was committed with mode `100644` (not executable) because the project
  was initialized on Windows. GitHub Actions runs on Linux and requires `100755`.
  Fix: `git update-index --chmod=+x gradlew` before pushing.
- After the first GHCR push, the package defaults to private. Must manually set
  visibility to Public in GitHub → Packages → Package Settings so the hiring
  team can `docker pull` without authenticating.
- No repository secrets need to be configured — `GITHUB_TOKEN` is provisioned
  automatically by GitHub Actions; the `permissions: packages: write` block in
  the workflow is sufficient.

**Outcome:** 21 tests passing across 4 suites (ZipMatcherTest × 8,
OrderRoutingServiceTest × 10, OrderControllerTest × 2,
SynapseInterviewApplicationTests × 1). Full implementation committed across
6 commits. README, GitHub Actions workflow, and AI_PROMPTS.md all complete.

---