package dev.nexonm.distfs.metadata.service.model;

import dev.nexonm.distfs.metadata.entity.ChunkProperties;
import dev.nexonm.distfs.metadata.entity.StorageNode;

public record ChunkSendResult(ChunkProperties chunkProperties, StorageNode storageNode, boolean result) {
}
