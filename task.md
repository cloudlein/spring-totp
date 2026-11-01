# TOTP & Mandatory MFA on Logout Implementation Roadmap

Technical task backlog and engineering specification for implementing Time-based One-Time Password (TOTP) Two-Factor Authentication with the business rule: **Users are required to enable TOTP prior to logging out**, complemented with **Asynchronous Security Email Notifications via Resend**.

---

## Definition of Done (DoD)
1. Newly registered users have `isTotpEnabled = false` by default.
2. When a user requests `/api/v1/auth/logout`, the system validates the user's TOTP status:
   - If `false` or `null`: The logout request is rejected with HTTP `400 Bad Request` and an explicit error message prompting TOTP activation.
   - If `true`: The token is blacklisted and the logout completes successfully (HTTP `200 OK`).
3. Authenticated users can request TOTP provisioning parameters (Secret Key and QR Code).
4. Authenticated users can verify a 6-digit OTP code to confirm and activate TOTP (`isTotpEnabled = true`).
5. Subsequent login attempts for MFA-enabled accounts:
   - Primary credential validation returns `mfaRequired: true` along with an ephemeral `tempToken`.
   - The TOTP verification endpoint exchanges the `tempToken` and valid 6-digit code for fully authenticated access and refresh tokens.
6. When a user activates TOTP (`enableTotp`), the system dispatches an asynchronous security notification email via Resend to the user's registered email without degrading or blocking the primary auth transaction response.

---

## Current Progress Status (Codebase Audit via CodeGraph)
- **Phase 1: Core TOTP Engine & Utilities**: 100% Completed
  - [x] Task 1.1: `TotpSetupResponse` DTO implemented.
  - [x] Task 1.2: `TotpEnableRequest` DTO implemented.
  - [x] Task 1.3: Interface `TotpService` and implementation `TotpServiceImpl` completed (secure Base32 secret generation, QR code data URI generation, time-based code verification with clock drift tolerance).
- **Phase 2: Ephemeral MFA Token Management**: 100% Completed
  - [x] Task 2.1: `generateMfaToken` & `extractTokenType` implemented in `JwtService` & `JwtServiceImpl`.
  - [x] Task 2.2: MFA temp token isolation implemented in `JwtAuthenticationFilter`.
- **Phase 3: Business Logic & Service Layer (`AuthService`)**: 100% Completed
  - [x] Task 3.1: TOTP provisioning logic (`setupTotp`) implemented in `AuthService` & `AuthServiceImpl`.
  - [x] Task 3.2: TOTP activation logic (`enableTotp`) implemented in `AuthService` & `AuthServiceImpl`.
  - [x] Task 3.3: Mandatory TOTP pre-condition on logout implemented in `AuthServiceImpl.logout`.
  - [x] Task 3.4: Two-step authentication handling in login flow implemented in `AuthServiceImpl.login`.
  - [x] Task 3.5: TOTP verification login logic (`verifyTotpLogin`) implemented in `AuthService` & `AuthServiceImpl`.
- **Phase 4: Controller Endpoints & Security Configuration**: 100% Completed
  - [x] Task 4.1: Expose REST Endpoints in `AuthController` (`GET /totp/setup`, `POST /totp/enable`, `POST /totp/verify`).
  - [x] Task 4.2: Update Spring Security Filter Chain in `SecurityConfig` (`permitAll()` on `/api/v1/auth/totp/verify`, authenticated for others).
- **Phase 5: Verification & Quality Assurance**: In Progress
  - Verification with automated compilation & unit testing ready.
- **Phase 6: Email Notification Integration via Resend (Event-Driven REST API)**: In Progress
  - [x] Task 6.1: Environment configuration & Async/RestClient Setup.
  - [x] Task 6.2: Resend API DTOs, Thymeleaf template, and `EmailService` implementation.
  - [ ] Task 6.3: Domain Event & `@Async` Event Listener.
  - [ ] Task 6.4: Event Publishing integration in `AuthServiceImpl`.
  - [ ] Task 6.5: Unit & integration testing.

---

## Task Checklist

### Phase 1: Core TOTP Engine & Utilities
- [x] **Task 1.1: Create TOTP Setup Response DTO**
  - **Location**: [TotpSetupResponse.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/dto/auth/TotpSetupResponse.java)
  - **Requirements**: Record or class containing:
    - `secretKey` (String): Raw Base32 secret for manual entry in authenticator applications.
    - `qrCodeDataUrl` (String): Base64-encoded image Data URI (e.g., `data:image/png;base64,...`) for direct rendering in an `<img>` tag.
    - `manualUri` (String): Standard RFC 6238 URI format (`otpauth://totp/...`).

