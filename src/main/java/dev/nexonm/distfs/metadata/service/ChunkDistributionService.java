package dev.nexonm.distfs.metadata.service;

import dev.nexonm.distfs.metadata.entity.StorageNode;
import dev.nexonm.distfs.metadata.exception.StorageNodeException;
import dev.nexonm.distfs.metadata.service.model.DistributionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkDistributionService {
    private final NodeHealthRegistry nodeHealthRegistry;
    private final ReplicationFactorCalculator replicationFactorCalculator;


    public List<DistributionResult> distributeChunksWithReplication(List<StorageNode> nodes, int chunksNumber) {
        // Round Robin implementation
        List<StorageNode> storageNodesList = nodes.stream()
                .filter(node -> nodeHealthRegistry.isNodeHealthy(node.getId())).toList();
        if (storageNodesList.isEmpty()) {
            throw new StorageNodeException("No active nodes. File cannot be transferred.");
        }
        // Get replication factor
        int replicationFactor = replicationFactorCalculator.getOptimalReplicationFactor();
        List<DistributionResult> results = new LinkedList<>();
        int nodeIndex = 0;
        for (int chunkIndex = 0; chunkIndex < chunksNumber; chunkIndex++) {
            for (int i = 0; i < replicationFactor; i++) {
                if (nodeIndex >= storageNodesList.size()) {
                    nodeIndex = 0;
                }
                results.add(new DistributionResult(storageNodesList.get(nodeIndex), chunkIndex + 1));
                nodeIndex++;
            }
        }

        return results;
    }

}
