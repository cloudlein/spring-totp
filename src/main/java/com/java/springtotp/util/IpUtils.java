package com.java.springtotp.util;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public final class IpUtils {

    private static final String UNKNOWN = "unknown";


    private static final Pattern IP_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");

    private IpUtils() {}

    public static String maskIp(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return UNKNOWN;
        }

        String ip = rawIp.trim();


        if (ip.endsWith(".xxx") || ip.endsWith("::xxxx")) {
            return ip;
        }


        int zone = ip.indexOf('%');
        if (zone > 0) {
            ip = ip.substring(0, zone);
        }

        if (!IP_CHARS.matcher(ip).matches()) {
            return UNKNOWN;
        }

        try {
            byte[] b = InetAddress.getByName(ip).getAddress();

            if (b.length == 4) {
                return (b[0] & 0xFF) + "." + (b[1] & 0xFF) + "." + (b[2] & 0xFF) + ".xxx";
            }

            if (b.length == 16) {
                return String.format("%x:%x:%x::xxxx",
                        ((b[0] & 0xFF) << 8) | (b[1] & 0xFF),
                        ((b[2] & 0xFF) << 8) | (b[3] & 0xFF),
                        ((b[4] & 0xFF) << 8) | (b[5] & 0xFF));
            }
        } catch (UnknownHostException ignored) {
            // fall through
        }

        return UNKNOWN;
    }
}