# DestinAI Database Schema

## Overview

This document defines the PostgreSQL database schema for the DestinAI MVP. The schema supports:
- Passwordless email-based authentication with one-time tokens
- Server-side session management
- User favorites persistence with notes

**Database**: PostgreSQL 15+  
**Schema**: `public` (default)  
**Primary Key Strategy**: UUIDv4 via `gen_random_uuid()`  
**Timestamp Strategy**: `TIMESTAMPTZ` for all temporal columns

---

## 1. Tables

### 1.1 `users`

Stores registered user accounts. Minimal data: only email for identity.

| Column | Data Type | Constraints | Description |
|--------|-----------|-------------|-------------|
| `id` | `UUID` | `PRIMARY KEY DEFAULT gen_random_uuid()` | Unique user identifier |
| `email` | `VARCHAR(255)` | `NOT NULL` | User's email address (normalized to lowercase at app layer) |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Account creation timestamp |

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Unique constraint on lowercase email for case-insensitive uniqueness
CREATE UNIQUE INDEX idx_users_email_lower ON users (LOWER(email));
```

---

### 1.2 `otp_tokens`

Stores one-time password/magic link tokens for passwordless authentication. Tokens are hashed; plaintext is never stored.

| Column | Data Type | Constraints | Description |
|--------|-----------|-------------|-------------|
| `id` | `UUID` | `PRIMARY KEY DEFAULT gen_random_uuid()` | Unique token identifier |
| `email` | `VARCHAR(255)` | `NOT NULL` | Email address token was issued for |
| `token_hash` | `VARCHAR(255)` | `NOT NULL` | Hashed token (bcrypt or argon2) |
| `expires_at` | `TIMESTAMPTZ` | `NOT NULL` | Token expiration timestamp |
| `consumed_at` | `TIMESTAMPTZ` | `NULL` | When token was used (NULL = unused) |
| `attempt_count` | `INTEGER` | `NOT NULL DEFAULT 0` | Failed verification attempts |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Token creation timestamp |

```sql
CREATE TABLE otp_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Design Notes:**
- No foreign key to `users` table: allows OTP issuance for new signups before user record exists
- `consumed_at` NULL means token is still valid (single-use enforcement)
- `attempt_count` tracks failed verifications; 5 attempts triggers lockout (invalidation)

---

### 1.3 `sessions`

Stores server-side session data. Session tokens are hashed (SHA-256); plaintext sent to client in httpOnly cookie.

| Column | Data Type | Constraints | Description |
|--------|-----------|-------------|-------------|
| `id` | `UUID` | `PRIMARY KEY DEFAULT gen_random_uuid()` | Unique session identifier |
| `user_id` | `UUID` | `NOT NULL REFERENCES users(id) ON DELETE RESTRICT` | Associated user |
| `token_hash` | `VARCHAR(64)` | `NOT NULL` | SHA-256 hash of session token (hex) |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Session creation timestamp |
| `expires_at` | `TIMESTAMPTZ` | `NOT NULL` | Session expiration timestamp |
| `last_accessed_at` | `TIMESTAMPTZ` | `NULL` | Last activity timestamp |
| `user_agent` | `TEXT` | `NULL` | Client user agent string |
| `ip_address` | `INET` | `NULL` | Client IP address |

```sql
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    last_accessed_at TIMESTAMPTZ,
    user_agent TEXT,
    ip_address INET
);
```

**Design Notes:**
- `ON DELETE RESTRICT` prevents accidental user deletion while sessions exist
- `token_hash` is SHA-256 (64 hex characters) for fast lookup
- `last_accessed_at` enables idle timeout tracking
- `user_agent` and `ip_address` for security auditing (optional fields)

---

### 1.4 `favorites`

Stores user's saved destination countries with optional notes.

