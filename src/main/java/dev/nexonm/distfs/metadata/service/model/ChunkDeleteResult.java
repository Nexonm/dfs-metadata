package dev.nexonm.distfs.metadata.service.model;

import dev.nexonm.distfs.metadata.entity.ChunkProperties;
import dev.nexonm.distfs.metadata.entity.StorageNode;

public record ChunkDeleteResult(ChunkProperties chunkProperties, StorageNode storageNode, boolean success) {
}
