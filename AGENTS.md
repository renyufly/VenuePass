# AGENTS.md

## 1. Project Objective

Adapt an existing Java Spring Boot backend repository into an event booking and fulfillment platform.

The target system supports:

- Venue, event, and session management
- Capacity-limited reservations
- Reservation expiration and inventory release
- Payment and cancellation workflows
- Admission check-in and verification
- Basic operational reporting

The existing repository is the primary implementation reference.

**This is a business-domain adaptation and migration task, NOT a greenfield rewrite or architecture redesign.**

Prioritize code reuse, working functionality, consistency, and simplicity.

## 2. Core Principles

- Reuse existing implementations whenever reasonably possible.
- Preserve the existing technical architecture and coding conventions.
- Modify business-specific logic to fit the new domain.
- Prefer minimal, incremental changes over large-scale rewrites.
- Do not introduce unnecessary abstractions, frameworks, or design patterns.
- Do not overengineer the system.
- Treat the target as a practical Spring Boot portfolio project, not a production-scale enterprise platform.

**Do not rebuild functionality that the source repository already implements adequately.**

## 3. Source Repository Adaptation

Before making substantial changes:

1. Inspect the source repository structure.
2. Identify reusable infrastructure and common components.
3. Identify business-specific modules requiring adaptation.
4. Identify obsolete functionality that should be removed.
5. Map existing business concepts to the new event reservation domain.

During implementation:

- Reuse existing controllers, services, repositories, and utilities where appropriate.
- Preserve working Redis, Kafka, database, security, and configuration implementations when applicable.
- Rename and adapt business entities, DTOs, APIs, and services consistently.
- Update business rules rather than merely renaming classes.
- Remove obsolete domain-specific code, configuration, and dependencies.
- Do not retain unrelated business features solely because they exist in the source.
- Avoid copying unused modules into the target repository.
- Do not introduce unnecessary compatibility layers for the old business domain.

## 4. Architecture Constraints

- Keep the application as a conventional Spring Boot monolith.
- Preserve the source repository's module structure unless simplification is clearly beneficial.
- Do not introduce microservices or split the application into additional modules without explicit approval.
- Follow the existing Controller-Service-Repository architecture.
- Prefer existing Spring Boot and Java conventions.
- Do not introduce DDD, CQRS, event sourcing, hexagonal architecture, or other architectural patterns unless explicitly requested.
- Do not create interfaces, factories, strategies, or generic frameworks without a concrete need.
- Avoid speculative extensibility and premature optimization.

## 5. Business Domain Adaptation

The new business domain is event booking and fulfillment.

Use clear and consistent domain terminology, such as:

- Venue
- Event
- Session
- Reservation
- Inventory
- Payment
- CheckIn

Requirements:

- Implement real business behavior, not superficial renaming.
- Keep reservation, payment, cancellation, and check-in state transitions understandable.
- Ensure reservation capacity cannot be oversold.
- Ensure expired or canceled reservations release inventory correctly.
- Handle duplicate requests and messages where they could cause inconsistent business state.
- Prefer straightforward transactional business logic.
- Avoid unnecessary workflow engines and elaborate state machine frameworks.

## 6. Redis and Kafka

Reuse existing Redis and Kafka implementations whenever they fit the new requirements.

- Use Redis atomic operations or Lua scripts for high-concurrency reservation inventory where appropriate.
- Use Kafka for asynchronous processing where it is actually needed.
- Adapt existing concurrency control and messaging logic instead of redesigning it from scratch.
- Keep message producers, consumers, and payloads simple.
- Implement necessary idempotency and consistency safeguards.
- Do not introduce additional messaging systems, distributed transaction frameworks, or complex orchestration mechanisms.
- Do not add unnecessary caching or asynchronous processing to ordinary CRUD operations.

## 7. Database and API Migration

- Adapt the existing database model to the new business domain.
- Preserve reusable database infrastructure and persistence patterns.
- Remove obsolete tables, entities, and repository methods when no longer needed.
- Keep relationships and constraints simple and explicit.
- Update API endpoints, request DTOs, response DTOs, and validation rules consistently.
- Avoid unnecessary database abstraction layers.
- Do not preserve old API compatibility unless explicitly requested.
- Do not use destructive database operations against existing external databases without approval.

## 8. Code Quality and Cleanup

- Follow the source repository's established coding style.
- Prefer simple, readable Java code.
- Reuse existing shared utilities where appropriate.
- Avoid introducing duplicate implementations.
- Remove unused imports, dead code, obsolete configuration, and unrelated business references.
- Do not perform unrelated refactoring.
- Do not add excessive comments, logging, validation, or exception handling.
- Keep changes focused on the migration and new business requirements.

## 9. Testing and Verification

- Prioritize tests for important business flows.
- Test reservation creation, capacity limits, expiration, cancellation, and check-in.
- Test Redis and Kafka integration where relevant.
- Update or remove obsolete tests from the original business domain.
- Do not create trivial tests merely to increase coverage.
- Run the build and relevant tests whenever possible.
- Do not claim functionality is verified unless it has actually been tested.

## 10. Deployment

- Reuse existing Docker, Docker Compose, and Kubernetes configurations where suitable.
- Update application names, environment variables, service names, and configuration references.
- Keep deployment configuration minimal and understandable.
- Do not introduce service meshes, complex CI/CD pipelines, operators, or unnecessary infrastructure.
- Preserve basic health checks, logging, and observability where already available.

## 11. Working Method

For each migration task:

1. Inspect the relevant source code and dependencies.
2. Determine which existing components can be reused.
3. Identify the smallest necessary business changes.
4. Implement the adaptation in the target repository.
5. Remove obsolete source-domain references.
6. Verify compilation and relevant functionality.
7. Briefly summarize completed changes and remaining gaps.

Additional constraints:

- Do not rewrite the entire project when existing code can be adapted.
- Do not introduce new features beyond the target requirements.
- Do not generate extensive design documents unless requested.
- Do not create unnecessary TODOs, placeholder implementations, or empty abstractions.
- Do not silently replace existing functionality with mocks or simplified stubs.
- Ask before making major architectural changes.
- Keep the implementation practical and maintainable.

## 12. Completion Criteria

The migration is complete when:

- The target repository contains a working Spring Boot application.
- The main business flows match the new event reservation domain.
- Reusable infrastructure from the source repository has been preserved where appropriate.
- Unrelated old business code and terminology have been removed.
- Redis, Kafka, and database integrations work for their intended use cases.
- The application builds and relevant tests pass, or limitations are clearly reported.
- The project remains straightforward to understand, run, and explain.

**Final rule: Adapt the existing application. Do not reinvent it.**

## Repository Boundaries

- The source repository:"D:\Code\sky-take-out" is read-only.
- All implementation changes must be made in the target repository:"D:\Code\VenuePass".
- Do not modify or delete files in the source repository.
- Do not copy the source repository's .git directory.
- Do not copy secrets, credentials, local environment files, or generated build artifacts.
- Preserve applicable license and attribution requirements.

## Refactoring Policy

- Business adaptation does not justify unrelated refactoring.
- Do not replace existing working implementations simply because another approach seems cleaner.
- Do not upgrade Java, Spring Boot, or dependency versions unless required.
- Do not change the build system or package organization without a concrete reason.
- Prefer adapting existing implementations over introducing parallel implementations.

## Migration Correctness

- Do not treat class renaming as sufficient business migration.
- Ensure business rules, database models, API semantics, and state transitions match the new domain.
- Remove obsolete workflows instead of disguising them with new names.
- Do not invent missing functionality or claim that incomplete workflows are implemented.
