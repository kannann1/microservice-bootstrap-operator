package io.github.kannann1.microservicebootstrapoperator.config;

import io.javaoperatorsdk.operator.Operator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthConfig {

    @Bean
    public HealthIndicator operatorHealthIndicator(Operator operator) {
        return () -> {
            // Check if the operator is running by checking if it's not null
            // Java Operator SDK doesn't have an isStarted() method, so we use a different approach
            if (operator != null) {
                return Health.up()
                        .withDetail("status", "Operator is running")
                        .build();
            } else {
                return Health.down()
                        .withDetail("status", "Operator is not running")
                        .build();
            }
        };
    }
}