- [x] **Task 1.2: Create TOTP Enable Request DTO**
  - **Location**: [TotpEnableRequest.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/dto/auth/TotpEnableRequest.java)
  - **Requirements**: Contains field `code` (String / 6-digit OTP) with Bean Validation constraints (`@NotBlank`, `@Size(min = 6, max = 6)`).

- [x] **Task 1.3: Create `TotpService` Interface & Implementation**
  - **Status**: Completed
  - **Target Files**: [TotpService.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/TotpService.java) & [TotpServiceImpl.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/impl/TotpServiceImpl.java)
  - **Method Specifications**:
    1. `String generateSecretKey()`:
       - Generate a cryptographically secure Base32-encoded random secret (using Spring Security `KeyGenerators.secureRandom()`).
    2. `String generateQrCodeDataUri(String otpAuthUri)`:
       - Render PNG byte stream using `net.glxn.qrgen.javase.QRCode`.
       - Encode to Base64 Data URI: `"data:image/png;base64," + Base64...`.
    3. `boolean verifyCode(String secret, String code)`:
       - Utilize `TimeBasedOneTimePasswordGenerator` from `com.eatthepath.otp.TimeBasedOneTimePasswordGenerator`.
       - Decode the Base32 secret into a `SecretKey`.
       - Validate the code against current system timestamp (`Instant.now()`), allowing standard clock drift tolerance (±1 period / 30 seconds) using constant-time `MessageDigest.isEqual`.
    4. `String buildOtpAuthUri(String secret, String accountName, String issuer)`:
       - Construct standard TOTP URI: `otpauth://totp/{issuer}:{accountName}?secret={secret}&issuer={issuer}&algorithm=SHA1&digits=6&period=30`.

---

### Phase 2: Ephemeral MFA Token Management
- [x] **Task 2.1: Implement Ephemeral MFA Token Generator in `JwtService`**
  - **Target File**: [JwtService.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/JwtService.java) & [JwtServiceImpl.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/impl/JwtServiceImpl.java)
  - **Requirements**:
    - Method `generateMfaToken(UserDetails userDetails)`.
    - Include a distinct claim to indicate token purpose: `claims.put("type", "MFA_TEMP")`.
    - Set short time-to-live (configured via `JWT_MFA_EXPIRATION`).

- [x] **Task 2.2: Enforce Token Isolation in `JwtAuthenticationFilter`**
  - **Target File**: [JwtAuthenticationFilter.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/config/JwtAuthenticationFilter.java)
  - **Requirements**:
    - Regular request authentication filters verify token claims.
    - Explicitly reject tokens containing claim `type == "MFA_TEMP"` from establishing authenticated sessions for standard protected resources (HTTP 401).

---

### Phase 3: Business Logic & Service Layer (`AuthService`)
- [x] **Task 3.1: Implement TOTP Provisioning Logic**
  - **Target Files**: [AuthService.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/AuthService.java) & [AuthServiceImpl.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java)
  - **Method**: `TotpSetupResponse setupTotp(String username)`
  - **Execution Flow**:
    1. Retrieve user by `username`.
    2. Check if a secret already exists or generate a new one if not set yet.
    3. Persist the generated secret to the user record (`isTotpEnabled` remains `false`).
    4. Return `TotpSetupResponse` containing QR Data URI, manual URI, and raw secret.

- [x] **Task 3.2: Implement TOTP Activation Logic**
  - **Target Files**: [AuthService.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/AuthService.java) & [AuthServiceImpl.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java)
  - **Method**: `void enableTotp(String username, String code)`
  - **Execution Flow**:
    1. Fetch the user's secret key from persistence.
    2. Validate the submitted code via `totpService.verifyCode(...)`.
    3. If invalid: throw a client error (`BadRequestException("invalid TOTP verification code")`).
    4. If valid: update `user.setIsTotpEnabled(true)` and persist changes.

- [x] **Task 3.3: Implement Mandatory TOTP Pre-condition on Logout**
  - **Target File**: [AuthServiceImpl.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java)
  - **Method**: `void logout(String token)`
  - **Execution Flow**:
    1. Extract the user identity from the provided bearer token.
    2. Inspect user status `user.getIsTotpEnabled()`.
    3. **Enforcement Rule**:
       - If `isTotpEnabled == null || !isTotpEnabled`: throw `BadRequestException("TOTP Two-Factor Authentication Required")`.
       - If `true`: proceed with token blacklisting in Redis and clear `SecurityContextHolder`.

