package dev.nexonm.distfs.metadata.controller;

import dev.nexonm.distfs.metadata.dto.request.FileDownloadRequest;
import dev.nexonm.distfs.metadata.dto.response.ChunkAllocationResponse;
import dev.nexonm.distfs.metadata.dto.response.FileDownloadResponse;
import dev.nexonm.distfs.metadata.service.FileDownloadService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/storage")
@AllArgsConstructor
public class FileDownloadController {


    private FileDownloadService service;

    /**
     * Strongly for browser testing
     */
    @GetMapping("/download/{fileUUID}")
    public ResponseEntity<FileDownloadResponse> downloadFileGet(@PathVariable String fileUUID) {
        return ResponseEntity.ok(service.getFileAllocations(new FileDownloadRequest(fileUUID)));
    }

    @PostMapping("/download")
    public ResponseEntity<FileDownloadResponse> downloadFile(@RequestBody FileDownloadRequest request) {
        return ResponseEntity.ok(service.getFileAllocations(request));
    }

}
