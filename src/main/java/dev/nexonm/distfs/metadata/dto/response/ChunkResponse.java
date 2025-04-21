package dev.nexonm.distfs.metadata.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkResponse {

    String chunkFileName;
    long chunkFileSizeBytes;
    String host;

}