| Column | Data Type | Constraints | Description |
|--------|-----------|-------------|-------------|
| `id` | `UUID` | `PRIMARY KEY DEFAULT gen_random_uuid()` | Unique favorite identifier |
| `user_id` | `UUID` | `NOT NULL REFERENCES users(id) ON DELETE RESTRICT` | Owning user |
| `country` | `VARCHAR(100)` | `NOT NULL` | Country name |
| `note` | `VARCHAR(100)` | `NULL` | User's note (max 100 chars) |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | When favorite was saved |

```sql
CREATE TABLE favorites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    country VARCHAR(100) NOT NULL,
    note VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT uq_favorites_user_country UNIQUE (user_id, country)
);
```

**Design Notes:**
- Unique constraint on `(user_id, country)` prevents duplicate countries per user (FR-021)
- 50-favorite cap enforced at application layer (not database)
- No `updated_at` column; only `note` can be updated, timestamp not required per PRD
- `ON DELETE RESTRICT` prevents orphaned favorites

---

## 2. Relationships

### Entity Relationship Diagram

```
┌─────────────────┐                    ┌─────────────────┐
│     users       │                    │   otp_tokens    │
├─────────────────┤                    ├─────────────────┤
│ id (PK)         │                    │ id (PK)         │
│ email           │◄─ ─ ─ ─ ─ ─ ─ ─ ─ ─│ email           │
│ created_at      │   (logical, no FK) │ token_hash      │
└────────┬────────┘                    │ expires_at      │
         │                             │ consumed_at     │
         │ 1                           │ attempt_count   │
         │                             │ created_at      │
         │                             └─────────────────┘
    ┌────┴────┐
    │         │
    ▼ *       ▼ *
┌─────────────────┐     ┌─────────────────┐
│    sessions     │     │    favorites    │
├─────────────────┤     ├─────────────────┤
│ id (PK)         │     │ id (PK)         │
│ user_id (FK)────┼─────│ user_id (FK)    │
│ token_hash      │     │ country         │
│ created_at      │     │ note            │
│ expires_at      │     │ created_at      │
│ last_accessed_at│     │ UNIQUE(user_id, │
│ user_agent      │     │        country) │
│ ip_address      │     └─────────────────┘
└─────────────────┘
```

### Relationship Details

| Relationship | Type | Description |
|--------------|------|-------------|
| `users` → `sessions` | One-to-Many | One user can have multiple active sessions |
| `users` → `favorites` | One-to-Many | One user can have up to 50 favorites |
| `otp_tokens` → `users` | Logical (no FK) | OTP linked by email; allows pre-registration token issuance |

---

## 3. Indexes

### 3.1 Primary Key Indexes (Automatic)

All primary keys automatically create unique B-tree indexes.

### 3.2 Explicit Indexes

```sql
-- Users: Case-insensitive email lookup (also enforces uniqueness)
CREATE UNIQUE INDEX idx_users_email_lower ON users (LOWER(email));

-- OTP Tokens: Rate limiting queries (count tokens per email in time window)
CREATE INDEX idx_otp_tokens_email_created ON otp_tokens (email, created_at DESC);

-- OTP Tokens: Find active (unconsumed) tokens for an email
CREATE INDEX idx_otp_tokens_active ON otp_tokens (email) 
    WHERE consumed_at IS NULL;

-- Sessions: Look up sessions by user
CREATE INDEX idx_sessions_user_id ON sessions (user_id);

-- Sessions: Fast token validation for authentication
CREATE INDEX idx_sessions_token_active ON sessions (token_hash) 
    WHERE expires_at > now();

-- Favorites: User's favorites lookup (covered by unique constraint)
-- The UNIQUE constraint on (user_id, country) creates an index automatically
```

### Index Summary

