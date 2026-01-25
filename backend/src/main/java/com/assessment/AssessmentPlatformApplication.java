package com.assessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class AssessmentPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssessmentPlatformApplication.class, args);
    }
}
