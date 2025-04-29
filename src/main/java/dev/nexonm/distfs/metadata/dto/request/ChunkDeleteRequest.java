package dev.nexonm.distfs.metadata.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChunkDeleteRequest {
    private String fileUUID;
    private String chunkUUID;
    private int chunkIndex;
}
