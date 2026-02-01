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
    ip_address VARCHAR(45)
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

-- Sessions: token validation (index on token_hash for fast lookups)
-- Note: expiry check done at query time since now() is not immutable
CREATE INDEX idx_sessions_token_hash ON sessions (token_hash);

-- Note: favorites (user_id, country) index created by UNIQUE constraint

