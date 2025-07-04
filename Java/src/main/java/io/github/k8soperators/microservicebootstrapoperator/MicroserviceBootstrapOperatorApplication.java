package io.github.kannann1.microservicebootstrapoperator;

import io.github.kannann1.microservicebootstrapoperator.model.AppConfig;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class MicroserviceBootstrapOperatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicroserviceBootstrapOperatorApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(KubernetesClient client, Operator operator, 
                                              Reconciler<AppConfig> appConfigReconciler) {
        return args -> {
            log.info("Starting Microservice Bootstrap Operator");
            
            // Register the AppConfig controller with the operator
            operator.register(appConfigReconciler);
            
            // Start the operator
            operator.start();
            
            log.info("Microservice Bootstrap Operator started successfully");
        };
    }
}
