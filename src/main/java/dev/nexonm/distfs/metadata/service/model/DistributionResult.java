package dev.nexonm.distfs.metadata.service.model;

import dev.nexonm.distfs.metadata.entity.StorageNode;

public record DistributionResult(StorageNode storageNode, int chunkIndex) {
}
