package io.github.kannann1.microservicebootstrapoperator.model;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Group("microservice.example.com")
@Version("v1")
public class AppConfig extends CustomResource<AppConfigSpec, AppConfigStatus> implements Namespaced {
    private static final long serialVersionUID = 1L;
}
