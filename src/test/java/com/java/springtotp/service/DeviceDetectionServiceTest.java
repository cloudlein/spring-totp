package com.java.springtotp.service;

import com.java.springtotp.dto.userDevice.DeviceMetadata;
import com.java.springtotp.service.impl.DeviceDetectionServiceImpl;
import com.java.springtotp.util.GeoIpResolver;
import com.java.springtotp.util.IpUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceDetectionServiceTest {

    @Mock
    private GeoIpResolver geoIpResolver;

    private DeviceDetectionService deviceDetectionService;

    @BeforeEach
    void setUp() {
        deviceDetectionService = new DeviceDetectionServiceImpl(geoIpResolver);
    }

    @Test
    @DisplayName("extractMetaData parses User-Agent, X-Forwarded-For, and cookie token")
    void extractMetaData_WithFullHeaders_ShouldExtractCorrectly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // trusted proxy
        request.addHeader("X-Forwarded-For", "203.0.113.195, 10.0.0.1");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/122.0.0.0 Safari/537.36");
        request.setCookies(new Cookie("__Host-device_id", "test-cookie-token"));

        when(geoIpResolver.resolve("203.0.113.195")).thenReturn("Jakarta, Indonesia");

        DeviceMetadata metadata = deviceDetectionService.extractMetaData(request);

        assertThat(metadata.ipAddress()).isEqualTo("203.0.113.xxx");
        assertThat(metadata.browser()).startsWith("Chrome");
        assertThat(metadata.operatingSystem()).isEqualTo("Windows");
        assertThat(metadata.location()).isEqualTo("Jakarta, Indonesia");
        assertThat(metadata.deviceToken()).isEqualTo("test-cookie-token");
    }

    @Test
    @DisplayName("IpUtils masks IPv4 and IPv6 addresses correctly")
    void ipUtils_Masking_ShouldMaskProperly() {
        assertThat(IpUtils.maskIp("192.168.1.123")).isEqualTo("192.168.1.xxx");
        assertThat(IpUtils.maskIp("2001:db8:85a3:0:0:8a2e:370:7334")).isEqualTo("2001:db8:85a3::xxxx");
        assertThat(IpUtils.maskIp("invalid-ip")).isEqualTo("unknown");
        assertThat(IpUtils.maskIp(null)).isEqualTo("unknown");
    }
}
