package dev.nexonm.distfs.metadata.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDownloadResponse {

    String fileUUID;
    String filename;
    Long fileSizeBytes;
    List<ChunkAllocationResponse> chunks;
}
