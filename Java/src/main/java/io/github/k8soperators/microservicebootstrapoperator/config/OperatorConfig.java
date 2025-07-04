package io.github.k8soperators.microservicebootstrapoperator.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OperatorConfig {

    @Bean
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }

    @Bean
    public Operator operator(KubernetesClient client) {
        return new Operator(client);
    }
}
