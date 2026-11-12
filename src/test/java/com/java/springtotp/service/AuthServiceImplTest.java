package com.java.springtotp.service;

import com.java.springtotp.dto.auth.AuthResponse;
import com.java.springtotp.dto.auth.LoginRequest;
import com.java.springtotp.dto.auth.RefreshTokenRequest;
import com.java.springtotp.dto.auth.RegisterRequest;
import com.java.springtotp.dto.auth.TotpSetupResponse;
import com.java.springtotp.dto.auth.TotpVerifyRequest;
import com.java.springtotp.event.TotpEnabledEvent;
import com.java.springtotp.exception.BadRequestException;
import com.java.springtotp.exception.InvalidTokenException;
import com.java.springtotp.exception.ResourceAlreadyExistsException;
import com.java.springtotp.exception.ResourceNotFoundException;
import com.java.springtotp.mapper.UserMapper;
import com.java.springtotp.model.Role;
import com.java.springtotp.model.User;
import com.java.springtotp.repository.RoleRepository;
import com.java.springtotp.repository.UserRepository;
import com.java.springtotp.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.java.springtotp.service.DeviceVerificationService;
import org.springframework.mock.web.MockHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private TotpService totpService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private DeviceVerificationService deviceVerificationService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "applicationName", "SpringTotp");
    }

    @Test
    @DisplayName("enableTotp enables TOTP, saves user, and dispatches TotpEnabledEvent when code is valid")
    void enableTotp_WithValidCode_ShouldEnableTotpAndPublishEvent() {
        String username = "testuser";
        String code = "123456";
        String secret = "JBSWY3DPEHPK3PXP";
        String email = "test@example.com";

        User user = User.builder()
                .username(username)
                .email(email)
                .totpSecret(secret)
                .isTotpEnabled(false)
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(totpService.verifyCode(secret, code)).thenReturn(true);

        authService.enableTotp(username, code);

        assertThat(user.getIsTotpEnabled()).isTrue();
        verify(userRepository).save(user);

        ArgumentCaptor<TotpEnabledEvent> eventCaptor = ArgumentCaptor.forClass(TotpEnabledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TotpEnabledEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.email()).isEqualTo(email);
        assertThat(capturedEvent.username()).isEqualTo(username);
        assertThat(capturedEvent.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("enableTotp throws BadRequestException and never publishes event when code is invalid")
    void enableTotp_WithInvalidCode_ShouldThrowBadRequestException() {
        String username = "testuser";
        String code = "000000";
        String secret = "JBSWY3DPEHPK3PXP";

        User user = User.builder()
                .username(username)
                .totpSecret(secret)
                .isTotpEnabled(false)
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(totpService.verifyCode(secret, code)).thenReturn(false);

        assertThatThrownBy(() -> authService.enableTotp(username, code))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("invalid TOTP verification code");

        assertThat(user.getIsTotpEnabled()).isFalse();
        verify(userRepository, never()).save(user);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("enableTotp throws ResourceNotFoundException when user is not found")
    void enableTotp_WithUnknownUser_ShouldThrowResourceNotFoundException() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.enableTotp("unknown", "123456"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("user not found");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("setupTotp generates new secret if absent and returns setup details")
    void setupTotp_WhenSecretAbsent_ShouldGenerateAndSaveSecret() {
        String username = "testuser";
        User user = User.builder()
                .username(username)
                .totpSecret(null)
                .isTotpEnabled(false)
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(totpService.generateSecretKey()).thenReturn("GENERATED_SECRET");
        when(totpService.buildOtpAuthUri("GENERATED_SECRET", username, "SpringTotp"))
                .thenReturn("otpauth://totp/SpringTotp:testuser?secret=GENERATED_SECRET");
        when(totpService.generateQrCodeDataUri(anyString())).thenReturn("data:image/png;base64,qrdata");

        TotpSetupResponse response = authService.setupTotp(username);

        assertThat(response).isNotNull();
        assertThat(response.getSecretKey()).isEqualTo("GENERATED_SECRET");
        assertThat(response.getQrCodeDataUrl()).isEqualTo("data:image/png;base64,qrdata");
        assertThat(user.getTotpSecret()).isEqualTo("GENERATED_SECRET");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("setupTotp reuses existing secret without generating a new one")
    void setupTotp_WhenSecretAlreadyPresent_ShouldReuseExistingSecret() {
        String username = "testuser";
        User user = User.builder()
                .username(username)
                .totpSecret("EXISTING_SECRET")
                .isTotpEnabled(false)
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(totpService.buildOtpAuthUri("EXISTING_SECRET", username, "SpringTotp"))
                .thenReturn("otpauth://totp/SpringTotp:testuser?secret=EXISTING_SECRET");
        when(totpService.generateQrCodeDataUri(anyString())).thenReturn("data:image/png;base64,qrdata");

        TotpSetupResponse response = authService.setupTotp(username);

        assertThat(response.getSecretKey()).isEqualTo("EXISTING_SECRET");
        verify(totpService, never()).generateSecretKey();
        verify(userRepository, never()).save(user);
    }

    @Test
    @DisplayName("login returns standard access and refresh tokens when TOTP is not enabled")
    void login_WhenTotpDisabled_ShouldReturnFullTokens() {
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("password")
                .build();

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("testuser");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        User user = User.builder()
                .username("testuser")
                .isTotpEnabled(false)
                .build();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(userDetails)).thenReturn("access-token");
        when(jwtService.generateTokenRefresh(userDetails)).thenReturn("refresh-token");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        AuthResponse response = authService.login(request, httpRequest);

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getMfaRequired()).isNull();
        assertThat(response.getTempToken()).isNull();
        verify(deviceVerificationService).verifyDeviceAndNotify(user, httpRequest);
    }

    @Test
    @DisplayName("login returns MFA requirement and ephemeral token when TOTP is enabled")
    void login_WhenTotpEnabled_ShouldReturnMfaRequiredWithTempToken() {
        LoginRequest request = LoginRequest.builder()
                .username("mfauser")
                .password("password")
                .build();

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("mfauser");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        User user = User.builder()
                .username("mfauser")
                .isTotpEnabled(true)
                .build();
        when(userRepository.findByUsername("mfauser")).thenReturn(Optional.of(user));
        when(jwtService.generateMfaToken(userDetails)).thenReturn("temp-token");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        AuthResponse response = authService.login(request, httpRequest);

        assertThat(response.getMfaRequired()).isTrue();
        assertThat(response.getTempToken()).isEqualTo("temp-token");
        assertThat(response.getToken()).isNull();
        assertThat(response.getRefreshToken()).isNull();
        verify(deviceVerificationService, never()).verifyDeviceAndNotify(any(), any());
    }

    @Test
    @DisplayName("verifyTotpLogin returns full tokens when MFA token and TOTP code are valid")
    void verifyTotpLogin_WithValidCredentials_ShouldReturnAccessAndRefreshTokens() {
        TotpVerifyRequest request = TotpVerifyRequest.builder()
                .tempToken("valid-temp-token")
                .code("123456")
                .build();

        UserDetails userDetails = mock(UserDetails.class);
        User user = User.builder()
                .username("mfauser")
                .totpSecret("SECRET")
                .isTotpEnabled(true)
                .build();

        when(jwtService.extractUsername("valid-temp-token")).thenReturn("mfauser");
        when(userDetailsService.loadUserByUsername("mfauser")).thenReturn(userDetails);
        when(userRepository.findByUsername("mfauser")).thenReturn(Optional.of(user));
        when(jwtService.extractTokenType("valid-temp-token")).thenReturn("MFA_TEMP");
        when(jwtService.isValidToken("valid-temp-token", userDetails)).thenReturn(true);
        when(totpService.verifyCode("SECRET", "123456")).thenReturn(true);
        when(jwtService.generateToken(userDetails)).thenReturn("new-access-token");
        when(jwtService.generateTokenRefresh(userDetails)).thenReturn("new-refresh-token");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        AuthResponse response = authService.verifyTotpLogin(request, httpRequest);

        assertThat(response.getToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        verify(deviceVerificationService).verifyDeviceAndNotify(user, httpRequest);
    }

    @Test
    @DisplayName("verifyTotpLogin throws BadRequestException when token type is not MFA_TEMP")
    void verifyTotpLogin_WithNonMfaToken_ShouldThrowBadRequestException() {
        TotpVerifyRequest request = TotpVerifyRequest.builder()
                .tempToken("standard-token")
                .code("123456")
                .build();

        UserDetails userDetails = mock(UserDetails.class);
        User user = User.builder().username("mfauser").build();

        when(jwtService.extractUsername("standard-token")).thenReturn("mfauser");
        when(userDetailsService.loadUserByUsername("mfauser")).thenReturn(userDetails);
        when(userRepository.findByUsername("mfauser")).thenReturn(Optional.of(user));
        when(jwtService.extractTokenType("standard-token")).thenReturn("ACCESS");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        assertThatThrownBy(() -> authService.verifyTotpLogin(request, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("invalid TOTP verification code");
    }

    @Test
    @DisplayName("verifyTotpLogin throws BadRequestException when OTP code is incorrect")
    void verifyTotpLogin_WithWrongOtpCode_ShouldThrowBadRequestException() {
        TotpVerifyRequest request = TotpVerifyRequest.builder()
                .tempToken("valid-temp-token")
                .code("999999")
                .build();

        UserDetails userDetails = mock(UserDetails.class);
        User user = User.builder().username("mfauser").totpSecret("SECRET").build();

        when(jwtService.extractUsername("valid-temp-token")).thenReturn("mfauser");
        when(userDetailsService.loadUserByUsername("mfauser")).thenReturn(userDetails);
        when(userRepository.findByUsername("mfauser")).thenReturn(Optional.of(user));
        when(jwtService.extractTokenType("valid-temp-token")).thenReturn("MFA_TEMP");
        when(jwtService.isValidToken("valid-temp-token", userDetails)).thenReturn(true);
        when(totpService.verifyCode("SECRET", "999999")).thenReturn(false);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        assertThatThrownBy(() -> authService.verifyTotpLogin(request, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("invalid TOTP verification code");
    }

    @Test
    @DisplayName("logout throws BadRequestException when TOTP is not enabled")
    void logout_WhenTotpDisabled_ShouldThrowBadRequestException() {
        String token = "jwt-token";
        User user = User.builder()
                .username("testuser")
                .isTotpEnabled(false)
                .build();

        when(jwtService.extractUsername(token)).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.logout(token))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("TOTP Two-Factor Authentication Required");

        verify(tokenBlacklistService, never()).addToBlacklist(anyString(), anyLong());
    }

    @Test
    @DisplayName("logout blacklists token when TOTP is enabled and token has remaining lifetime")
    void logout_WhenTotpEnabled_ShouldAddToBlacklist() {
        String token = "jwt-token";
        User user = User.builder()
                .username("testuser")
                .isTotpEnabled(true)
                .build();

        long futureEpoch = System.currentTimeMillis() + 60000;
        when(jwtService.extractUsername(token)).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(jwtService.extractExpiration(token)).thenReturn(new Date(futureEpoch));

        authService.logout(token);

        verify(tokenBlacklistService).addToBlacklist(eq(token), anyLong());
    }

    @Test
    @DisplayName("register persists user with encoded password and default role")
    void register_WithValidDetails_ShouldSaveUser() {
        RegisterRequest request = RegisterRequest.builder()
                .name("New User")
                .username("newuser")
                .email("new@example.com")
                .password("plainPassword")
                .confirmPassword("plainPassword")
                .build();

        User user = User.builder()
                .name("New User")
                .username("newuser")
                .email("new@example.com")
                .password("plainPassword")
                .build();

        Role role = Role.builder().role("USER").build();

        when(userRepository.existsByName("New User")).thenReturn(false);
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(user);
        when(passwordEncoder.encode("plainPassword")).thenReturn("hashedPassword");
        when(roleRepository.findByRole("USER")).thenReturn(Optional.of(role));

        AuthResponse response = authService.register(request);

        assertThat(response.getMessage()).isEqualTo("register successful");
        assertThat(user.getIsTotpEnabled()).isFalse();
        assertThat(user.getPassword()).isEqualTo("hashedPassword");
        assertThat(user.getRole()).isEqualTo(role);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("register throws ResourceAlreadyExistsException when username is taken")
    void register_WhenUsernameExists_ShouldThrowResourceAlreadyExistsException() {
        RegisterRequest request = RegisterRequest.builder()
                .name("User")
                .username("existinguser")
                .email("user@example.com")
                .password("password")
                .build();

        when(userRepository.existsByName("User")).thenReturn(false);
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessage("username already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("refreshToken returns new tokens when valid")
    void refreshToken_WithValidToken_ShouldReturnNewTokens() {
        String token = "valid-refresh-token";
        UserDetails userDetails = mock(UserDetails.class);

        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtService.extractUsername(token)).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);
        when(jwtService.isValidToken(token, userDetails)).thenReturn(true);
        when(jwtService.generateToken(userDetails)).thenReturn("new-access");
        when(jwtService.generateTokenRefresh(userDetails)).thenReturn("new-refresh");

        AuthResponse response = authService.refreshToken(token);

        assertThat(response.getToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    @DisplayName("refreshToken throws InvalidTokenException when token is blacklisted")
    void refreshToken_WhenBlacklisted_ShouldThrowInvalidTokenException() {
        String token = "blacklisted-token";
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(true);

        assertThatThrownBy(() -> authService.refreshToken(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("invalid token");
    }
}
