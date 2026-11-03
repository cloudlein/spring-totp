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
  - [x] Task 6.3: Domain Event & `@Async` Event Listener.
  - [x] Task 6.4: Event Publishing integration in `AuthServiceImpl`.
  - [ ] Task 6.5: Unit & integration testing.

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

- [ ] **Test Case 1: Initial Registration & Login**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    1. Dispatch `POST /api/v1/auth/register` with unique user credentials.
    2. Dispatch `POST /api/v1/auth/login` using the newly registered credentials.
  - **Key Assertions**: HTTP `200 OK`, `data.token` is present and non-empty, and `data.mfaRequired` evaluates to `false` (or absent/null).
  - **Engineering Clue**: Persist the issued JWT access token from the login response to authenticate subsequent protected endpoints as a Bearer token.

- [ ] **Test Case 2: Attempt Immediate Logout (Expected Failure)**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    - Dispatch `POST /api/v1/auth/logout` supplying header `Authorization: Bearer <token_from_case_1>`.
  - **Key Assertions**:
    - Status code must be HTTP `400 Bad Request` (`status().isBadRequest()`).
    - Error response body must explicitly indicate that TOTP Two-Factor Authentication activation is mandatory prior to session termination.
  - **Engineering Clue**: Append `.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)` to the MockMvc request builder.

- [ ] **Test Case 3: Provision TOTP Secret & QR Code**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    - Dispatch `GET /api/v1/auth/totp/setup` with an active Bearer token.
  - **Key Assertions**:
    - Status code HTTP `200 OK`.
    - Response payload contains `secretKey` (non-blank Base32 format) and `qrCodeDataUrl` starting with standard prefix `data:image/png;base64,`.
  - **Engineering Clue**: Extract the generated `secretKey` from the JSON response to compute valid OTP verification codes in Test Case 4.

- [ ] **Test Case 4: Enable TOTP with Invalid vs Valid Code**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    - **Negative Path**: Dispatch `POST /api/v1/auth/totp/enable` with body `{ "code": "000000" }` -> Assert HTTP `400 Bad Request`.
    - **Positive Path**: Dispatch `POST /api/v1/auth/totp/enable` with a valid 6-digit OTP code calculated against the user's `secretKey`.
  - **Key Assertions**: Valid code request returns HTTP `200 OK`.
  - **Engineering Clue**: Autowire or instantiate `TotpService` within your test class to derive the synchronized one-time passcode for `Instant.now()`.

- [ ] **Test Case 5: Retry Logout (Expected Success)**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    - Following successful TOTP activation in Case 4, reissue `POST /api/v1/auth/logout` using the active Bearer token.
  - **Key Assertions**:
    - HTTP `200 OK`.
    - The bearer token is invalidated and successfully blacklisted in Redis.

- [ ] **Test Case 6: Login Flow with MFA Enforced**
  - **Target File**: [AuthControllerTest.java](file:///home/udin/projects/spring-totp/src/test/java/com/java/springtotp/controller/AuthControllerTest.java)
  - **Execution Flow**:
    - **Step 1**: Dispatch `POST /api/v1/auth/login` for the MFA-enabled account.
      - Assertions: HTTP `200 OK`, `data.mfaRequired == true`, `data.tempToken` is present and populated, whereas standard `data.token` is null or suppressed.
    - **Step 2**: Dispatch `POST /api/v1/auth/totp/verify` submitting `{ "tempToken": "...", "code": "<valid_otp_code>" }`.
      - Assertions: HTTP `200 OK`, returning fully authenticated `token` (access token) and `refreshToken`.
  - **Engineering Clue**: Endpoint `/api/v1/auth/totp/verify` is publicly accessible (`permitAll()` in `SecurityConfig`), requiring no Bearer authentication header.

- [ ] **Test Case 7: Asynchronous Security Email Dispatch on TOTP Activation**
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

- [ ] **Task 6.5: Verification & Quality Assurance (Service-Layer Unit Testing Specification)**
  - **Status**: Not Started
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

