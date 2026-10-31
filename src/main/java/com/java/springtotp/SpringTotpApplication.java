package com.java.springtotp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringTotpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringTotpApplication.class, args);
    }

}
