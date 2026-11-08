package com.java.springtotp.util;


public final class IpUtils {

    private static final String UNKNOWN = "unknown";

    private IpUtils() {}

    public static String maskIp(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return UNKNOWN;
        }

        if (rawIp.contains(":")) {
            return maskIpv6(rawIp);
        }

        if (rawIp.contains(".")) {
            int dotCount = rawIp.split("\\.", -1).length - 1;
            if (dotCount != 3) {
                return UNKNOWN;
            }
            int lastDot = rawIp.lastIndexOf('.');
            return rawIp.substring(0, lastDot + 1) + "xxx";
        }

        return UNKNOWN;
    }

    private static String maskIpv6(String rawIp) {
        String[] parts = rawIp.split(":");
        if (parts.length < 3) {
            return UNKNOWN;
        }
        return parts[0] + ":" + parts[1] + ":" + parts[2] + "::xxxx";
    }
}