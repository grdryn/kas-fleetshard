package org.bf2.performance;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.bf2.operator.operands.KafkaInstanceConfiguration;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdopterProfile {

    public static final KafkaInstanceConfiguration VALUE_PROD = buildProfile(
            "4Gi", "2Gi", "1000m",
            "8Gi", "3Gi", "3000m");

    public static final KafkaInstanceConfiguration SMALL_VALUE_PROD = buildProfile(
            "1Gi", "500Mi", "500m",
            "1Gi", "500Mi", "1000m");

    public static final KafkaInstanceConfiguration TYPE_KICKER = buildProfile(
            "2Gi", "1Gi", "500m",
            "2Gi", "1Gi", "500m");

    public static KafkaInstanceConfiguration buildProfile(String zookeeperContainerMemory, String zookeeperJavaMemory,
            String zookeeperCpu, String kafkaContainerMemory, String kafkaJavaMemory, String kafkaCpu) {
        KafkaInstanceConfiguration config = new KafkaInstanceConfiguration();
        config.getKafka().setContainerMemory(kafkaContainerMemory);
        config.getKafka().setContainerCpu(kafkaCpu);
        config.getKafka().setJvmXms(kafkaJavaMemory);
        config.getZookeeper().setContainerCpu(zookeeperCpu);
        config.getZookeeper().setContainerMemory(zookeeperContainerMemory);
        config.getZookeeper().setJvmXms(zookeeperJavaMemory);
        return config;
    }
}