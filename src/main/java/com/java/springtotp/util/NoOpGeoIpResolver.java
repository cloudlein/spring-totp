package com.java.springtotp.util;

import org.springframework.stereotype.Component;

@Component
public class NoOpGeoIpResolver implements GeoIpResolver{


    @Override
    public String resolve(String ip) {
        return "Unknown";
    }
}
