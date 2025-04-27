package dev.nexonm.distfs.metadata.service;


import dev.nexonm.distfs.metadata.dto.FileMapper;
import dev.nexonm.distfs.metadata.dto.response.FileUploadResponse;
import dev.nexonm.distfs.metadata.entity.ChunkProperties;
import dev.nexonm.distfs.metadata.entity.FileProperties;
import dev.nexonm.distfs.metadata.repository.ChunkPropertiesRepository;
import dev.nexonm.distfs.metadata.repository.FilePropertiesRepository;
import dev.nexonm.distfs.metadata.repository.StorageNodeRepository;
import dev.nexonm.distfs.metadata.service.model.ChunkDivisionResult;
import dev.nexonm.distfs.metadata.service.model.ChunkSendResult;
import dev.nexonm.distfs.metadata.service.model.DistributionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageService {
    private final StorageNodeRepository storageNodeRepository;
    private final ChunkPropertiesRepository chunkPropertiesRepository;
    private final FilePropertiesRepository filePropertiesRepository;
    private final FileDivisionService fileDivisionService;
    private final ChunkDistributionService chunkDistributionService;
    private final ParallelChunkSender parallelChunkSender;


    public FileUploadResponse storeFileChunked(MultipartFile file, int chunkSizeBytes) {
        /** The flow of the file storage process is as follows:
         * 1. Get file
         * 2. Divide into chunks
         * 3. Get number of available hosts and replication factor -> Get for multiple hosts
         * 4. Create distribution -> Distribute over multiple hosts with replication factor
         * 5. Send data to nodes -> send to all nodes (need change of node process if node is down)
         * 6. Recieve answer from nodes
         * 7. Save data into database
         * 8. Return answer
         */

        // 1. Got a file
        FileProperties fileProperties = FileProperties.builder()
                .id(UUID.randomUUID())
                .filename(StringUtils.cleanPath(file.getOriginalFilename()))
                .totalSize(file.getSize())
                .build();
        // 2. Divide into chunks
        Map<Integer, ChunkDivisionResult> chunks = fileDivisionService.divideIntoChunks(fileProperties, file);
        // Add database persistence for file properties and chunks
        filePropertiesRepository.save(fileProperties);
        chunkPropertiesRepository.saveAll(fileProperties.getChunks());
        // 3+4. Create distribution
        List<DistributionResult> distribution = new ArrayList<>(
                chunkDistributionService.distributeChunksWithReplication(storageNodeRepository.findAll(), chunks.size())
        );
        // 5. Send data to nodes
        List<ChunkSendResult> sendResults = parallelChunkSender.sendAllChunks(chunks, distribution);
        // persist chunk data with nodes
        persistChunksInDB(sendResults);
        // return file data to client
        return FileMapper.mapFiletoFileUploadResponse(file, chunks.size(), fileProperties.getId().toString());
    }

    private void persistChunksInDB(List<ChunkSendResult> results) {
        HashSet<ChunkProperties> chunkSet = new HashSet<>();
        results.forEach(result -> {
            if (result.result()) {
                result.chunkProperties().addStorageNode(result.storageNode());
            }
            chunkSet.add(result.chunkProperties());
        });
        chunkPropertiesRepository.saveAll(chunkSet.stream().toList());
    }

}
