# TOTP & Mandatory MFA on Logout Implementation Roadmap

Technical task backlog and engineering specification for implementing Time-based One-Time Password (TOTP) Two-Factor Authentication with the business rule: **Users are required to enable TOTP prior to logging out**, complemented with **Asynchronous Security Email Notifications via Resend** and **Risk-Based New Device / Suspicious Location Login Security Alerts**.

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
7. When a successful login occurs (both initial non-MFA login and completed MFA verification):
   - The system inspects client metadata (User-Agent device fingerprint, IP address, and GeoIP location).
   - If the device and IP/location are already recognized for this user: session metadata is refreshed without triggering redundant notifications (preventing **Alert Fatigue**).
   - If the login originates from a **New Device / Browser** or an **Anomalous Location / IP**: an asynchronous security alert email is dispatched via Resend containing login timestamp, parsed device/browser details, IP address, estimated location, and a prominent emergency action link ("Bukan Anda? Amankan Akun").

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
- **Phase 5: Verification & Quality Assurance**: 100% Completed
  - [x] Verification with automated compilation & unit testing (TotpServiceTest, AuthControllerTest with 7 scenarios).
- **Phase 6: Email Notification Integration via Resend (Event-Driven REST API)**: 100% Completed
  - [x] Task 6.1: Environment configuration & Async/RestClient Setup.
  - [x] Task 6.2: Resend API DTOs, Thymeleaf template, and `EmailService` implementation.
  - [x] Task 6.3: Domain Event & `@Async` Event Listener.
  - [x] Task 6.4: Event Publishing integration in `AuthServiceImpl`.
  - [x] Task 6.5: Unit & integration testing.
- **Phase 7: Risk-Based New Device & Suspicious Location Security Alerts**: In Progress (~75%)
  - [x] Task 7.1: Entity & Repository for Known Devices (`UserDevice`, `UserDeviceRepository`, & Flyway migration `V4__create_user_device_table.sql`).
  - [x] Task 7.2: Device Metadata & GeoIP Extraction Utility (`DeviceDetectionService`, `IpUtils`, `Fingerprints`, `NoOpGeoIpResolver`, persistent cookie token `__Host-device_id` extraction).
  - [/] Task 7.3: Device Verification & Risk-Based Evaluation Logic in `AuthService` & `DeviceVerificationService` - *In Progress (~75% - Known device update with `fingerPrint` audit trail complete; `newDevice` persistence via `userDeviceRepository.save(newDevice)` in `else` block implemented; event publishing via `eventPublisher.publishEvent`, null-safe fallback for `deviceHash`, TOTP login flow hook in `verifyTotpLogin`, and `AuthServiceImplTest` signature update pending)*.
  - [x] Task 7.4: Domain Event & `@Async` Security Notification Listener (`NewDeviceLoginEvent` & `NewDeviceSecurityListener`).
  - [/] Task 7.5: Security Notification Template (`new-device-login.html`) & `EmailService` Dispatch - *In Progress (~70% - Template created, context attributes populated; Resend dispatch via `sendEmail` and `EmailServiceImplTest` constructor fix pending)*.
  - [ ] Task 7.6: Verification & Quality Assurance (Fix existing test compilation errors in `AuthServiceImplTest` and `EmailServiceImplTest`, add unit & integration tests for Phase 7).

---

## Task Checklist

### Phase 1: Core TOTP Engine & Utilities
- [x] **Task 1.1: Create TOTP Setup Response DTO**
  - **Location**: [TotpSetupResponse.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/dto/auth/TotpSetupResponse.java)
  - **Requirements**: Record or class containing:
    - `secretKey` (String): Raw Base32 secret for manual entry in authenticator applications.
    - `qrCodeDataUrl` (String): Base64-encoded image Data URI (e.g., `data:image/png;base64,...`) for direct rendering in an `<img>` tag.
    - `manualUri` (String): Standard RFC 6238 URI format (`otpauth://totp/...`).

- [x] **Task 1.2: Create TOTP Enable Request DTO**
  - **Location**: [TotpEnableRequest.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/dto/auth/TotpEnableRequest.java)
  - **Requirements**: Contains field `code` (String / 6-digit OTP) with Bean Validation constraints (`@NotBlank`, `@Size(min = 6, max = 6)`).

- [x] **Task 1.3: Create `TotpService` Interface & Implementation**
  - **Status**: Completed
  - **Target Files**: [TotpService.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/TotpService.java) & [TotpServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/TotpServiceImpl.java)
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
  - **Target File**: [JwtService.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/JwtService.java) & [JwtServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/JwtServiceImpl.java)
  - **Requirements**:
    - Method `generateMfaToken(UserDetails userDetails)`.
    - Include a distinct claim to indicate token purpose: `claims.put("type", "MFA_TEMP")`.
    - Set short time-to-live (configured via `JWT_MFA_EXPIRATION`).

