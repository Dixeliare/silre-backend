# Authentication System Documentation

## 🎯 Overview

Hệ thống authentication sử dụng **JWT (JSON Web Token)** với pattern **Access Token + Refresh Token**.

### Key Features:
- ✅ **Stateless Authentication** (không cần server-side sessions)
- ✅ **Access Token** (short-lived: 15 minutes)
- ✅ **Refresh Token** (long-lived: 7 days)
- ✅ **BCrypt Password Hashing** (strength 10)
- ✅ **Token Type Validation** (prevent access token reuse as refresh token)
- ✅ **Account Status Check** (ACTIVE, SUSPENDED, DELETED)

---

## 📁 Architecture

### Components:

1. **JwtConfig** (`config/JwtConfig.java`)
   - JWT configuration (secret, expiration times, issuer)
   - SecretKey bean generation

2. **JwtTokenProvider** (`util/JwtTokenProvider.java`)
   - Token generation (access & refresh)
   - Token validation
   - Claim extraction

3. **JwtAuthenticationFilter** (`config/JwtAuthenticationFilter.java`)
   - Intercepts HTTP requests
   - Validates JWT tokens
   - Sets authentication in SecurityContext

4. **AuthService** (`service/AuthService.java` & `service/impl/AuthServiceImpl.java`)
   - Register, Login, Refresh Token logic
   - Password verification
   - User status validation

5. **AuthController** (`controller/AuthController.java`)
   - REST endpoints for authentication
   - Request/Response handling

6. **SecurityConfig** (`config/SecurityConfig.java`)
   - Security filter chain configuration
   - JWT filter integration
   - Public/Protected endpoints

7. **GlobalExceptionHandler** (`exception/GlobalExceptionHandler.java`)
   - Global exception handling
   - Consistent error responses

---

## 🔐 Security Flow

### 1. Registration Flow:

```
1. POST /api/v1/auth/register
   └─ Request: { displayName, email, password }
   └─ AuthService.register()
      ├─ Check email exists
      ├─ Create user (UserService)
      ├─ Hash password (BCrypt)
      ├─ Generate TSID (internalId)
      ├─ Generate NanoID (publicId)
      └─ Generate tokens (access + refresh)
   └─ Response: { accessToken, refreshToken, tokenType, expiresIn, user }
```

### 2. Login Flow:

```
1. POST /api/v1/auth/login
   └─ Request: { email, password }
   └─ AuthService.login()
      ├─ Find user by email
      ├─ Check account status (ACTIVE)
      ├─ Verify password (BCrypt.matches)
      ├─ Update lastLoginAt
      └─ Generate tokens (access + refresh)
   └─ Response: { accessToken, refreshToken, tokenType, expiresIn, user }
```

### 3. Token Refresh Flow:

```
1. POST /api/v1/auth/refresh
   └─ Request: { refreshToken }
   └─ AuthService.refreshToken()
      ├─ Validate refresh token (signature, expiration)
      ├─ Check token type ("refresh")
      ├─ Extract user info
      ├─ Verify user exists & active
      └─ Generate new access token
   └─ Response: { accessToken (new), refreshToken (same), tokenType, expiresIn, user }
```

### 4. Protected Endpoint Flow:

```
1. GET /api/v1/users/{publicId}
   └─ Request: Authorization: Bearer <accessToken>
   └─ JwtAuthenticationFilter
      ├─ Extract token from Authorization header
      ├─ Validate token (signature, expiration, type)
      ├─ Extract user info (userId, publicId)
      └─ Set authentication in SecurityContext
   └─ Controller processes request
   └─ Response: User data
```

---

## 🔑 JWT Token Structure

### Access Token Claims:

```json
{
  "iss": "silre-backend",           // Issuer
  "sub": "1234567890123456789",     // Subject (userId - TSID)
  "iat": 1705012315000,             // Issued At
  "exp": 1705013215000,             // Expiration (15 min)
  "userId": "1234567890123456789",  // Custom: User ID (TSID)
  "publicId": "Xy9zQ2mP",          // Custom: Public ID (NanoID)
  "type": "access"                  // Custom: Token type
}
```

### Refresh Token Claims:

```json
{
  "iss": "silre-backend",
  "sub": "1234567890123456789",
  "iat": 1705012315000,
  "exp": 1705617115000,             // Expiration (7 days)
  "userId": "1234567890123456789",
  "publicId": "Xy9zQ2mP",
  "type": "refresh"
}
```

