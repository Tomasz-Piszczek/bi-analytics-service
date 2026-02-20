package org.example.bianalyticsservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BiAnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BiAnalyticsServiceApplication.class, args);
    }

}
