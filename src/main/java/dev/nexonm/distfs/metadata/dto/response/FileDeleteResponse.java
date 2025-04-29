package dev.nexonm.distfs.metadata.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileDeleteResponse {
    private boolean success;
    private String message;
    private String fileId;
    private String filename;
}