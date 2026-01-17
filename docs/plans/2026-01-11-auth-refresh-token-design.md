# Auth Refresh Token Design

## Goal
Implement production-ready logout using short-lived access tokens and server-managed refresh tokens with rotation. Logout revokes refresh tokens so new access tokens cannot be minted after logout.

## Summary
- Access tokens remain JWTs used for API authorization (`Authorization: Bearer <token>`).
- Refresh tokens are long-lived, stored server-side (hashed), and delivered via HttpOnly cookie.
- Logout revokes the refresh token and clears the cookie.
- Access tokens expire naturally; refresh controls session continuity.

## Architecture
- **Access token**: short TTL (e.g., 15 minutes), stateless JWT signed with existing secret.
- **Refresh token**: long TTL (e.g., 7-30 days), stored in DB as a hash (SHA-256).
- **Rotation**: on refresh, issue a new refresh token and revoke the old one.
- **Multi-session**: support multiple refresh tokens per user; optional policy to revoke existing tokens on login.

## Data Model
Create `refresh_tokens` table with:
- `id` (PK)
- `user_id` (FK to users)
- `token_hash` (string, unique)
- `issued_at` (timestamp)
- `expires_at` (timestamp)
- `revoked_at` (timestamp, nullable)
- `created_at` (timestamp)
- `updated_at` (timestamp)

## API Surface
- `POST /api/auth/login`
  - Authenticate user.
  - Return `{ accessToken, type }` in body.
  - Set HttpOnly `refreshToken` cookie.
- `POST /api/auth/register`
  - Create user.
  - Return `{ accessToken, type }` in body.
  - Set HttpOnly `refreshToken` cookie.
- `POST /api/auth/refresh`
  - Read refresh token from cookie.
  - Validate against DB (hash match, not revoked, not expired).
  - Issue new access token and rotate refresh token.
- `POST /api/auth/logout`
  - Revoke refresh token and clear cookie.
  - Return 204 (idempotent).
- `GET /api/auth/me`
  - Unchanged; requires access token.

## Security and Error Handling
- Invalid/missing access token -> 401 on protected endpoints.
- Invalid/missing refresh token -> 401 on `/api/auth/refresh`.
- Revoked/expired refresh token -> 401 on `/api/auth/refresh`.
- Logout returns 204 even if token already revoked.

## Testing
- Unit tests for refresh token hashing, expiry, rotation, and revocation.
- Integration tests for login/refresh/logout flows.
- Verify logout prevents refresh from issuing new access tokens.

## Migration Notes
Add Flyway migration to create `refresh_tokens` table. Optionally schedule cleanup of expired tokens in a later iteration.