| Table | Index Name | Columns | Type | Purpose |
|-------|------------|---------|------|---------|
| `users` | `idx_users_email_lower` | `LOWER(email)` | Unique B-tree | Email lookup, uniqueness |
| `otp_tokens` | `idx_otp_tokens_email_created` | `(email, created_at DESC)` | B-tree | Rate limiting queries |
| `otp_tokens` | `idx_otp_tokens_active` | `email` | Partial B-tree | Active token lookup |
| `sessions` | `idx_sessions_user_id` | `user_id` | B-tree | User's sessions lookup |
| `sessions` | `idx_sessions_token_active` | `token_hash` | Partial B-tree | Session validation |
| `favorites` | `uq_favorites_user_country` | `(user_id, country)` | Unique B-tree | Dedupe, user favorites |

---

## 4. Row-Level Security (RLS)

For the MVP, Row-Level Security is **not implemented** at the database level. Authorization is enforced at the application layer:

- Session validation happens in Spring Security filters
- All Favorites queries include `user_id` from the authenticated session
- API endpoints reject unauthenticated requests before reaching the database

**Rationale:**
- Single application server with trusted connection
- Server-side sessions handle identity verification
- Simpler to implement and debug for MVP
- Can be added later if multi-tenant or direct DB access is needed

### Future RLS Consideration

If RLS is needed post-MVP, policies would look like:

```sql
-- Example RLS policy for favorites (not implemented for MVP)
ALTER TABLE favorites ENABLE ROW LEVEL SECURITY;

CREATE POLICY favorites_user_isolation ON favorites
    USING (user_id = current_setting('app.current_user_id')::UUID);
```

---

## 5. Complete Schema DDL

```sql
-- =============================================================================
-- DestinAI Database Schema
-- PostgreSQL 15+
-- =============================================================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- TABLES
-- =============================================================================

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- OTP tokens table
CREATE TABLE otp_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Sessions table
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    last_accessed_at TIMESTAMPTZ,
    user_agent TEXT,
    ip_address INET
);

-- Favorites table
CREATE TABLE favorites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    country VARCHAR(100) NOT NULL,
    note VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT uq_favorites_user_country UNIQUE (user_id, country)
);

-- =============================================================================
-- INDEXES
-- =============================================================================

-- Users: case-insensitive unique email
CREATE UNIQUE INDEX idx_users_email_lower ON users (LOWER(email));

-- OTP Tokens: rate limiting queries
CREATE INDEX idx_otp_tokens_email_created ON otp_tokens (email, created_at DESC);

-- OTP Tokens: active token lookup (partial index)
CREATE INDEX idx_otp_tokens_active ON otp_tokens (email) 
    WHERE consumed_at IS NULL;

-- Sessions: user's sessions
CREATE INDEX idx_sessions_user_id ON sessions (user_id);

-- Sessions: token validation (partial index for active sessions)
CREATE INDEX idx_sessions_token_active ON sessions (token_hash) 
    WHERE expires_at > now();

-- Note: favorites (user_id, country) index created by UNIQUE constraint
```

---

## 6. Key Query Patterns

### 6.1 OTP Single-Use Enforcement (Atomic)

```sql
-- Consume OTP token atomically; returns 1 row if successful, 0 if already used/expired
UPDATE otp_tokens 
SET consumed_at = now() 
WHERE id = :token_id 
  AND consumed_at IS NULL 
  AND expires_at > now()
  AND attempt_count < 5
RETURNING id, email;
```

### 6.2 OTP Rate Limiting Check

```sql
-- Count tokens issued to email in last hour
SELECT COUNT(*) 
FROM otp_tokens 
WHERE email = LOWER(:email) 
  AND created_at > now() - INTERVAL '1 hour';
```

### 6.3 OTP Failed Attempt Increment

```sql
-- Increment attempt counter; invalidate if reaching limit
UPDATE otp_tokens 
SET attempt_count = attempt_count + 1,
    consumed_at = CASE WHEN attempt_count + 1 >= 5 THEN now() ELSE consumed_at END
WHERE id = :token_id 
  AND consumed_at IS NULL;
```

### 6.4 Session Validation

```sql
-- Validate session token and get user
SELECT s.id, s.user_id, s.expires_at, u.email
FROM sessions s
JOIN users u ON u.id = s.user_id
WHERE s.token_hash = :token_hash
  AND s.expires_at > now();
```

