package dev.nexonm.distfs.metadata.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileChunkedResponse {

    String originalFileName;
    String fileType;
    String fileSize;
    String chunkSize;
    List<ChunkResponse> chunks;
}
