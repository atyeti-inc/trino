# Workflow Execution Context

## 1. Delivery & Git Invariants
- **Remote Invariant**: All working mutations (`git checkout`, `git commit`, `git push`, candidate PR creation) target `origin` (`atyeti-inc/trino`) ONLY. Upstream (`trinodb/trino`) is read-only during development.
- **PR Delivery Contract**:
  - Must comply with upstream Trino contribution guidelines and require Contributor License Agreement (CLA) acceptance at https://github.com/trinodb/cla under Apache-2.0.
  - Linear commit history rebased onto latest upstream `master`. No merge commits allowed (`action-merge: fail` in CI).
  - Every individual commit on the PR branch must compile and pass airstyle/checkstyle independently (`check-commits-dispatcher` / `compile-commit`).
  - Commit messages must satisfy the Airlift commit policy: imperative mood, capitalized title, no trailing period, blank line after subject, wrapped to <= 72 characters.
  - PR description must adhere to `.github/pull_request_template.md`. Issue-closing references (e.g., `fixes #123`) MUST be on bulleted list items starting with `- ` or `* ` to ensure rich link preview rendering.
  - Plan-vs-diff containment: zero opportunistic cleanup or out-of-scope edits.

## 2. Toolchain & Boundary Invariants
- **Primary Languages & Extensions**: Java 25 (source files: `*.java`), TypeScript/TSX for Web UI (`core/trino-web-ui`).
- **Directory Layout & Boundaries**:
  - `core/`: Core query engine, SPI, server, parser, and Web UI.
  - `plugin/`: Connectors and plugin implementations.
  - `lib/`: Shared utility and format libraries.
  - `client/`: CLI, JDBC driver, and client protocols.
- **Prohibited Tooling**: Mocking frameworks (Mockito/PowerMock) are strictly prohibited; hand-crafted test doubles and mocks must be written.

## 3. Verification & Test Harness Contract
- **Test Runner & Verification**:
  - Single test class: `mvnd test -Dair.check.skip-all=true -pl <module> -Dtest=<TestClass>`
  - Module tests: `./mvnw test -Dair.check.skip-all=true -pl <module>`
- **Lint / Typecheck**:
  - Airstyle verification: `mvnd airstyle:check -pl <module>`
  - Airstyle auto-format: `mvnd airstyle:format -pl <module> -Dincludes=**/<FileName>.java`
  - Error Prone static analysis: `mvnd clean test-compile -Dair.check.skip-all=true -P errorprone-compiler -pl <module>`
  - Static checks & Javadoc: `mvnd verify -DskipTests -P ci -pl <module>`
  - License validation: `mvnd license:check`
  - POM format check: `mvnd sortpom:verify`
  - Frontend Web UI verification: `bun run verify` (in `core/trino-web-ui/src/main/resources/webapp`)
- **Test Isolation & Sandbox Requirements**: Write deterministic tests with hand-crafted mocks; use AssertJ for complex assertions and Airlift `Assertions` where applicable.
- **Pass Criteria**: Zero compilation errors, zero test regressions, clean airstyle/checkstyle checks, clean working tree.

## 4. Code Style & Design Invariants
- **Typing Discipline**: Strict Java 25 static typing. Use `var` only when the explicit type is obvious from a constructor initializer; avoid `var` when inferred types obscure clarity. Annotate SQL and regex parameters with `@org.intellij.lang.annotations.Language`.
- **Error Handling**: Throw `TrinoException` with an explicit `ErrorCode`. Never swallow exceptions or catch generic `Throwable`/`Exception` without rethrowing or translating into a typed `TrinoException`. Configuration validation must fail fast at coordinator startup using `@DefunctConfig`.
