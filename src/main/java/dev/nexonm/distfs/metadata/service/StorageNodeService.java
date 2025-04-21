package dev.nexonm.distfs.metadata.service;

import dev.nexonm.distfs.metadata.dto.request.AddStorageNodeRequest;
import dev.nexonm.distfs.metadata.entity.StorageNode;
import dev.nexonm.distfs.metadata.exception.StorageNodeAlreadyRegisteredException;
import dev.nexonm.distfs.metadata.repository.StorageNodeRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Service
@AllArgsConstructor
@Slf4j
public class StorageNodeService {

    private final StorageNodeRepository repository;


    @Transactional
    public String registerStorageNode(AddStorageNodeRequest request) {
        if (repository.existsByHostAddrAndPort(request.getHost(), request.getPort())) {
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

        return "Storage node was saved.";
    }
}
