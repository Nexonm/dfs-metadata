package dev.nexonm.distfs.metadata.service;

import dev.nexonm.distfs.metadata.dto.request.FileDownloadRequest;
import dev.nexonm.distfs.metadata.dto.response.ChunkAllocationResponse;
import dev.nexonm.distfs.metadata.dto.response.FileDownloadResponse;
import dev.nexonm.distfs.metadata.dto.response.HostResponse;
import dev.nexonm.distfs.metadata.entity.ChunkProperties;
import dev.nexonm.distfs.metadata.entity.FileProperties;
import dev.nexonm.distfs.metadata.entity.StorageNode;
import dev.nexonm.distfs.metadata.repository.FilePropertiesRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.UUID;

@Service
@Slf4j
@AllArgsConstructor
public class FileDownloadService {

    private final FilePropertiesRepository filePropertiesRepository;


    public FileDownloadResponse getFileAllocations(FileDownloadRequest request){
        String fileUUID = validateRequest(request);
        FileProperties fileProperties = filePropertiesRepository.findById(UUID.fromString(fileUUID)).orElseThrow(
                () -> new IllegalArgumentException(String.format("The file with UUID=%s does not exist.", fileUUID))
        );
        FileDownloadResponse response = FileDownloadResponse.builder()
                .fileUUID(fileUUID)
                .filename(fileProperties.getFilename())
                .fileSizeBytes(fileProperties.getTotalSize())
                .chunks(new LinkedList<>())
                .build();
        for (ChunkProperties chunk : fileProperties.getChunks()){
            ChunkAllocationResponse chunkAllocation = ChunkAllocationResponse.builder()
                    .chunkUUID(chunk.getId().toString())
                    .chunkIndex(chunk.getChunkIndex())
                    .chunkSizeBytes(chunk.getChunkSize().intValue())
                    .hosts(new LinkedList<>())
                    .build();
            for(StorageNode storageNode : chunk.getStorageNodes()){
                chunkAllocation.getHosts().add(
                        new HostResponse(storageNode.getHostAddr(), storageNode.getPort())
                );
            }
            response.getChunks().add(chunkAllocation);
        }

        return response;
    }

    private String validateRequest(FileDownloadRequest request){
        String fileUUID = request.getFileUUID();
        if (fileUUID == null || fileUUID.strip().isBlank() || fileUUID.length()!=36){
            log.error("The fileUUID is null or empty or is too big, UUID={}", fileUUID);
            throw new IllegalArgumentException("The fileUUID is null or empty");
        }
        return fileUUID;
    }


}
