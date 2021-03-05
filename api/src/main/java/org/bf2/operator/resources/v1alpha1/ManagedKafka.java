package org.bf2.operator.resources.v1alpha1;

import io.dekorate.crd.annotation.Crd;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.BuildableReference;

/**
 * Represents a ManagedKafka instance declaration with corresponding specification and status
 */
@Buildable(
        builderPackage = "io.fabric8.kubernetes.api.builder",
        refs = @BuildableReference(CustomResource.class),
        editableEnabled = false
)
@Group("managedkafka.bf2.org")
@Version("v1alpha1")
@Crd(group = "managedkafka.bf2.org", version = "v1alpha1")
public class ManagedKafka extends CustomResource<ManagedKafkaSpec, ManagedKafkaStatus> implements Namespaced {

    private String id;

    /**
     * The id of the ManagedKafka, aka the placement id
     * @return
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}