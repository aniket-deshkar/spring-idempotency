# Contributing

## Development Workflow

1. Create a focused branch from `main`.
2. Keep the public API and distributed state transitions explicit.
3. Add deterministic tests for each behavioral change and adapter tests where storage behavior changes.
4. Run `./mvnw verify` on JDK 21 or newer.
5. Open a pull request describing semantics, transaction assumptions, and compatibility impact.

Use conventional commit subjects such as `feat: add idempotency execution boundary` or `fix: preserve response headers during replay`. Do not commit credentials, IDE state, generated build output, or live service data.

## Code Standards

The Maven verify lifecycle enforces Google Java Format through Spotless and runs PMD. Public APIs should validate input, remain storage-neutral, and document concurrency behavior. Tests must not depend on paid APIs or shared cloud infrastructure.