---

## 📝 API Endpoints

### Public Endpoints (No Authentication):

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register new user |
| POST | `/api/v1/auth/login` | Login user |
| POST | `/api/v1/auth/refresh` | Refresh access token |
| GET | `/api/v1/auth/validate` | Validate access token |

### Protected Endpoints (Require JWT):

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/users/{publicId}` | Get user by public ID |
| PATCH | `/api/v1/users/{publicId}` | Update user profile |
| GET | `/api/v1/users/check-email` | Check if email exists |

---

## 🛡️ Security Best Practices

### 1. **Password Hashing**
- ✅ BCrypt with strength 10 (good balance)
- ✅ Never store plain passwords
- ✅ Use `PasswordEncoder.matches()` for verification

### 2. **Token Security**
- ✅ Short-lived access tokens (15 min)
- ✅ Long-lived refresh tokens (7 days)
- ✅ Token type validation (prevent misuse)
- ✅ Signature verification (HMAC-SHA-256)
- ✅ Expiration check

### 3. **Account Security**
- ✅ Account status check (ACTIVE only)
- ✅ Soft delete support
- ✅ Last login tracking

### 4. **Error Handling**
- ✅ Generic error messages (don't leak info)
- ✅ Consistent error format
- ✅ Proper HTTP status codes

---

## ⚙️ Configuration

### application.yaml:

```yaml
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-here-change-in-production-minimum-32-chars-long-enough}
  issuer: ${JWT_ISSUER:silre-backend}
  access-token-expiration: ${JWT_ACCESS_TOKEN_EXPIRATION:900000}  # 15 minutes
  refresh-token-expiration: ${JWT_REFRESH_TOKEN_EXPIRATION:604800000}  # 7 days
```

### Environment Variables (Production):

```bash
JWT_SECRET=your-very-long-secret-key-at-least-32-characters
JWT_ISSUER=silre-backend
JWT_ACCESS_TOKEN_EXPIRATION=900000
JWT_REFRESH_TOKEN_EXPIRATION=604800000
```

---

## 🧪 Testing

### 1. Register User:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "John Doe",
    "email": "john@example.com",
    "password": "password123"
  }'
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "internalId": "1234567890123456789",
    "publicId": "Xy9zQ2mP",
    "displayName": "John Doe",
    "email": "john@example.com"
  }
}
```

### 2. Login:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "password123"
  }'
```

### 3. Use Access Token:

```bash
curl -X GET http://localhost:8080/api/v1/users/Xy9zQ2mP \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### 4. Refresh Token:

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }'
```

---

## 🔍 Troubleshooting

### Common Issues:

1. **"Invalid token"**
   - Check token format: `Bearer <token>`
   - Verify token hasn't expired
   - Check JWT secret matches

2. **"Invalid credentials"**
   - Verify email exists
   - Check password is correct
   - Ensure account is ACTIVE

3. **"Token type mismatch"**
   - Don't use access token as refresh token
   - Use correct token type for endpoint

4. **"Account is not active"**
   - Check account status in database
   - Verify `isActive = true` and `accountStatus = 'ACTIVE'`

---

## 📊 Token Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                    Token Lifecycle                          │
└─────────────────────────────────────────────────────────────┘

Registration/Login
    │
    ├─ Generate Access Token (15 min)
    │   └─ Use for API calls
    │
    └─ Generate Refresh Token (7 days)
        └─ Use to refresh access token

Access Token Expires (15 min)
    │
    └─ Use Refresh Token to get new Access Token

Refresh Token Expires (7 days)
    │
    └─ User must login again
```

---

## ✅ Checklist

- [x] JWT Token Provider
- [x] Access Token & Refresh Token
- [x] Password Hashing (BCrypt)
- [x] Authentication Filter
- [x] Register/Login/Refresh endpoints
- [x] Security Configuration
- [x] Exception Handling
- [x] Token Validation
- [x] Account Status Check
- [x] Documentation

---

## 🚀 Next Steps

1. **Rate Limiting**: Add rate limiting for auth endpoints
2. **Password Reset**: Implement password reset flow
3. **Email Verification**: Add email verification
4. **2FA**: Add two-factor authentication
5. **OAuth2**: Integrate OAuth2 providers (Google, GitHub)

---

**Last Updated:** 2025-01-11  
**Version:** 1.0
