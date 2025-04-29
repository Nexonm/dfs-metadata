package dev.nexonm.distfs.metadata.controller;

import dev.nexonm.distfs.metadata.dto.request.FileDeleteRequest;
import dev.nexonm.distfs.metadata.dto.request.FileDownloadRequest;
import dev.nexonm.distfs.metadata.dto.response.FileDeleteResponse;
import dev.nexonm.distfs.metadata.dto.response.FileDownloadResponse;
import dev.nexonm.distfs.metadata.dto.response.FileUploadResponse;
import dev.nexonm.distfs.metadata.service.FileDeleteService;
import dev.nexonm.distfs.metadata.service.FileDownloadService;
import dev.nexonm.distfs.metadata.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(path = "/api/files")
@Slf4j
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService fileStorageService;
    private final FileDownloadService downloadService;
    private final FileDeleteService deleteService;


    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFileChunked(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "fileHash") String fileHash) {

        return ResponseEntity.ok(fileStorageService.storeFileChunked(file, fileHash));
    }

    /**
     * Strongly for browser testing
     */
    @Deprecated
    @GetMapping("/download/{fileUUID}")
    public ResponseEntity<FileDownloadResponse> downloadFileGet(@PathVariable String fileUUID) {
        return ResponseEntity.ok(downloadService.getFileAllocations(new FileDownloadRequest(fileUUID)));
    }

    @PostMapping("/download")
    public ResponseEntity<FileDownloadResponse> downloadFile(@RequestBody FileDownloadRequest request) {
        return ResponseEntity.ok(downloadService.getFileAllocations(request));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<FileDeleteResponse> deleteFile(@RequestBody FileDeleteRequest request){
        return ResponseEntity.ok(deleteService.deleteFile(request));
    }


}
