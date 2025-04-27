package dev.nexonm.distfs.metadata.service.model;

import dev.nexonm.distfs.metadata.entity.ChunkProperties;

public record ChunkDivisionResult(ChunkProperties chunkProperties, byte[] chunkData) {
}
