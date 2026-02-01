# DestinAI Test Plan

## 1. Executive summary
- **What we’re testing:** The DestinAI Spring Boot application covering passwordless OTP auth, session handling, questionnaire-driven recommendation generation, and favorites management, with both web UI (Thymeleaf) and JSON APIs. The plan targets the code in controllers, services, repositories, integration clients (OTP email + LLM), and UI flows. 
- **Why:** These flows are the core MVP user journeys and carry the highest business/security risk (auth/session correctness, LLM response integrity, data integrity for favorites). 
- **What “done” means:** All critical flows pass risk-based coverage at unit + integration + E2E levels; required non-functional checks (security, performance smoke, accessibility baseline) pass for release; and agreed quality gates (coverage + no Sev-1/Sev-2 defects) are met.

**Top 5 risks and mitigations**
1. **OTP auth misuse (brute force/rate limiting/OTP reuse).** Mitigate with unit + integration tests for OTP issuance/verification logic, rate limiting behavior, and single-use enforcement; add security checks in E2E for invalid/expired OTP flows. 
2. **Session/authz leakage (cross-user access to favorites).** Mitigate with authz tests in API and UI flows, repository filtering by user, and E2E multi-user tests. 
3. **LLM response invalid JSON or schema drift.** Mitigate with contract tests against the JSON schema, service-level validation tests, and retry/repair test cases in RecommendationService and LlmClient behavior. 
4. **Long-running recommendation requests causing timeouts or failures.** Mitigate with performance smoke tests for request timeouts and retry handling, plus observability checks (logs/metrics). 
5. **Data integrity (favorites limits, notes update, deletes).** Mitigate with integration tests and DB migration validation; add data validation tests for Flyway migrations and repository behavior.

## 2. System overview (derive from @codebase)
### Architecture summary
- **Spring Boot MVC app** with Thymeleaf templates (login, questionnaire, results, favorites). 
- **Modules:** auth (OTP + session), questionnaire (inputs), recommendations (LLM integration), favorites (CRUD), users (user persistence). 
- **API layer:** JSON API controllers for auth, favorites, recommendations plus DTOs/commands/services. 
- **Persistence:** JPA repositories for users, sessions, OTP tokens, favorites, with Flyway migrations. 
- **Integrations:** SMTP OTP sender and OpenRouter LLM client. 

### Primary user journeys and business-critical flows
- Passwordless login (request OTP → verify OTP → create session). 
- Submit questionnaire → generate recommendations (LLM call + JSON validation) → display results. 
- Save favorite destination with notes → list favorites → edit/delete favorites. 
- Logout and session validation via secured endpoints.

### Data flows + integrations
- **Auth:** login controller/API → OTP token persistence → SMTP sender → OTP verify → session persistence. 
- **Recommendations:** questionnaire controller/API → RecommendationService → OpenRouter LLM client → JSON schema validation → results rendering. 
- **Favorites:** favorites controller/API → FavoritesService → FavoriteRepository (user-scoped) → UI/API responses. 

### Assumptions and known gaps
- Assumes rate-limiting and OTP single-use protections are implemented in service logic or configuration. 
- Assumes OpenRouter API key management and SMTP configuration are environment-specific. 
- Assumes session lifecycle (expiry/invalidation) is defined in SecurityConfig. 
- If any of these are missing, prioritize validation of the intended behavior with product and engineering.

## 3. Test scope
### In scope
- OTP auth, session handling, and authorization. 
- Questionnaire validation and recommendation generation. 
- Favorites CRUD and limits per user. 
- UI flows (Thymeleaf screens) for the core journeys. 
- Integration points (SMTP and OpenRouter). 
- Database migrations and repository queries.

### Out of scope
- Non-MVP screens or admin tooling not present in codebase. 
- Load testing beyond basic performance smoke (unless required for release). 
- Localization beyond default language (no i18n assets detected).

### Test levels
- **Unit:** services, command/DTO validation, prompt builder, utility logic. 
- **Component/UI:** Thymeleaf templates + JS interactions. 
- **Integration:** Spring MVC + service + repository with Testcontainers. 
- **Contract:** LLM response schema, SMTP sender contract (interface behavior). 
- **End-to-end:** full user journeys via browser + API. 
- **Exploratory:** focused sessions around edge cases and abuse cases. 
- **Regression:** automated core flows in CI. 
- **UAT:** staged environment acceptance with product.

### Non-functional scope
- **Performance:** recommendation latency, OTP request/verify latency. 
- **Security:** auth/session, CSRF, data leakage, rate limiting, input validation. 
- **Accessibility:** baseline WCAG checks on primary pages. 
- **Reliability:** retries for LLM, error handling. 
- **Compatibility:** major browsers (Chrome, Firefox, Safari). 
- **Localization:** Not applicable unless future i18n added.