### 6.5 Favorites Count Check (50 Cap)

```sql
-- Check count before inserting new favorite
SELECT COUNT(*) FROM favorites WHERE user_id = :user_id;
-- Application proceeds with INSERT only if count < 50
```

### 6.6 Save Favorite (with Conflict Handling)

```sql
-- Insert or ignore if duplicate
INSERT INTO favorites (user_id, country, note)
VALUES (:user_id, :country, :note)
ON CONFLICT (user_id, country) DO NOTHING
RETURNING id;
-- Returns NULL if duplicate existed
```

---

## 7. Design Decisions & Notes

### 7.1 UUID vs Serial Primary Keys

**Decision**: UUIDv4 for all primary keys

**Rationale**:
- Prevents enumeration attacks on user IDs
- Safe for distributed systems if needed later
- No sequential pattern to exploit
- Slight storage overhead acceptable for MVP scale

### 7.2 Email Normalization

**Decision**: Normalize to lowercase at application layer, enforce uniqueness via functional index

**Rationale**:
- `LOWER(email)` index handles case-insensitive lookups
- Application layer normalization ensures consistency
- Avoids database triggers or generated columns

### 7.3 OTP Token Storage

**Decision**: Store hash only (bcrypt/argon2 for OTP codes, SHA-256 acceptable for long random tokens)

**Rationale**:
- If database is compromised, tokens cannot be extracted
- Short numeric codes need bcrypt/argon2 (salt + slow hash)
- Long random tokens (magic links) can use SHA-256

### 7.4 Session Token Storage

**Decision**: SHA-256 hash stored in database

**Rationale**:
- Session tokens are high-entropy random values
- SHA-256 provides fast lookup with collision resistance
- Plaintext token in httpOnly cookie is secure in transit

### 7.5 No Soft Deletes

**Decision**: Hard deletes for favorites; no `deleted_at` column

**Rationale**:
- PRD does not require deletion recovery
- Simplifies queries (no `WHERE deleted_at IS NULL`)
- Account deletion out of scope for MVP

### 7.6 Partial Indexes

**Decision**: Use partial indexes for active tokens and sessions

**Rationale**:
- Most queries filter by `consumed_at IS NULL` or `expires_at > now()`
- Partial indexes are smaller and faster for these patterns
- Reduces index maintenance overhead

### 7.7 No Background Cleanup Jobs

**Decision**: Rely on query-level filtering; defer cleanup to post-MVP

**Rationale**:
- Partial indexes keep queries fast despite expired rows
- Simplifies MVP infrastructure
- Can add periodic cleanup when table sizes warrant it

### 7.8 50-Favorite Cap at Application Layer

**Decision**: Enforce via application transaction, not database trigger

**Rationale**:
- Simpler to implement and modify limits
- Better error messaging to users
- Database triggers add complexity and debugging difficulty

---

## 8. Migration Strategy Notes

For Spring Boot with Flyway (recommended):

1. Place migration in `src/main/resources/db/migration/`
2. Naming convention: `V1__create_initial_schema.sql`
3. Each subsequent change: `V2__description.sql`, `V3__description.sql`, etc.

**Initial migration file**: `V1__create_initial_schema.sql` should contain the complete DDL from Section 5.

---

## 9. Unresolved Configuration Items

These values should be configured at application level:

| Item | Suggested Default | Notes |
|------|-------------------|-------|
| OTP expiry duration | 15 minutes | `expires_at = now() + INTERVAL '15 minutes'` |
| Session lifetime | 7 days | `expires_at = now() + INTERVAL '7 days'` |
| Session idle timeout | 24 hours | Check `last_accessed_at` in validation |
| OTP rate limit | 5 per hour per email | Query-based check |
| OTP max attempts | 5 | Stored in `attempt_count` |
| Favorites cap | 50 per user | Application-enforced |

