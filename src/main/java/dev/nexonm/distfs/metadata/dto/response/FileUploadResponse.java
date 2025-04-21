package dev.nexonm.distfs.metadata.dto.response;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class FileUploadResponse {

    String newFileName;
    String originalFileName;
    String downloadUri;
    String fileType;
    String fileSize;

}