- [x] **Task 2.2: Enforce Token Isolation in `JwtAuthenticationFilter`**
  - **Target File**: [JwtAuthenticationFilter.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/config/JwtAuthenticationFilter.java)
  - **Requirements**:
    - Regular request authentication filters verify token claims.
    - Explicitly reject tokens containing claim `type == "MFA_TEMP"` from establishing authenticated sessions for standard protected resources (HTTP 401).

---

### Phase 3: Business Logic & Service Layer (`AuthService`)
- [x] **Task 3.1: Implement TOTP Provisioning Logic**
  - **Target Files**: [AuthService.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/AuthService.java) & [AuthServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java)
  - **Method**: `TotpSetupResponse setupTotp(String username)`
  - **Execution Flow**:
    1. Retrieve user by `username`.
    2. Check if a secret already exists or generate a new one if not set yet.
    3. Persist the generated secret to the user record (`isTotpEnabled` remains `false`).
    4. Return `TotpSetupResponse` containing QR Data URI, manual URI, and raw secret.

- [x] **Task 3.2: Implement TOTP Activation Logic**
  - **Target Files**: [AuthService.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/AuthService.java) & [AuthServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java)
  - **Method**: `void enableTotp(String username, String code)`
  - **Execution Flow**:
    1. Fetch the user's secret key from persistence.
    2. Validate the submitted code via `totpService.verifyCode(...)`.
    3. If invalid: throw a client error (`BadRequestException("invalid TOTP verification code")`).
    4. If valid: update `user.setIsTotpEnabled(true)` and persist changes.

- [x] **Task 3.3: Implement Mandatory TOTP Pre-condition on Logout**
  - **Target File**: [AuthServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java)
  - **Method**: `void logout(String token)`
  - **Execution Flow**:
    1. Extract the user identity from the provided bearer token.
    2. Inspect user status `user.getIsTotpEnabled()`.
    3. **Enforcement Rule**:
       - If `isTotpEnabled == null || !isTotpEnabled`: throw `BadRequestException("TOTP Two-Factor Authentication Required")`.
       - If `true`: proceed with token blacklisting in Redis and clear `SecurityContextHolder`.

- [x] **Task 3.4: Adapt Two-Step Authentication in Login Flow**
  - **Target File**: [AuthServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java)
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
  - **Target Files**: [AuthService.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/AuthService.java) & [AuthServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java)
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
  - **Target File**: [AuthController.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/controller/AuthController.java)
  - Endpoints declared:
    - `GET /api/v1/auth/totp/setup` (Authenticated, returns `TotpSetupResponse`)
    - `POST /api/v1/auth/totp/enable` (Authenticated, Body: `TotpEnableRequest`)
    - `POST /api/v1/auth/totp/verify` (Public, Body: `TotpVerifyRequest`, returns full `AuthResponse`)

- [x] **Task 4.2: Update Spring Security Filter Chain**
  - **Target File**: [SecurityConfig.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/config/SecurityConfig.java)
  - Configure route authorization:
    - Allow unauthenticated access (`permitAll()`) to `/api/v1/auth/totp/verify`.
    - Require active authentication (`authenticated()`) for `/api/v1/auth/totp/setup` and `/api/v1/auth/totp/enable`.

---

### Phase 5: Verification & Quality Assurance (Testing Guide & Target Files)

> [!TIP]
> **Core Testing Concepts:**
> - **Unit Testing (`@ExtendWith(MockitoExtension.class)`)**: Tests a single class in complete isolation without loading the Spring ApplicationContext (high execution speed). Best suited for pure business and mathematical logic such as RFC 6238 hashing, Base32 encoding, and clock drift tolerance validation in `TotpService`.
> - **Integration Testing (`@SpringBootTest` + `@AutoConfigureMockMvc`)**: Bootstraps the complete Spring ApplicationContext and validates the entire request lifecycle: HTTP Request -> Security Filter Chain -> Controller -> Service Layer -> Persistence / In-Memory Cache (Database/Redis). Best suited for verifying the end-to-end 7 TOTP authentication lifecycle test cases below.

#### Target Test File Locations
1. **Service Layer Unit Tests**:
   - [TotpServiceTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/service/TotpServiceTest.java)
   *(Scope: Pure logic for `generateSecretKey`, OTP code verification, and ±30-second clock drift window validation)*