- [x] **Task 3.4: Adapt Two-Step Authentication in Login Flow**
  - **Target File**: [AuthServiceImpl.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java)
  - **Method**: `AuthResponse login(LoginRequest loginRequest)`
  - **Execution Flow**:
    1. Authenticate username and password credentials using `authenticationManager`.
    2. Evaluate flag `user.getIsTotpEnabled()`.
    3. If `false` or `null`: issue standard access and refresh tokens.
    4. If `true`:
       - Suppress full access token issuance.
       - Generate ephemeral token via `jwtService.generateMfaToken(...)`.
       - Return `AuthResponse` with `mfaRequired: true` and `tempToken: "..."`.

- [x] **Task 3.5: Implement TOTP Verification Endpoint Logic**
  - **Target Files**: [AuthService.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/AuthService.java) & [AuthServiceImpl.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java)
  - **Method**: `AuthResponse verifyTotpLogin(TotpVerifyRequest request)`
  - **Execution Flow**:
    1. Validate `request.tempToken()` (signature, claim `type == "MFA_TEMP"`, non-expired).
    2. Extract username from `tempToken`.
    3. Retrieve user entity and associated `totpSecret`.
    4. Validate the 6-digit code via `totpService.verifyCode(...)`.
    5. If valid: issue full access token and refresh token in `AuthResponse`.

---

### Phase 4: Controller Endpoints & Security Configuration
- [x] **Task 4.1: Expose REST Endpoints in `AuthController`**
  - **Target File**: [AuthController.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/controller/AuthController.java)
  - Endpoints declared:
    - `GET /api/v1/auth/totp/setup` (Authenticated, returns `TotpSetupResponse`)
    - `POST /api/v1/auth/totp/enable` (Authenticated, Body: `TotpEnableRequest`)
    - `POST /api/v1/auth/totp/verify` (Public, Body: `TotpVerifyRequest`, returns full `AuthResponse`)

- [x] **Task 4.2: Update Spring Security Filter Chain**
  - **Target File**: [SecurityConfig.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/config/SecurityConfig.java)
  - Configure route authorization:
    - Allow unauthenticated access (`permitAll()`) to `/api/v1/auth/totp/verify`.
    - Require active authentication (`authenticated()`) for `/api/v1/auth/totp/setup` and `/api/v1/auth/totp/enable`.

---

### Phase 5: Verification & Quality Assurance (Testing Guide)

- [ ] **Test Case 1: Initial Registration & Login**
  - Register a new user -> Execute primary login.
  - Verification: Standard response containing full `token`; `mfaRequired` is `false` or omitted.
- [ ] **Test Case 2: Attempt Immediate Logout (Expected Failure)**
  - Invoke `POST /api/v1/auth/logout` using the active Bearer token.
  - Verification: HTTP `400 Bad Request` returned with required TOTP message. Token remains valid and unblacklisted.
- [ ] **Test Case 3: Provision TOTP Secret & QR Code**
  - Invoke `GET /api/v1/auth/totp/setup` with the Bearer token.
  - Verification: HTTP `200 OK` with Base64 QR code and secret string. Import key into Google Authenticator or Authy.
- [ ] **Test Case 4: Enable TOTP with Invalid vs Valid Code**
  - Invoke `POST /api/v1/auth/totp/enable` with invalid code `000000` -> Expect HTTP `400 Bad Request`.
  - Invoke with valid 6-digit code from authenticator app -> Expect HTTP `200 OK`.
- [ ] **Test Case 5: Retry Logout (Expected Success)**
  - Invoke `POST /api/v1/auth/logout` with Bearer token.
  - Verification: HTTP `200 OK`. Token is blacklisted in Redis.
- [ ] **Test Case 6: Login Flow with MFA Enforced**
  - Invoke `POST /api/v1/auth/login`.
  - Verification: Returns `mfaRequired: true` and `tempToken`; no standard access token provided.
  - Invoke `POST /api/v1/auth/totp/verify` with `tempToken` and current 6-digit code.
  - Verification: Successfully returns full `token` and `refreshToken`.
- [ ] **Test Case 7: Asynchronous Security Email Dispatch on TOTP Activation**
  - Invoke `POST /api/v1/auth/totp/enable` with valid 6-digit code.
  - Verification: HTTP `200 OK` returns promptly without waiting for remote HTTP email response.
  - Inspect server application logs: Confirm asynchronous dispatch event was processed and Resend API returned email ID successfully.

---

### Phase 6: Email Notification Integration via Resend (Option A: Event-Driven REST API)

