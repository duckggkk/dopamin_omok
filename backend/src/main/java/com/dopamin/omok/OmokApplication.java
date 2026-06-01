package com.dopamin.omok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OmokApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmokApplication.class, args);
    }
}
