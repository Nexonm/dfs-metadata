package dev.nexonm.distfs.metadata.dto;

import dev.nexonm.distfs.metadata.dto.response.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public class FileMapper {


    public static FileUploadResponse mapFiletoFileUploadResponse(MultipartFile file, long chunkNumber,
                                                                 String fileUUID) {

        return FileUploadResponse.builder()
                .fileUUID(fileUUID)
                .originalFileName(file.getOriginalFilename())
                .fileType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .chunkNumber(chunkNumber)
                .build();

    }
}
