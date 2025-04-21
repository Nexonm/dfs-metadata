package dev.nexonm.distfs.metadata.controller;

import dev.nexonm.distfs.metadata.dto.request.AddStorageNodeRequest;
import dev.nexonm.distfs.metadata.service.StorageNodeService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/node")
@AllArgsConstructor
@Slf4j
public class StorageNodeController {

    private final StorageNodeService service;

    @PostMapping("/register")
    public ResponseEntity<String> registerStorageNode(@RequestBody AddStorageNodeRequest request){
        log.info("Request for node register with address={}:{}", request.getHost(), request.getPort());
        return ResponseEntity.ok(service.registerStorageNode(request));
    }
}
