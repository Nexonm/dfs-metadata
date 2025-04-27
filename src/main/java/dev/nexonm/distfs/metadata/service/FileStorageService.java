package dev.nexonm.distfs.metadata.service;


import dev.nexonm.distfs.metadata.dto.FileMapper;
import dev.nexonm.distfs.metadata.dto.response.ChunkResponse;
import dev.nexonm.distfs.metadata.dto.response.FileUploadResponse;
import dev.nexonm.distfs.metadata.entity.ChunkProperties;
import dev.nexonm.distfs.metadata.entity.FileProperties;
import dev.nexonm.distfs.metadata.entity.StorageNode;
import dev.nexonm.distfs.metadata.exception.FileStorageException;

import dev.nexonm.distfs.metadata.exception.StorageNodeException;
import dev.nexonm.distfs.metadata.repository.ChunkPropertiesRepository;
import dev.nexonm.distfs.metadata.repository.FilePropertiesRepository;
import dev.nexonm.distfs.metadata.repository.StorageNodeRepository;
import dev.nexonm.distfs.metadata.service.model.ChunkDivisionResult;
import dev.nexonm.distfs.metadata.service.model.ChunkSendResult;
import dev.nexonm.distfs.metadata.service.model.DistributionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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
    private final WebClient webClient;
    private final ChunkSizeCalculator chunkSizeCalculator;
    private final NodeHealthRegistry nodeHealthRegistry;
    private final ReplicationFactorCalculator replicationFactorCalculator;
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
        Map<Integer, ChunkDivisionResult> chunks = divideIntoChunks(fileProperties, file);
        // Add database persistence for file properties and chunks
        filePropertiesRepository.save(fileProperties);
        chunkPropertiesRepository.saveAll(fileProperties.getChunks());
        // 3+4. Create distribution
        List<DistributionResult> distribution = new ArrayList<>(distributeChunksWithReplication(chunks.size()));
        // 5. Send data to nodes
        List<ChunkSendResult> sendResults = parallelChunkSender.sendAllChunks(chunks, distribution);
        // persist chunk data with nodes
        persistChunksInDB(sendResults);
        // return file data to client
        return FileMapper.mapFiletoFileUploadResponse(file, chunks.size(), fileProperties.getId().toString());
    }

    private Map<Integer, ChunkDivisionResult> divideIntoChunks(FileProperties fileProperties, MultipartFile file) {
        Map<Integer, ChunkDivisionResult> results = new HashMap<>();

        int chunkSizeBytes = chunkSizeCalculator.calculateOptimalChunkSize(file.getSize());

        try (InputStream inputStream = file.getInputStream()) {
            byte[] buffer = new byte[chunkSizeBytes];
            int bytesRead;
            int chunkNumber = 1;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // Read data in as byte array
                byte[] chunkContent = Arrays.copyOf(buffer, bytesRead);
                // Create chunk properties
                ChunkProperties chunkProperty =
                        ChunkProperties.builder()
                                .chunkIndex(chunkNumber)
                                .chunkSize((long) chunkContent.length)
                                .file(fileProperties)
                                .id(UUID.randomUUID())
                                .build();
                // Add to list
                results.put(chunkNumber, new ChunkDivisionResult(chunkProperty, chunkContent));
                // Add chunk to FileProperties
                fileProperties.addChunk(chunkProperty);
                // Increase counter
                chunkNumber++;
            }

            log.info("Generated {} chunks for {}", results.size(), fileProperties.getFilename());
            fileProperties.setTotalChunks(chunkNumber - 1);
            return results;

        } catch (IOException ex) {
            throw new FileStorageException("Failed to store chunked file", ex);
        }
    }

    private List<DistributionResult> distributeChunksWithReplication(int chunksNumber) {
        // Round Robin implementation
        List<StorageNode> storageNodesList = storageNodeRepository.findAll().stream()
                .filter(node -> nodeHealthRegistry.isNodeHealthy(node.getId())).toList();
        if (storageNodesList.isEmpty()){
            throw new StorageNodeException("No active nodes. File cannot be transferred.");
        }
        // Get replication factor
        int replicationFactor = replicationFactorCalculator.getOptimalReplicationFactor();
        List<DistributionResult> results = new LinkedList<>();
        int nodeIndex = 0;
        for (int chunkIndex = 0; chunkIndex < chunksNumber; chunkIndex++) {
            for (int i = 0; i < replicationFactor; i++) {
                if (nodeIndex >= storageNodesList.size()) {
                    nodeIndex = 0;
                }
                results.add(new DistributionResult(storageNodesList.get(nodeIndex), chunkIndex+1));
                nodeIndex++;
            }
        }

        return results;
    }

    private void persistChunksInDB(List<ChunkSendResult> results){
        HashSet<ChunkProperties> chunkSet = new HashSet<>();
        results.forEach(result -> {
            if (result.result()){
                result.chunkProperties().addStorageNode(result.storageNode());
            }
            chunkSet.add(result.chunkProperties());
        });
        chunkPropertiesRepository.saveAll(chunkSet.stream().toList());
    }

}
