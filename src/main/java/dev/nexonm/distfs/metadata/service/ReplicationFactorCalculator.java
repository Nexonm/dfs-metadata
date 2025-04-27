package dev.nexonm.distfs.metadata.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReplicationFactorCalculator {

    private final NodeHealthRegistry nodeHealthRegistry;

    @Value("${storage.replication.min:2}")
    private int minReplicationFactor;
    @Value("${storage.replication.max:5}")
    private int maxReplicationFactor;

    public int getOptimalReplicationFactor(){
        int activeNodeCount = nodeHealthRegistry.getHealthyNodesCount();
        if (activeNodeCount==1){
            return 1;
        }
        if (activeNodeCount <= minReplicationFactor) {
            return Math.max(2, activeNodeCount - 1); // All but one node for very small clusters
        }

        /*
         * RF 2: 3-4 nodes
         * RF 3: 5-8 nodes
         * RF 4: 9-16 nodes
         * RF 5: 17-32 nodes
         */
        int calculatedFactor = (int) Math.ceil(Math.log(activeNodeCount) / Math.log(2));
        // Get at least min value, at most max value, otherwise calculated factor
        return Math.min(maxReplicationFactor, Math.max(minReplicationFactor, calculatedFactor));
    }
}
