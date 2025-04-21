package dev.nexonm.distfs.metadata.dto;

import dev.nexonm.distfs.metadata.dto.response.ChunkResponse;
import dev.nexonm.distfs.metadata.dto.response.FileChunkedResponse;
import dev.nexonm.distfs.metadata.dto.response.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

public class FileMapper {

    public static FileUploadResponse mapFileToFileResponse(MultipartFile file, String newFileName) {
        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/files/download/")
                .path(newFileName)
                .toUriString();

        return FileUploadResponse.builder()
                .newFileName(newFileName)
                .originalFileName(file.getOriginalFilename())
                .downloadUri(fileDownloadUri)
                .fileType(file.getContentType())
                .fileSize(String.format("%d bytes", file.getSize()))
                .build();
    }

    public static FileChunkedResponse mapFileToFileChunkedResponse(MultipartFile file,
                                                                   List<ChunkResponse> chunks, long chunkSize) {

        return FileChunkedResponse.builder()
                .originalFileName(file.getOriginalFilename())
                .fileType(file.getContentType())
                .fileSize(String.format("%d bytes", file.getSize()))
                .chunkSize(String.format("%d bytes", chunkSize))
                .chunks(chunks)
                .build();

    }
}