## 4. Risk-based prioritization
### Risk matrix (Impact x Likelihood)
| Risk | Impact | Likelihood | Notes |
| --- | --- | --- | --- |
| OTP abuse/replay | High | Medium | Auth security is business-critical. |
| Authz leakage | High | Low/Medium | Favorites user isolation risk. |
| LLM invalid response | High | Medium | Impacts core recommendations flow. |
| LLM timeout/failure | Medium/High | Medium | Long-running calls can degrade UX. |
| Favorites data loss | Medium | Low | Affects user trust. |
| UI flow breakage | Medium | Medium | Primary user path is UI-driven. |

### Test areas ranked by risk
1. OTP issuance/verification + sessions (auth security). 
2. Recommendations flow (LLM response validation + retries). 
3. Authorization on favorites and user-scoped data. 
4. Questionnaire validation and error handling. 
5. UI rendering + JS interactions on primary pages.

### Risk → test type mapping
- **OTP failures/abuse:** unit + integration + E2E + security checks. 
- **Authz leakage:** integration + E2E multi-user checks. 
- **LLM invalid JSON:** unit + contract + integration. 
- **Timeouts/retries:** performance smoke + integration. 
- **Favorites integrity:** integration + data validation tests.

## 5. Test strategy by layer (tailored to @techstack)
### 1) Unit tests
**Purpose:** Validate core logic in services, DTO validation, prompt builder, and error handling.

**What to test**
- OTP creation/verification logic, expiration rules. 
- Recommendation prompt builder output and schema validation rules. 
- Favorites CRUD rules (limit, user-scoped operations). 
- Mapping between DTOs/commands and domain models.

**What NOT to test (anti-patterns)**
- External SMTP or OpenRouter API calls. 
- Thymeleaf rendering or HTTP routing logic.

**Suggested tools**
- JUnit 5, Mockito, AssertJ.

**Example test cases**
- OTP verify rejects expired tokens. 
- OTP verify consumes token only once. 
- Prompt builder produces required JSON sections. 
- Favorites limit (max 50) enforced. 
- Recommendation service handles validation errors.

### 2) Component/UI tests
**Purpose:** Validate Thymeleaf pages and JS behaviors (login, questionnaire, results, favorites).

**What to test**
- Form validation errors and user feedback. 
- JS-driven updates (favorites add/remove, results rendering). 
- CSRF token presence on POST forms.

**What NOT to test**
- Deep business logic; keep in unit/integration tests.

**Suggested tools**
- Playwright or Cypress for UI; Spring MVC test for template rendering.

**Example test cases**
- Login page renders email input + OTP request flow. 
- Questionnaire page shows required fields and client validation. 
- Favorites page lists saved items with edit/delete controls.

### 3) API/Service integration tests
**Purpose:** Validate service + repository + DB integration with realistic data.

**What to test**
- OTP token persistence and session creation. 
- Favorites CRUD with user scoping. 
- Recommendation workflow end-to-end with mock LLM client.

**What NOT to test**
- External SMTP and real OpenRouter calls (mock them).

**Suggested tools**
- Spring Boot Test, Testcontainers (PostgreSQL), WireMock for HTTP mocks.

**Example test cases**
- OTP request creates DB record with hashed token. 
- Favorites list returns only user’s favorites. 
- Recommendation request stores no questionnaire answers.

### 4) Contract tests (consumer/provider)
**Purpose:** Guard external contracts for LLM output schema and SMTP behavior.

**What to test**
- LLM response must match JSON schema (recommendation-schema.json). 
- LLM error responses mapped to meaningful errors. 
- SMTP sender API contract (message format, failure handling).

**What NOT to test**
- Provider uptime or latency (belongs to monitoring).

**Suggested tools**
- JSON schema validation (Everit/NetworkNT), Pact (optional) for HTTP contracts.

**Example test cases**
- LLM response missing required field fails validation. 
- LLM response with extra fields handled gracefully. 
- SMTP sender throws and is handled without crashing.

### 5) End-to-end tests
**Purpose:** Validate full user journeys from UI through backend and DB.

**What to test**
- Login → questionnaire → recommendations → save favorite. 
- Login → favorites page → edit note → delete favorite. 
- Invalid/expired OTP login flow. 
- LLM validation failure shows error message.

**What NOT to test**
- High-volume load or chaos in E2E suite.

**Suggested tools**
- Playwright + Testcontainers for ephemeral DB; seeded test data.

**Example test cases**
- User completes full journey and favorites persist after logout/login. 
- OTP invalid attempts lock out after max tries. 
- Recommendation failure shows retry option or friendly error.

