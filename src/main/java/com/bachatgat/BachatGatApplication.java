package com.bachatgat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bachat Gat / Self-Help Group (SHG) Management Platform
 * 
 * Production-ready Enterprise Application built for Google Cloud Run,
 * Google Cloud Firestore, Cloud Storage, Secret Manager and Cloud Scheduler.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties
public class BachatGatApplication {

    public static void main(String[] args) {
        SpringApplication.run(BachatGatApplication.class, args);
    }
}
