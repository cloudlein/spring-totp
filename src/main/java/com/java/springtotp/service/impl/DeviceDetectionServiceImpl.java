package com.java.springtotp.service.impl;

import com.java.springtotp.dto.userDevice.DeviceMetadata;
import com.java.springtotp.service.DeviceDetectionService;
import com.java.springtotp.util.GeoIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class DeviceDetectionServiceImpl implements DeviceDetectionService {

    private static final String LOCAL_LABEL = "Localhost / Private Network";

    private final String UNKNOWN = "Unknown";

    private static final Set<String> TRUSTED_PROXIES = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "10.0.0.2");

    private final GeoIpResolver geoIpResolver;

    @Override
    public DeviceMetadata extractMetaData(HttpServletRequest request) {
        String ip = extractIpClient(request);
        String userAgent = request.getHeader("User-Agent");
        String browser = parseBrowser(userAgent);
        String os = parseOs(userAgent);
        String location = resolveLocation(ip);
        String deviceDetails = browser + " on " + os;
        String fingerprint = computeFingerPrint(deviceDetails);

        return new DeviceMetadata(ip, browser, os, location, deviceDetails, fingerprint);
    }


    private String extractIpClient(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (!TRUSTED_PROXIES.contains(remoteAddr)) {
            return remoteAddr;
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (isValid(xff)) {
            return xff.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (isValid(realIp)) {
            return realIp.trim();
        }

        return remoteAddr;
    }

    private boolean isValid(String value) {
        return value != null && !value.isBlank() && !UNKNOWN.equalsIgnoreCase(value);
    }

    private String parseBrowser(String ua) {
        if (ua == null || ua.isBlank()) return UNKNOWN;

        Pattern[] patterns = {
                Pattern.compile("Edg(?:e|A|iOS)?/([\\d.]+)"),
                Pattern.compile("PostmanRuntime/([\\d.]+)"),
                Pattern.compile("Firefox/([\\d.]+)"),
                Pattern.compile("Chrome/([\\d.]+)"),
                Pattern.compile("Version/([\\d.]+).*Safari")
        };

        String[] names = {"Edge", "Postman", "Firefox", "Chrome", "Safari"};

        for (int i = 0; i < patterns.length; i++ ) {
            Matcher m = patterns[i].matcher(ua);
            if (m.find()) {
                return names[i] + " " + m.group(1);
            }
        }

        return UNKNOWN;
    }

    private String parseOs(String ua) {
        if (ua == null || ua.isBlank()) return UNKNOWN;

        if (ua.contains("Android"))                              return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad"))        return "iOS";
        if (ua.contains("Windows"))                              return "Windows";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("Linux"))                                return "Linux";
        return UNKNOWN;
    }

    private String resolveLocation(String ip) {
        if (isPrivateOrLocal(ip)) {
            return LOCAL_LABEL;
        }

        try {
            String result = geoIpResolver.resolve(ip);
            return (result == null || result.isBlank()) ? UNKNOWN : result;
        }catch (Exception e) {
            log.warn("GeoIP lookup failed for {}: {}", ip, e.getMessage());
            return UNKNOWN;
        }
    }

    private boolean isPrivateOrLocal(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isAnyLocalAddress();
        } catch (UnknownHostException e) {
            return true;
        }
    }

    private String computeFingerPrint(String deviceDetails) {
        String normalized = deviceDetails.trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

}
