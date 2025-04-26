package dev.nexonm.distfs.metadata.service;

import dev.nexonm.distfs.metadata.dto.request.AddStorageNodeRequest;
import dev.nexonm.distfs.metadata.entity.StorageNode;
import dev.nexonm.distfs.metadata.exception.StorageNodeAlreadyRegisteredException;
import dev.nexonm.distfs.metadata.repository.StorageNodeRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class StorageNodeService {

    private final StorageNodeRepository repository;
    private final NodeHealthRegistry nodeHealthRegistry;


    @Transactional
    public String registerStorageNode(AddStorageNodeRequest request) {
        if (repository.existsByHostAddrAndPort(request.getHost(), request.getPort())) {
            // Check if server was unhealthy and recovered
            StorageNode node = repository.findByHostAddrAndPort(request.getHost(), request.getPort()).orElseThrow();
            if (!nodeHealthRegistry.isNodeHealthy(node.getId())) {
                nodeHealthRegistry.updateNodeHealth(node.getId(), true); // Recover
                return "Storage node was recovered.";
            }
            StorageNodeAlreadyRegisteredException e =
                    new StorageNodeAlreadyRegisteredException(request.getHost(), request.getPort());
            log.error(e.getMessage());
            throw e;
        }

        StorageNode node = StorageNode.builder()
                .id(UUID.randomUUID())
                .hostAddr(request.getHost())
                .port(request.getPort())
                .build();

        repository.save(node);
        // Save newly registered node
        nodeHealthRegistry.updateNodeHealth(node.getId(), true);

        return "Storage node was saved.";
    }
}
