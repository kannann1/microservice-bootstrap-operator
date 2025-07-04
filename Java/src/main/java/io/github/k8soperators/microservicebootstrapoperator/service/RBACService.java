package io.github.kannann1.microservicebootstrapoperator.service;

import io.github.kannann1.microservicebootstrapoperator.model.AppConfig;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;



@Slf4j
@Service
@RequiredArgsConstructor
public class RBACService {

    private final KubernetesClient kubernetesClient;

    /**
     * Sets up RBAC for the given AppConfig
     *
     * @param appConfig the AppConfig resource
     */
    public void setupRBAC(AppConfig appConfig) {
        log.info("Setting up RBAC for: {}/{}", appConfig.getMetadata().getNamespace(), appConfig.getMetadata().getName());
        
        if (appConfig.getSpec().getRbac() == null) {
            log.info("No RBAC configuration found, skipping");
            return;
        }

        // Create ServiceAccount
        String serviceAccountName = appConfig.getSpec().getRbac().getServiceAccountName();
        createServiceAccount(appConfig, serviceAccountName);

        // Create Roles
        if (appConfig.getSpec().getRbac().getRoles() != null) {
            for (String roleName : appConfig.getSpec().getRbac().getRoles()) {
                createRole(appConfig, roleName);
            }
        }

        // Create RoleBindings
        if (appConfig.getSpec().getRbac().getRoleBindings() != null) {
            for (String roleBindingName : appConfig.getSpec().getRbac().getRoleBindings()) {
                createRoleBinding(appConfig, roleBindingName, serviceAccountName);
            }
        }
    }

    /**
     * Creates a ServiceAccount for the given AppConfig
     *
     * @param appConfig the AppConfig resource
     * @param serviceAccountName the name of the ServiceAccount to create
     */
    private void createServiceAccount(AppConfig appConfig, String serviceAccountName) {
        ServiceAccount serviceAccount = new ServiceAccountBuilder()
                .withNewMetadata()
                    .withName(serviceAccountName)
                    .withNamespace(appConfig.getMetadata().getNamespace())
                    .withOwnerReferences(createOwnerReference(appConfig))
                .endMetadata()
                .build();

        Resource<ServiceAccount> serviceAccountResource = kubernetesClient.serviceAccounts()
                .inNamespace(appConfig.getMetadata().getNamespace())
                .withName(serviceAccountName);

        if (serviceAccountResource.get() == null) {
            kubernetesClient.serviceAccounts()
                    .inNamespace(appConfig.getMetadata().getNamespace())
                    .resource(serviceAccount)
                    .create();
            log.info("Created ServiceAccount: {}/{}", appConfig.getMetadata().getNamespace(), serviceAccountName);
        } else {
            log.info("ServiceAccount already exists: {}/{}", appConfig.getMetadata().getNamespace(), serviceAccountName);
        }

        // Track created resource
        String resourceName = String.format("ServiceAccount/%s", serviceAccountName);
        if (appConfig.getStatus() != null) {
            appConfig.getStatus().addCreatedResource(resourceName);
        }
    }

    /**
     * Creates a Role for the given AppConfig
     *
     * @param appConfig the AppConfig resource
     * @param roleName the name of the Role to create
     */
    private void createRole(AppConfig appConfig, String roleName) {
        // In a real implementation, you would define specific rules based on the role name
        // For demonstration, we'll create a simple role with read access to configmaps
        
        Role role = new RoleBuilder()
                .withNewMetadata()
                    .withName(roleName)
                    .withNamespace(appConfig.getMetadata().getNamespace())
                    .withOwnerReferences(createOwnerReference(appConfig))
                .endMetadata()
                .addNewRule()
                    .withApiGroups("")
                    .withResources("configmaps")
                    .withVerbs("get", "list", "watch")
                .endRule()
                .build();

        Resource<Role> roleResource = kubernetesClient.rbac().roles()
                .inNamespace(appConfig.getMetadata().getNamespace())
                .withName(roleName);

        if (roleResource.get() == null) {
            kubernetesClient.rbac().roles()
                    .inNamespace(appConfig.getMetadata().getNamespace())
                    .resource(role)
                    .create();
            log.info("Created Role: {}/{}", appConfig.getMetadata().getNamespace(), roleName);
        } else {
            kubernetesClient.rbac().roles()
                    .inNamespace(appConfig.getMetadata().getNamespace())
                    .resource(role)
                    .serverSideApply();
            log.info("Updated Role: {}/{}", appConfig.getMetadata().getNamespace(), roleName);
        }

        // Track created resource
        String resourceName = String.format("Role/%s", roleName);
        if (appConfig.getStatus() != null) {
            appConfig.getStatus().addCreatedResource(resourceName);
        }
    }

    /**
     * Creates a RoleBinding for the given AppConfig
     *
     * @param appConfig the AppConfig resource
     * @param roleBindingName the name of the RoleBinding to create
     * @param serviceAccountName the name of the ServiceAccount to bind to
     */
    private void createRoleBinding(AppConfig appConfig, String roleBindingName, String serviceAccountName) {
        // In a real implementation, you would determine the appropriate role based on the binding name
        // For demonstration, we'll use the same name for the role and the binding
        
        RoleBinding roleBinding = new RoleBindingBuilder()
                .withNewMetadata()
                    .withName(roleBindingName)
                    .withNamespace(appConfig.getMetadata().getNamespace())
                    .withOwnerReferences(createOwnerReference(appConfig))
                .endMetadata()
                .withNewRoleRef()
                    .withApiGroup("rbac.authorization.k8s.io")
                    .withKind("Role")
                    .withName(roleBindingName.replace("Binding", ""))
                .endRoleRef()
                .addNewSubject()
                    .withKind("ServiceAccount")
                    .withName(serviceAccountName)
                    .withNamespace(appConfig.getMetadata().getNamespace())
                .endSubject()
                .build();

        Resource<RoleBinding> roleBindingResource = kubernetesClient.rbac().roleBindings()
                .inNamespace(appConfig.getMetadata().getNamespace())
                .withName(roleBindingName);

        if (roleBindingResource.get() == null) {
            kubernetesClient.rbac().roleBindings()
                    .inNamespace(appConfig.getMetadata().getNamespace())
                    .resource(roleBinding)
                    .create();
            log.info("Created RoleBinding: {}/{}", appConfig.getMetadata().getNamespace(), roleBindingName);
        } else {
            kubernetesClient.rbac().roleBindings()
                    .inNamespace(appConfig.getMetadata().getNamespace())
                    .resource(roleBinding)
                    .serverSideApply();
            log.info("Updated RoleBinding: {}/{}", appConfig.getMetadata().getNamespace(), roleBindingName);
        }

        // Track created resource
        String resourceName = String.format("RoleBinding/%s", roleBindingName);
        if (appConfig.getStatus() != null) {
            appConfig.getStatus().addCreatedResource(resourceName);
        }
    }

    /**
     * Creates an owner reference for the given AppConfig
     *
     * @param appConfig the AppConfig resource
     * @return the owner reference
     */
    private io.fabric8.kubernetes.api.model.OwnerReference createOwnerReference(AppConfig appConfig) {
        return new io.fabric8.kubernetes.api.model.OwnerReferenceBuilder()
                .withApiVersion(appConfig.getApiVersion())
                .withKind(appConfig.getKind())
                .withName(appConfig.getMetadata().getName())
                .withUid(appConfig.getMetadata().getUid())
                .withBlockOwnerDeletion(true)
                .withController(true)
                .build();
    }
}