- [x] **Task 6.1: Environment & Async/RestClient Configuration**
  - **Status**: Completed
  - **Target Files**:
    - `src/main/resources/application.yaml`
    - `.env` & `env.example`
    - [AsyncConfig.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/config/AsyncConfig.java)
    - [ResendProperties.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/config/ResendProperties.java)
    - [RestClientConfig.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/config/RestClientConfig.java)
  - **Requirements**:
    1. Define environment properties:
       - `application.resend.api-key=${RESEND_API_KEY}`
       - `application.resend.from-email=${RESEND_FROM_EMAIL}`
       - `application.resend.base-url=${RESEND_BASE_URL}`
    2. Create `AsyncConfig` with `@Configuration`, `@EnableAsync`, and custom executor `emailExecutor`.
    3. Create `ResendProperties` using `@ConfigurationProperties(prefix = "application.resend")` with `@ConfigurationPropertiesScan` on main application.
    4. Create `RestClientConfig` declaring `RestClient` bean pre-configured with timeout, `baseUrl`, and authorization header.

- [x] **Task 6.2: Create Resend DTOs & `EmailService` Interface and Implementation**
  - **Status**: Completed
  - **Target Files**:
    - [ResendEmailRequest.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/dto/email/ResendEmailRequest.java)
    - [ResendEmailResponse.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/dto/email/ResendEmailResponse.java)
    - [EmailService.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/EmailService.java)
    - [EmailServiceImpl.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/impl/EmailServiceImpl.java)
    - [totp-enabled.html](file:///e:/project/spring-totp/src/main/resources/templates/totp-enabled.html)
  - **Requirements & Method Specifications**:
    1. `ResendEmailRequest`: DTO with fields `from`, `to`, `subject`, and `htmlContent` (Lombok builder/getters/setters).
    2. `ResendEmailResponse`: DTO with field `id`.
    3. Interface `EmailService`:
       - `void sendEmail(ResendEmailRequest request)`
       - `void senTotpEnabledAlert(String to, String username)`
    4. Implementation `EmailServiceImpl`:
       - Injects configured `RestClient`, `ResendProperties`, and Thymeleaf `TemplateEngine`.
       - Implements `sendEmail`: Executes `POST /emails` via `RestClient`.
       - Implements `senTotpEnabledAlert`: Renders `totp-enabled.html` template via Thymeleaf with dynamic context variables (`to`, `username`, `enabledAt`, `supportEmail`).
       - Enforces error containment: Logs errors without interrupting callers.

- [ ] **Task 6.3: Implement Domain Event & Asynchronous Notification Listener**
  - **Status**: Not Started
  - **Target Files**:
    - [TotpEnabledEvent.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/event/TotpEnabledEvent.java)
    - [TotpSecurityNotificationListener.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/event/listener/TotpSecurityNotificationListener.java)
  - **Requirements & Execution Flow**:
    1. Create event object `TotpEnabledEvent` (record) with fields:
       - `email` (String)
       - `username` (String)
       - `timestamp` (Instant)
    2. Create listener component `TotpSecurityNotificationListener`:
       - Annotate method `onTotpEnabled(TotpEnabledEvent event)` with `@Async` and `@EventListener` (or `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`).
       - Invoke `emailService.sendTotpEnabledAlert(event.email(), event.username())`.
       - Wrap with try-catch to ensure failure in mail transport never rolls back database transactions.

- [ ] **Task 6.4: Integrate Event Dispatch in `AuthServiceImpl`**
  - **Status**: Not Started
  - **Target File**: [AuthServiceImpl.java](file:///e:/project/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java)
  - **Method**: `void enableTotp(String username, String code)`
  - **Execution Flow**:
    1. Inject `ApplicationEventPublisher`.
    2. In `enableTotp`:
       - Execute standard code validation and set `user.setIsTotpEnabled(true)` then save via `userRepository.save(user)`.
       - Immediately dispatch domain event:
         `eventPublisher.publishEvent(new TotpEnabledEvent(user.getEmail(), user.getUsername(), Instant.now()))`.

- [ ] **Task 6.5: Verification & Quality Assurance**
  - **Status**: Not Started
  - **Target Files**:
    - [ResendEmailServiceImplTest.java](file:///e:/project/spring-totp/src/test/java/com/java/springtotp/service/ResendEmailServiceImplTest.java)
    - [AuthServiceImplTest.java](file:///e:/project/spring-totp/src/test/java/com/java/springtotp/service/AuthServiceImplTest.java)
  - **Requirements**:
    1. Unit test `ResendEmailServiceImpl`: Validate REST request body and header serialization using `MockRestServiceServer` or Mockito.
    2. Unit test `AuthServiceImpl`: Verify `eventPublisher.publishEvent(any(TotpEnabledEvent.class))` is called once on successful TOTP enable.
    3. Full test suite verification via `rtk ./gradlew test`.