### 6) Data validation tests
**Purpose:** Ensure database migrations, constraints, and repository mapping are correct.

**What to test**
- Flyway migrations apply cleanly. 
- Unique constraints (user + destination). 
- Session/OTP token cleanup behavior (if implemented).

**What NOT to test**
- Application business flows (covered elsewhere).

**Suggested tools**
- Flyway, Testcontainers, JPA repository tests.

**Example test cases**
- Migration V1–V3 apply in order. 
- Favorites unique constraint prevents duplicates. 
- OTP token row cannot be reused.

### 7) Non-functional tests
**Purpose:** Ensure minimal performance/security/a11y quality for MVP.

**What to test**
- Recommendation response time (p95). 
- Security: CSRF, session cookies (Secure/HttpOnly/SameSite). 
- Accessibility: label/input pairing, focus order. 
- Resilience: LLM timeout handling.

**What NOT to test**
- Full-scale load testing unless production scale requires it.

**Suggested tools**
- k6 or Gatling for perf smoke, OWASP ZAP for security checks, axe-core for a11y.

**Example test cases**
- Recommendation call under 30s for typical input. 
- CSRF token enforced on favorites POST. 
- Login page passes axe-core baseline checks.

## 6. Test environments & data
### Environment matrix
| Environment | Purpose | What runs |
| --- | --- | --- |
| Local | Dev + unit tests | Unit + component tests |
| Dev | Integration + API | Integration + contract tests |
| Stage | Release candidate | E2E + UAT + perf smoke |
| Prod | Monitoring only | Synthetic checks, no destructive tests |

### Test data strategy
- Use factories/fixtures for users, OTPs, favorites, and questionnaire inputs. 
- Favor synthetic data; mask any production-like data. 
- Maintain seed scripts for baseline users and favorites.

### Mocking strategy
- **OpenRouter:** mock LLM client responses for deterministic tests. 
- **SMTP:** stub sender to capture outbound emails and verify OTP code format. 

### Seed/reset & isolation
- Use Testcontainers + Flyway for clean DB per test suite. 
- Reset DB per test class or per suite; use transactional tests where possible.

## 7. Coverage map
### Traceability matrix
| Flow/Requirement | Modules/Components | Test Types | Suites |
| --- | --- | --- | --- |
| OTP request + verify | AuthService, LoginController, AuthApiController, OtpTokenRepository | Unit, integration, E2E | auth-unit, auth-integration, auth-e2e |
| Session creation/validation | SecurityConfig, SessionRepository | Unit, integration | session-unit, session-integration |
| Questionnaire submission | QuestionnaireController, RecommendationService | Unit, integration, E2E | rec-unit, rec-integration, rec-e2e |
| LLM JSON validation | RecommendationService, recommendation-schema.json | Unit, contract | rec-schema-contract |
| Favorites CRUD | FavoritesService, FavoritesController/API, FavoriteRepository | Unit, integration, E2E | favorites-unit, favorites-integration, favorites-e2e |

### Coverage holes + closure plan
- **Rate limiting**: if not implemented, add service-level tests + proxy config tests once added. 
- **Session expiration/invalidation**: add tests validating session lifecycle. 
- **LLM retry policy**: define and add integration tests around retry behavior.

## 8. CI/CD and automation plan
- **PR checks:** unit tests + static checks + fast integration tests (mocked LLM/SMTP). 
- **Nightly:** full integration suite + contract tests + UI regression (small E2E). 
- **Pre-release:** full E2E + performance smoke + a11y baseline + security scan.

**Parallelization**
- Split by test layer: unit, integration, UI/E2E. 
- Use Testcontainers caching in CI to reduce startup time.

**Flake management**
- Quarantine flaky tests with labels; auto-retry once. 
- Track flake rate and stabilize before release.

**Artifacts**
- JUnit reports, coverage reports, screenshots/videos for UI, LLM response snapshots.

**Quality gates**
- Minimum 80% unit coverage for services. 
- Zero Sev-1/Sev-2 open defects. 
- Performance: recommendation p95 < 30s in stage. 
- Security: no high vulnerabilities in dependency scans.

## 9. Defect management & reporting
- **Severity definitions:**
  - Sev-1: Auth bypass, data leakage, service down.
  - Sev-2: Core flow broken (OTP login, recommendations, favorites).
  - Sev-3: Minor UI issues or non-critical errors.
- **Triage workflow:** QA logs → daily triage → assign owner → verify fix → close. 
- **SLAs:** Sev-1 within 24h, Sev-2 within 3 days, Sev-3 by next sprint.
- **Metrics:** pass rate, flake rate, escaped defects, mean time to resolution (MTTR).

