package dev.nexonm.distfs.metadata.dto.response;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class FileUploadResponse {

    String fileUUID;
    String originalFileName;
    String fileType;
    String fileSize;
    String chunkNumber;

}
