package dev.nexonm.distfs.metadata.controller;

import dev.nexonm.distfs.metadata.dto.response.FileUploadResponse;
import dev.nexonm.distfs.metadata.service.FileStorageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(path = "/api/files")
@Slf4j
@AllArgsConstructor
public class FileStoreController {
    private final FileStorageService fileStorageService;

//    @PostMapping("/upload")
//    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
//        log.info("Received file upload request for file: {}", file.getOriginalFilename());
//
//        String fileName = fileStorageService.storeFile(file);
//
//        return ResponseEntity.ok(FileMapper.mapFileToFileResponse(file, fileName));
//    }

    @PostMapping("/uploadChunked")
    public ResponseEntity<FileUploadResponse> uploadFileChunked(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "50") int chunkSize) {

        return ResponseEntity.ok(fileStorageService.storeFileChunked(file, chunkSize));
    }
}