## 10. Timeline & resourcing
### Phased plan
- **Weeks 1–2:** Build unit test coverage for auth/recommendations/favorites; stand up integration testing with Testcontainers; define test data factories. 
- **Weeks 3–4:** Add contract tests (LLM schema) and small E2E suite for core flows; add a11y baseline checks. 
- **Weeks 5–6:** Performance smoke tests, security scanning, and stabilization; expand regression suite.

### Roles and effort (est.)
- QA lead/test architect (plan + strategy): 0.5 FTE. 
- QA automation engineer (tests + CI): 1 FTE. 
- Dev support for testability (mocks, seams): 0.5 FTE.

### Quick wins vs longer-term
- **Quick wins:** unit tests around OTP/authorization, LLM schema validation, and favorites integrity. 
- **Longer-term:** robust E2E, performance baselines, and security hardening.

## 11. Test cases (starter pack)
| ID | Area | Scenario | Preconditions | Steps | Expected Result | Type | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-001 | Auth | Request OTP with valid email | User exists | Enter email → request OTP | OTP stored + email sent | Integration | P0 |
| TC-002 | Auth | Request OTP with invalid email | None | Enter invalid email → request OTP | Validation error | UI | P1 |
| TC-003 | Auth | Verify OTP with correct code | OTP exists, unexpired | Enter OTP → verify | Session created | E2E | P0 |
| TC-004 | Auth | Verify OTP with wrong code | OTP exists | Enter wrong OTP | Error, no session | E2E | P0 |
| TC-005 | Auth | OTP reuse attempt | OTP already used | Submit same OTP | Rejected | Integration | P0 |
| TC-006 | Auth | OTP expired | OTP expired | Verify OTP | Rejected | Integration | P0 |
| TC-007 | Auth | Rate limit OTP request | Rate limit threshold hit | Request OTP multiple times | Throttled response | Integration | P0 |
| TC-008 | Auth | Logout invalidates session | Logged in | Logout → access favorites | Access denied | E2E | P0 |
| TC-009 | Authz | Cross-user favorites access | User A/B exist | User A requests User B favorite | 403/404 | Integration | P0 |
| TC-010 | Questionnaire | Missing required field | Logged in | Submit incomplete form | Validation error | UI | P1 |
| TC-011 | Questionnaire | Valid submission | Logged in | Complete form → submit | Request accepted | E2E | P0 |
| TC-012 | Recommendations | LLM returns valid JSON | Logged in | Submit questionnaire | Results render | Integration | P0 |
| TC-013 | Recommendations | LLM invalid JSON | Logged in | Submit questionnaire | Error + retry behavior | Integration | P0 |
| TC-014 | Recommendations | LLM timeout | Logged in | Submit questionnaire | Timeout handled gracefully | Integration | P1 |
| TC-015 | Recommendations | Schema missing field | Logged in | Simulate missing field | Validation error | Contract | P0 |
| TC-016 | Favorites | Add favorite | Logged in, results shown | Click save favorite | Favorite saved | E2E | P0 |
| TC-017 | Favorites | Add duplicate favorite | Logged in | Save same destination twice | Duplicate prevented | Integration | P1 |
| TC-018 | Favorites | Edit favorite note | Logged in, favorite exists | Edit note → save | Note updated | E2E | P1 |
| TC-019 | Favorites | Delete favorite | Logged in | Delete favorite | Removed from list | E2E | P0 |
| TC-020 | Favorites | Favorites limit (50) | 50 favorites exist | Add one more | Error/blocked | Integration | P1 |
| TC-021 | Security | CSRF enforced | Logged in | POST without CSRF | Rejected | Integration | P0 |
| TC-022 | Security | Session cookie flags | Logged in | Inspect cookie | Secure/HttpOnly/SameSite set | Integration | P1 |
| TC-023 | UI | Login page accessibility | None | Run axe | No critical a11y issues | A11y | P2 |
| TC-024 | UI | Questionnaire UI layout | Logged in | Open page | All fields render correctly | UI | P2 |
| TC-025 | Abuse | Spam OTP requests from same IP | None | Rapid OTP requests | Throttled + logged | Security | P0 |

## 12. Open questions / assumptions
- **Is rate limiting implemented in the auth flow?** If not, define limits and add to scope. 
- **What is the exact OTP/session expiry policy?** Needed for precise boundary testing. 
- **Do we have a staging environment with SMTP + LLM sandbox credentials?** Needed for pre-release E2E. 
- **Is there a requirement for logging/metrics or audit events?** If yes, add observability tests.

Default assumptions if not clarified:
- OTP expires in 10 minutes and allows 5 attempts. 
- Session idle timeout is 30 minutes. 
- Favorites limit is 50 per user. 
- LLM retry policy: 2 retries with exponential backoff.
