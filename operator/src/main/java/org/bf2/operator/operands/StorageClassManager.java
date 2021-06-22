package org.bf2.operator.operands;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.TopologySelectorLabelRequirement;
import io.fabric8.kubernetes.api.model.TopologySelectorTerm;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.api.model.storage.StorageClassBuilder;
import io.fabric8.kubernetes.api.model.storage.StorageClassList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.quarkus.runtime.Startup;
import org.bf2.common.OperandUtils;
import org.bf2.common.ResourceInformer;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Startup
@ApplicationScoped
public class StorageClassManager {

    private static final String TOPOLOGY_KEY = "topology.kubernetes.io/zone";

    @Inject
    Logger log;

    @Inject
    KubernetesClient kubernetesClient;

    ResourceInformer<Node> nodeInformer;
    ResourceInformer<StorageClass> storageClassInformer;

    private volatile List<String> storageClassNames;

    public List<String> getStorageClassNames() {
        return storageClassNames;
    }

    @PostConstruct
    protected void onStart() throws InterruptedException {
        MixedOperation<StorageClass, StorageClassList, Resource<StorageClass>> storageClasses =
                kubernetesClient.storage().storageClasses();

        nodeInformer = ResourceInformer.start(Node.class, kubernetesClient.nodes().withLabel("node-role.kubernetes.io/worker"), new ResourceEventHandler<Node>() {
            @Override public void onAdd(Node obj) {/* do nothing */}
            @Override public void onUpdate(Node oldObj, Node newObj) {/* do nothing */}
            @Override public void onDelete(Node obj, boolean deletedFinalStateUnknown) {/* do nothing */}
        });

        storageClassInformer = ResourceInformer.start(StorageClass.class, storageClasses, new ResourceEventHandler<StorageClass>() {

            @Override
            public void onAdd(StorageClass obj) {
                reconcileStorageClasses();
            }

            @Override
            public void onUpdate(StorageClass oldObj, StorageClass newObj) {
                reconcileStorageClasses();
            }

            @Override
            public void onDelete(StorageClass obj, boolean deletedFinalStateUnknown) {
                reconcileStorageClasses();
            }
        });

        reconcileStorageClasses();
    }

    private void reconcileStorageClasses() {
        if (null == nodeInformer || !nodeInformer.isReady() || null == storageClassInformer || !storageClassInformer.isReady()) {
            log.warn("Informers not yet initialized or ready");
            return;
        }

        List<String> zones = nodeInformer.getList().stream()
                .filter(Objects::nonNull)
                .map(node -> node.getMetadata().getLabels().get(TOPOLOGY_KEY))
                .distinct()
                .collect(Collectors.toList());

        List<StorageClass> cachedstorageClasses = storageClassInformer.getList().stream()
                .filter(Objects::nonNull)
                .filter(sc -> sc.getMetadata().getLabels() != null)
                .filter(sc -> sc.getMetadata().getLabels().containsKey(OperandUtils.getDefaultLabels().keySet().iterator().next()))
                .collect(Collectors.toList());

        List<StorageClass> storageClasses = storageClassesFrom(mapStorageClassesToZones(zones, cachedstorageClasses));
        storageClasses.stream().forEach(storageClass -> OperandUtils.createOrUpdate(kubernetesClient.storage().storageClasses(), storageClass));

        if (storageClasses.size() >= 3) {
            storageClassNames = storageClasses.stream().map(sc -> sc.getMetadata().getName()).sorted().collect(Collectors.toList());
        } else {
            String defaultStorageClass = storageClassInformer.getList().stream()
                    .filter(sc -> sc.getMetadata().getAnnotations() != null && "true".equals(sc.getMetadata().getAnnotations().get("storageclass.kubernetes.io/is-default-class")))
                    .map(sc -> sc.getMetadata().getName())
                    .findFirst().orElse("");

            log.warn("Not enough AZs were discovered from node metadata, so the default storage class will be used instead: " + defaultStorageClass);
            storageClassNames = List.of(defaultStorageClass, defaultStorageClass, defaultStorageClass);
        }
    }


    private Map<String, StorageClass> mapStorageClassesToZones(List<String> zones, List<StorageClass> storageClasses) {
        Map<String, StorageClass> zonedStorageClasses = storageClasses.stream()
                .filter(sc -> sc.getMetadata().getLabels().containsKey(TOPOLOGY_KEY))
                .collect(Collectors.toMap(sc -> sc.getMetadata().getLabels().get(TOPOLOGY_KEY), sc -> sc));

        zones.forEach(zone -> {
            if (!zonedStorageClasses.keySet().contains(zone)) {
                zonedStorageClasses.put(zone, null);
            }
        });

        return zonedStorageClasses;
    }

    private List<StorageClass> storageClassesFrom(Map<String, StorageClass> storageClasses) {
        return storageClasses.entrySet().stream().map(e -> {
            StorageClassBuilder builder = e.getValue() != null ? new StorageClassBuilder(e.getValue()) : new StorageClassBuilder();
            return builder
                    .editOrNewMetadata()
                        .withName("kas-" + e.getKey())
                        .withLabels(OperandUtils.getDefaultLabels())
                    .endMetadata()
                    .withProvisioner("kubernetes.io/aws-ebs")
                    .withReclaimPolicy("Delete")
                    .withVolumeBindingMode("WaitForFirstConsumer")
                    .withAllowVolumeExpansion(true)
                    .withParameters(getStorageClassParameters())
                    .withAllowedTopologies(getTopologySelectorTerm(e.getKey()))
                    .build();
        }).collect(Collectors.toList());
    }

    private Map<String, String> getStorageClassParameters() {
        return Map.of(
                "type", "gp2",
                "encrypted", "true"
                );
    }

    private TopologySelectorTerm getTopologySelectorTerm(String zone) {
        TopologySelectorLabelRequirement requirement = new TopologySelectorLabelRequirement(TOPOLOGY_KEY, Collections.singletonList(zone));
        List<TopologySelectorLabelRequirement> requirements = Collections.singletonList(requirement);
        return new TopologySelectorTerm(requirements);
    }


}