2. **Integration / Controller Tests**:
   - [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
   *(Scope: End-to-end MockMvc HTTP lifecycle covering the 7 Test Cases below)*
3. **Manual / Interactive API Testing (Optional IDE Tooling)**:
   - [auth-totp-api.http](file:///home/udin/projects/spring-totp/src/test/resources/auth-totp-api.http)
   *(Scope: REST Client request specification for manual execution via IntelliJ HTTP Client, VS Code, or Postman)*

---

#### Fundamental MockMvc Pattern (Reference Skeleton)
*Fundamental HTTP test structure in Spring Boot to adapt for each scenario:*

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Baseline pattern: Execute HTTP request and verify response payload")
    void exampleTest() throws Exception {
        // 1. Arrange: Prepare request payload DTO
        var request = new LoginRequest("user", "password");

        // 2. Act & Assert: Execute HTTP request and validate status code and JSON path values
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }
}
```

---

#### Comprehensive 7 Test Case Scenarios (Engineering Specification & Guidance)

- [x] **Test Case 1: Initial Registration & Login**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    1. Dispatch `POST /api/v1/auth/register` with unique user credentials.
    2. Dispatch `POST /api/v1/auth/login` using the newly registered credentials.
  - **Key Assertions**: HTTP `200 OK`, `data.token` is present and non-empty, and `data.mfaRequired` evaluates to `false` (or absent/null).
  - **Engineering Clue**: Persist the issued JWT access token from the login response to authenticate subsequent protected endpoints as a Bearer token.

- [x] **Test Case 2: Attempt Immediate Logout (Expected Failure)**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    - Dispatch `POST /api/v1/auth/logout` supplying header `Authorization: Bearer <token_from_case_1>`.
  - **Key Assertions**:
    - Status code must be HTTP `400 Bad Request` (`status().isBadRequest()`).
    - Error response body must explicitly indicate that TOTP Two-Factor Authentication activation is mandatory prior to session termination.
  - **Engineering Clue**: Append `.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)` to the MockMvc request builder.

- [x] **Test Case 3: Provision TOTP Secret & QR Code**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    - Dispatch `GET /api/v1/auth/totp/setup` with an active Bearer token.
  - **Key Assertions**:
    - Status code HTTP `200 OK`.
    - Response payload contains `secretKey` (non-blank Base32 format) and `qrCodeDataUrl` starting with standard prefix `data:image/png;base64,`.
  - **Engineering Clue**: Extract the generated `secretKey` from the JSON response to compute valid OTP verification codes in Test Case 4.

- [x] **Test Case 4: Enable TOTP with Invalid vs Valid Code**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    - **Negative Path**: Dispatch `POST /api/v1/auth/totp/enable` with body `{ "code": "000000" }` -> Assert HTTP `400 Bad Request`.
    - **Positive Path**: Dispatch `POST /api/v1/auth/totp/enable` with a valid 6-digit OTP code calculated against the user's `secretKey`.
  - **Key Assertions**: Valid code request returns HTTP `200 OK`.
  - **Engineering Clue**: Autowire or instantiate `TotpService` within your test class to derive the synchronized one-time passcode for `Instant.now()`.

- [x] **Test Case 5: Retry Logout (Expected Success)**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    - Following successful TOTP activation in Case 4, reissue `POST /api/v1/auth/logout` using the active Bearer token.
  - **Key Assertions**:
    - HTTP `200 OK`.
    - The bearer token is invalidated and successfully blacklisted in Redis.

- [x] **Test Case 6: Login Flow with MFA Enforced**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    - **Step 1**: Dispatch `POST /api/v1/auth/login` for the MFA-enabled account.
      - Assertions: HTTP `200 OK`, `data.mfaRequired == true`, `data.tempToken` is present and populated, whereas standard `data.token` is null or suppressed.
    - **Step 2**: Dispatch `POST /api/v1/auth/totp/verify` submitting `{ "tempToken": "...", "code": "<valid_otp_code>" }`.
      - Assertions: HTTP `200 OK`, returning fully authenticated `token` (access token) and `refreshToken`.
  - **Engineering Clue**: Endpoint `/api/v1/auth/totp/verify` is publicly accessible (`permitAll()` in `SecurityConfig`), requiring no Bearer authentication header.

- [x] **Test Case 7: Asynchronous Security Email Dispatch on TOTP Activation**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    - Mock the email delivery component via `@MockitoBean` (or `@MockBean`).
    - Execute TOTP activation (`POST /api/v1/auth/totp/enable`).
  - **Key Assertions**:
    - Primary HTTP response returns immediately without latency from external email transport.
    - Verify asynchronous alert dispatch via `Mockito.verify(..., Mockito.timeout(2000).times(1))`.
  - **Engineering Clue**: Because the notification listener executes asynchronously (`@Async`) and binds to transaction commit (`AFTER_COMMIT`), configure `Mockito.timeout(...)` so the verifier polls the background execution window without premature failure.

---

### Phase 6: Email Notification Integration via Resend (Option A: Event-Driven REST API)

- [x] **Task 6.1: Environment & Async/RestClient Configuration**
  - **Status**: Completed
  - **Target Files**:
    - `src/main/resources/application.yaml`
    - `.env` & `env.example`
    - [AsyncConfig.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/config/AsyncConfig.java)
    - [ResendProperties.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/config/ResendProperties.java)
    - [RestClientConfig.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/config/RestClientConfig.java)
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
    - [ResendEmailRequest.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/dto/email/ResendEmailRequest.java)
    - [ResendEmailResponse.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/dto/email/ResendEmailResponse.java)
    - [EmailService.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/EmailService.java)
    - [EmailServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/EmailServiceImpl.java)
    - [totp-enabled.html](file:///home/udin/projects/spring-totp/src/main/resources/templates/totp-enabled.html)
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

- [x] **Task 6.3: Implement Domain Event & Asynchronous Notification Listener**
  - **Status**: Completed
  - **Target Files**:
    - [TotpEnabledEvent.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/event/TotpEnabledEvent.java)
    - [TotpSecurityNotificationListener.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/event/TotpSecurityNotificationListener.java)
  - **Requirements & Execution Flow**:
    1. Create event object `TotpEnabledEvent` (record) with fields:
       - `email` (String)
       - `username` (String)
       - `timestamp` (Instant)
    2. Create listener component `TotpSecurityNotificationListener`:
       - Annotate method `onTotpEnabled(TotpEnabledEvent event)` with `@Async` and `@EventListener` (or `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`).
       - Invoke `emailService.sendTotpEnabledAlert(event.email(), event.username())`.
       - Wrap with try-catch to ensure failure in mail transport never rolls back database transactions.

- [x] **Task 6.4: Integrate Event Dispatch in `AuthServiceImpl`**
  - **Status**: Completed
  - **Target File**: [AuthServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java)
  - **Method**: `void enableTotp(String username, String code)`
  - **Execution Flow**:
    1. Inject `ApplicationEventPublisher`.
    2. In `enableTotp`:
       - Execute standard code validation and set `user.setIsTotpEnabled(true)` then save via `userRepository.save(user)`.
       - Immediately dispatch domain event:
         `eventPublisher.publishEvent(new TotpEnabledEvent(user.getEmail(), user.getUsername(), Instant.now()))`.

- [x] **Task 6.5: Verification & Quality Assurance (Service-Layer Unit Testing Specification)**
  - **Status**: Completed
  - **Target Files**:
    - [EmailServiceImplTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/service/EmailServiceImplTest.java)
    - [AuthServiceImplTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/service/AuthServiceImplTest.java)
  - **Core Testing Concepts**:
    - Service-layer unit tests validate business logic in isolation without spinning up an embedded database or invoking live external HTTP endpoints (Resend).
    - Leverage **Mockito** (`@ExtendWith(MockitoExtension.class)`) to mock collaborating dependencies (repositories, event publishers, HTTP clients).
  - **Fundamental Mockito Unit Test Pattern (Reference Skeleton)**:
    ```java
    @ExtendWith(MockitoExtension.class)
    class SampleServiceTest {

        @Mock
        private ApplicationEventPublisher eventPublisher;

        @InjectMocks
        private AuthServiceImpl authService;

        @Test
        @DisplayName("Baseline pattern: Stub mock behavior and verify collaborative interactions")
        void exampleUnitTest() {
            // 1. Arrange: Define mock behavior when required (when-thenReturn)

            // 2. Act: Invoke target service method under test

            // 3. Assert: Verify collaborator method invocation with expected arguments
            // verify(eventPublisher, times(1)).publishEvent(any());
        }
    }
    ```
  - **Test Suite Specifications**:
    1. **`AuthServiceImplTest` Specifications**:
       - **Positive Scenario (`enableTotp_Success`)**:
         - Stub `totpService.verifyCode(...)` returning `true`.
         - Invoke `authService.enableTotp(username, code)`.
         - Key Assertions: Confirm `user.getIsTotpEnabled()` transitions to `true`, `userRepository.save(user)` executes once, and `eventPublisher.publishEvent(any(TotpEnabledEvent.class))` is dispatched exactly once.
       - **Negative Scenario (`enableTotp_InvalidCode`)**:
         - Stub `totpService.verifyCode(...)` returning `false`.
         - Key Assertions: Assert that `BadRequestException` is thrown.
         - Engineering Clue: Assert `verify(eventPublisher, never()).publishEvent(any());` to guarantee zero events are published upon validation failure.
    2. **`EmailServiceImplTest` Specifications**:
       - **Template Processing & Dispatch (`sendTotpEnabledAlert`)**:
         - Verify Thymeleaf `TemplateEngine.process(...)` is invoked with template name `"totp-enabled"` and expected context variables (`to`, `username`).
         - Validate error containment / resilience: if remote HTTP dispatch throws an exception, ensure the error is handled gracefully and does not propagate to caller threads.
       - **Engineering Clue**: When testing `RestClient`, mock chained invocation responses or configure `MockRestServiceServer` to validate outbound HTTP request payload structure without live network connectivity.
  - **Test Execution Commands**:
    - `rtk ./gradlew test --tests com.java.springtotp.service.AuthServiceImplTest`
    - `rtk ./gradlew test --tests com.java.springtotp.service.EmailServiceImplTest`

---

### Phase 7: Risk-Based New Device & Suspicious Location Security Alerts

> [!TIP]
> **Core Engineering Concepts for Beginners:**
> 1. **Alert Fatigue Prevention vs. Real Threat Defense**:
>    - Sending a notification email on *every single login* (especially after the user already completed 2FA) degrades user security perception. Users become desensitized ("alert fatigued") and develop a habit of deleting or ignoring security emails without reading them.
>    - Industry leaders (Google, GitHub, Netflix) implement **Risk-Based Authentication Alerts**: Emails are dispatched exclusively when a login originates from an **Unrecognized / New Device or Browser**, an **Anomalous Geographic Location / IP**, or a **New Session After Extended Inactivity**.
> 2. **Device Fingerprinting Mechanics**:
>    - Web browsers transmit the standard `User-Agent` HTTP header (e.g., `"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ... Chrome/122.0.0.0"`).
>    - By parsing this header into normalized OS and browser strings and generating a deterministic cryptographic hash (e.g., SHA-256 of `userId + deviceDetails`), the system can reliably determine whether the client environment is recognized or new.
> 3. **Reverse Proxy & Client IP Extraction**:
>    - In production environments behind edge infrastructure (Nginx, Cloudflare, AWS ALB), invoking `request.getRemoteAddr()` returns the internal proxy loopback address (e.g., `127.0.0.1` or `10.0.0.1`).
>    - To extract the real client IP, the application inspects the `X-Forwarded-For` header (taking the first comma-separated client IP) or `X-Real-IP`, falling back to `request.getRemoteAddr()`.
> 4. **Zero-Latency Event-Driven Architecture (`@Async`)**:
>    - Client metadata extraction, GeoIP resolution, database persistence, and external Resend HTTP API dispatch execute asynchronously after database transaction commitment (`TransactionPhase.AFTER_COMMIT`), ensuring zero millisecond latency overhead on the primary login response.

---

- [x] **Task 7.1: Create Known Device Model & Repository (`UserDevice`)**
  - **Status**: Completed
  - **Target Files**:
    - [UserDevice.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/model/UserDevice.java) *(Created & Refined with unique constraint `uk_user_device_user_hash`)*
    - [UserDeviceRepository.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/repository/UserDeviceRepository.java) *(Created with query methods)*
    - [V4__create_user_device_table.sql](file:///home/udin/projects/spring-totp/src/main/resources/db/migration/V4__create_user_device_table.sql) *(Created)*
  - **Requirements & Schema Specifications**:
    1. Entity `UserDevice` extends `BaseModel`:
       - [x] Entity implemented with fields `user`, `deviceDetails`, `deviceHash`, `ipAddress`, `location`, `fingerPrint`, `lastLoginAt`.
       - [x] Unique constraint configured on `(user_id, device_hash)` (`uk_user_device_user_hash`).
       - [x] **Field Semantic Refinements & Storage (Gaps & Current Status)**:
         - [x] `deviceHash`: Primary deterministic lookup key. Decoupled from heuristic fingerprints via persistent cookie token `__Host-device_id` (extracted via `DeviceDetectionServiceImpl.extractDeviceToken` and stored in `DeviceMetadata.deviceToken()`), with heuristic fingerprint as fallback.
         - [x] `fingerPrint`: Secondary client heuristic fingerprint (composite browser, OS, screen/headers) for anomaly detection and audit trail without duplicating `deviceHash`. Column exists in `UserDevice`, set on new device instantiation in `DeviceVerificationServiceImpl` (`.fingerPrint(metadata.fingerprint())`) and refreshed on recognized device logins (`device.setFingerPrint(metadata.fingerprint())`).
         - [x] `ipAddress`: Store masked IP (e.g. `192.168.1.xxx` / IPv6 `2001:db8:85a3::xxxx`) via `IpUtils.maskIp(...)` for GDPR/privacy compliance (Guaranteed via auto-masking in `DeviceMetadata` compact constructor using `IpUtils.maskIp`).
    2. Interface `UserDeviceRepository` extends `JpaRepository<UserDevice, Long>`:
       - [x] Interface declared.
       - [x] Query method: `Optional<UserDevice> findByUserIdAndDeviceHash(Long userId, String deviceHash)`.
       - [x] Query method: `List<UserDevice> findAllByUserId(Long userId)`.
    3. Database Schema Migration:
       - [x] Flyway script `V4__create_user_device_table.sql` for PostgreSQL table creation and index definitions.

---

- [x] **Task 7.2: Device Metadata & GeoIP Extraction Utility (`DeviceDetectionService`)**
  - **Status**: Completed
  - **Target Files**:
    - [DeviceMetadata.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/dto/userDevice/DeviceMetadata.java) *(Created)*
    - [Fingerprints.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/util/Fingerprints.java) *(Created)*
    - [IpUtils.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/util/IpUtils.java) *(Created with IPv4/IPv6 masking support)*
    - [GeoIpResolver.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/util/GeoIpResolver.java) *(Created)*
    - [NoOpGeoIpResolver.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/util/NoOpGeoIpResolver.java) *(Created)*
    - [DeviceDetectionService.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/DeviceDetectionService.java) *(Created)*
    - [DeviceDetectionServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/DeviceDetectionServiceImpl.java) *(Completed)*
  - **Requirements & Method Specifications**:
    1. Utilities (`IpUtils`, `Fingerprints`, `GeoIpResolver`, `NoOpGeoIpResolver`):
       - [x] Implement `IpUtils.maskIp(String rawIp)`:
         - IPv4 masking: `192.168.1.123` -> `192.168.1.xxx`.
         - IPv6 masking: `2001:db8:85a3::8a2e:370:7334` -> `parts[0]:parts[1]:parts[2]::xxxx`.
         - Null/blank/malformed IP safe fallback to `"unknown"`.
       - [x] SHA-256 hashing utility `Fingerprints.sha256(...)`.
       - [x] Default `NoOpGeoIpResolver` fallback component.
    2. DTO / Record `DeviceMetadata`:
       - [x] Record created with fields `ipAddress`, `browser`, `operatingSystem`, `location`, `deviceDetails`, `fingerprint`, `deviceToken`.
       - [x] Compact constructor implemented: sets `deviceDetails = browser + " on " + operatingSystem` and `fingerprint = Fingerprints.sha256(deviceDetails)`.
       - [x] Ensure `ipAddress` stored in `DeviceMetadata` or exposed to notifications is masked via `IpUtils.maskIp(rawIp)` (Handled automatically in compact constructor).
       - [x] Decouple `deviceHash` from `fingerprint`: Persistent cookie token (`__Host-device_id`) extraction implemented in `DeviceDetectionServiceImpl` (`extractDeviceToken`) and propagated into `DeviceMetadata.deviceToken()` so `deviceToken` acts as primary unique client token and `fingerprint` acts as heuristic fallback.
    3. Service Interface & Implementation (`DeviceDetectionService`):
       - [x] Interface declared with `DeviceMetadata extractMetaData(HttpServletRequest request)`.
       - [x] Implementation handles client IP resolution (`X-Forwarded-For`, `X-Real-IP`, loopback/proxy checks), User-Agent parsing, and GeoIP lookup.
    4. Unit Testing:
       - [ ] Create `DeviceDetectionServiceTest` and `IpUtilsTest` verifying IP extraction, IPv4/IPv6 masking, and User-Agent parsing (Tracked under Task 7.6).

---

- [/] **Task 7.3: Implement Device Verification & Risk-Based Evaluation Logic in `AuthService`**
  - **Status**: In Progress (~75%)
  - **Target Files**:
    - [DeviceVerificationService.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/DeviceVerificationService.java) *(Created)*
    - [DeviceVerificationServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/DeviceVerificationServiceImpl.java) *(In Progress - Known device update with `fingerPrint` audit trail complete; `newDevice` persistence in `else` block implemented; `deviceHash` null-fallback and event publishing pending)*
    - [AuthController.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/controller/AuthController.java) *(Updated - Injected `HttpServletRequest` into `/login`)*
    - [AuthService.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/AuthService.java) *(Updated signature: `login(LoginRequest, HttpServletRequest)`)*
    - [AuthServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/AuthServiceImpl.java) *(Updated - Hooked `verifyDeviceAndNotify` into `login`)*
  - **Architectural Design (Device Recognition & Privacy Compliance)**:
    - **Device Recognition Strategy**:
      - `deviceHash`: Primary deterministic identifier. SHA-256 hash of device cookie token (`__Host-device_id` or primary device identifier).
      - `fingerPrint`: Secondary client heuristic fingerprint (composite browser, OS, screen/headers) for anomaly detection and audit trail without duplicating `deviceHash`.
      - `deviceDetails`: Human-readable label (e.g. `"Chrome on Windows"`) for security cards and email templates.
    - **IP Masking & Privacy (GDPR / UU PDP Compliance)**:
      - Persist `ipAddress` in `UserDevice` using masked format via `IpUtils.maskIp(metadata.ipAddress())`.
      - Raw client IP is kept strictly in-memory during GeoIP lookup.
  - **Execution Flow & Risk Evaluation**:
    1. Define dedicated service `DeviceVerificationService` declaring:
       `void verifyDeviceAndNotify(User user, HttpServletRequest request)`
    2. Extract request metadata via `deviceDetectionService.extractMetaData(request)`.
    3. Query database via `userDeviceRepository.findByUserIdAndDeviceHash(user.getId(), targetDeviceHash)`:
       - **Condition A: Recognized Device (*Known Device*)**:
         - [x] Update fields `lastLoginAt = Instant.now()`, `location = metadata.location()`, and `ipAddress = metadata.ipAddress()` *(Masked format guaranteed by `DeviceMetadata` compact constructor)*.
         - [x] Maintain `fingerPrint` audit trail: invoke `device.setFingerPrint(metadata.fingerprint())` before saving.
         - [x] Persist updates via `userDeviceRepository.save(device)`.
         - [x] **DO NOT** publish security alert event (Prevents alert fatigue).
       - **Condition B: Unrecognized Device / Location (*New Device Alert*)**:
         - [/] Complete `else` branch in `DeviceVerificationServiceImpl.java`:
           - [x] Instantiate `UserDevice` with `user`, `deviceDetails`, `deviceHash = deviceToken`, `ipAddress = metadata.ipAddress()`, `location = metadata.location()`, `fingerPrint = metadata.fingerprint()`.
           - [ ] Safe fallback for `deviceHash` when `deviceToken` is null/blank (fallback to `metadata.fingerprint()`) to ensure database non-null constraint (`device_hash VARCHAR(64) NOT NULL`).
           - [x] Persist new record via `userDeviceRepository.save(newDevice)`.
         - **Event Publishing**:
           - [ ] Inject `ApplicationEventPublisher` in `DeviceVerificationServiceImpl`.
           - [ ] Publish `NewDeviceLoginEvent(user.getUsername(), user.getEmail(), metadata)` (with masked IP).
         - [x] **Listener Action**: `NewDeviceSecurityListener.onNewDeviceLogin(NewDeviceLoginEvent event)`.
         - [ ] **Email Dispatch Action**: `emailService.sendNEwDeviceLoginAlert(...)` (displays masked IP in alert card; currently does not send email).
    4. Hook `deviceVerificationService.verifyDeviceAndNotify` into authentication flows:
       - [x] `AuthServiceImpl.login`: Hooked for non-TOTP login flow (`isTotpEnabled == false`).
       - [ ] `AuthServiceImpl.verifyTotpLogin`: Hook into TOTP verification upon valid 6-digit OTP code verification (pass `HttpServletRequest` via controller/service parameter or `RequestContextHolder`).
    5. Test Suite Fixes:
       - [ ] Fix `AuthServiceImplTest`: Update unit test calls from `authService.login(request)` to `authService.login(request, httpServletRequest)` (lines 233, 265).

---

- [x] **Task 7.4: Implement Domain Event & Asynchronous Security Notification Listener**
  - **Status**: Completed
  - **Target Files**:
    - [NewDeviceLoginEvent.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/event/NewDeviceLoginEvent.java) *(Created)*
    - [NewDeviceSecurityListener.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/event/NewDeviceSecurityListener.java) *(Created)*
  - **Requirements & Specifications**:
    1. Record `NewDeviceLoginEvent`:
       - [x] Record created with fields `username`, `email`, `deviceMetadata`.
       - [ ] Ensure `deviceMetadata.ipAddress()` carries masked IP format for safe template rendering.
    2. Component `NewDeviceSecurityListener`:
       - [x] Method: `onNewDeviceLogin(NewDeviceLoginEvent event)`.
       - [x] Annotate with `@Async` for non-blocking background thread pool execution.
       - [x] Annotate with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
       - [x] Contain all exceptions in a `try-catch` block: log failures cleanly without bubbling exceptions.

---

- [/] **Task 7.5: Security Notification Template & `EmailService` Integration**
  - **Status**: In Progress (~70%)
  - **Target Files**:
    - [new-device-login.html](file:///home/udin/projects/spring-totp/src/main/resources/templates/new-device-login.html) *(Completed)*
    - [EmailService.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/EmailService.java) *(Completed)*
    - [EmailServiceImpl.java](file:///home/udin/projects/spring-totp/src/main/java/com/java/springtotp/service/impl/EmailServiceImpl.java) *(In Progress - Template rendering completed, needs Resend dispatch call)*
  - **Requirements & Specifications**:
    1. Extend interface `EmailService`:
       - [x] Declare: `void sendNEwDeviceLoginAlert(String username, String email, DeviceMetadata deviceMetadata)`.
    2. Implementation in `EmailServiceImpl`:
       - [x] Process Thymeleaf template `new-device-login.html` with context attributes:
         - `username`: User display name.
         - `deviceDetails`: Parsed browser and OS.
         - `ipAddress`: Client IP address (**Masked format**, e.g., `192.168.1.xxx` for privacy compliance).
         - `location`: Resolved geographic location.
         - `loginTime`: Human-friendly formatted timestamp.
         - `securityActionUrl`: Emergency action link.
         - `supportEmail`: Support contact email.
       - [ ] Build `ResendEmailRequest` and dispatch via `sendEmail(request)` inside `sendNEwDeviceLoginAlert`.
    3. Template Design `new-device-login.html`:
       - [x] Professional, clean, responsive security card layout.
       - [x] Header: **"Security alert: New sign-in"**.
       - [x] Details grid: Time, Device & Browser, IP Address (Masked), Approximate Location (annotated with *"Estimated based on IP address"*).
       - [x] Prominent Emergency Action Button (CTA): *"Secure Your Account"*.
       - [x] Reassurance note: *"If this was you, no action is needed."*
    4. Test Suite Fixes:
       - [ ] Fix `EmailServiceImplTest`: Update constructor instantiation to supply required `UserRepository` mock bean (or clean up unused `UserRepository` from `EmailServiceImpl`) (line 55).

---

- [ ] **Task 7.6: Verification & Quality Assurance (Testing Specification)**
  - **Status**: Pending
  - **Target Files**:
    - [DeviceDetectionServiceTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/service/DeviceDetectionServiceTest.java)
    - [DeviceVerificationServiceTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/service/DeviceVerificationServiceTest.java)
    - [AuthControllerNewDeviceTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerNewDeviceTest.java)
  - **Current Test Suite Blockers**:
    - `AuthServiceImplTest.java` compilation error (lines 233, 265): `authService.login(request)` does not match updated signature `login(LoginRequest, HttpServletRequest)`.
    - `EmailServiceImplTest.java` compilation error (line 55): `new EmailServiceImpl(...)` missing required `UserRepository` constructor parameter.
  - **Test Suite Scenarios**:
    1. **Unit Test: User-Agent Parsing, IP Extraction & Masking (`DeviceDetectionServiceTest`, `IpUtilsTest`)**:
       - Simulate `X-Forwarded-For: 203.0.113.195, 10.0.0.1` -> Verify extracted raw IP is `203.0.113.195` and masked IP is `203.0.113.xxx`.
       - Test IPv6 masking: `2001:db8:85a3::8a2e:370:7334` -> `2001:db8:85a3::xxxx`.
       - Simulate `User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ... Chrome/120.0` -> Verify parsed device label is `"Chrome on Windows 10"`.
    2. **Unit Test: Risk-Based Evaluation Logic (`DeviceVerificationServiceTest`)**:
       - **New Device Scenario**: `userDeviceRepository.findByUserIdAndDeviceHash(...)` returns `Optional.empty()` -> Verify new device entity is saved (with masked IP) and `eventPublisher.publishEvent(any(NewDeviceLoginEvent.class))` executes exactly once.
       - **Known Device Scenario (Alert Fatigue Prevention)**: `userDeviceRepository.findByUserIdAndDeviceHash(...)` returns existing device -> Verify `lastLoginAt` and masked IP are updated, and `eventPublisher.publishEvent(...)` is **NEVER** invoked (`never()`).
    3. **Integration Test via MockMvc (`AuthControllerNewDeviceTest`)**:
       - Execute successful login from a new user-agent/device -> Verify HTTP `200 OK` and verify asynchronous email dispatch via `Mockito.verify(emailService, timeout(2000).times(1)).sendNEwDeviceLoginAlert(any())` carries masked IP in metadata.
  - **Test Execution Commands**:
    - `rtk ./gradlew test --tests com.java.springtotp.service.DeviceDetectionServiceTest`
    - `rtk ./gradlew test --tests com.java.springtotp.service.DeviceVerificationServiceTest`

