package dev.nexonm.distfs.metadata.service;

import dev.nexonm.distfs.metadata.repository.StorageNodeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class NodeHealthRegistry {
    private final ConcurrentHashMap<UUID, Boolean> nodeHealthCache = new ConcurrentHashMap<>();
    private final StorageNodeRepository nodeRepository;

    /**
     * Check current state of the node from cache.
     * If there is no node in the cache, the false will be returned.
     * @param nodeId uuid of the node
     * @return true if node is active, false otherwise
     */
    public boolean isNodeHealthy(UUID nodeId){
        return nodeHealthCache.getOrDefault(nodeId, false);
    }

    /**
     * Updates health status of the provided node uuid.
     * If a new node is passed, the value will be just added.
     * @param nodeId uuid of the node
     * @param isHealthy current status of the node
     */
    public void updateNodeHealth(UUID nodeId, boolean isHealthy){
        nodeHealthCache.put(nodeId, isHealthy);
    }

    /**
     * Loads nodes' data from the database into cache.
     */
    @PostConstruct
    public void initializeCache(){
        nodeRepository.findAll().forEach(node -> nodeHealthCache.put(node.getId(), true));
    }

    /**
     * Get number of healthy nodes in the cache.
     * @return number of healthy nodes
     */
    public int getHealthyNodesCount(){
        return (int) nodeHealthCache.values().stream().filter(Boolean::booleanValue).count();
    }
}
