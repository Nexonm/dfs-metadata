package dev.nexonm.distfs.metadata.service;


import dev.nexonm.distfs.metadata.config.FileStorageProperties;
import dev.nexonm.distfs.metadata.dto.FileMapper;
import dev.nexonm.distfs.metadata.dto.response.ChunkResponse;
import dev.nexonm.distfs.metadata.dto.response.FileDownloadResponse;
import dev.nexonm.distfs.metadata.dto.response.FileUploadResponse;
import dev.nexonm.distfs.metadata.entity.ChunkProperties;
import dev.nexonm.distfs.metadata.entity.FileProperties;
import dev.nexonm.distfs.metadata.entity.StorageNode;
import dev.nexonm.distfs.metadata.exception.FileStorageException;

import dev.nexonm.distfs.metadata.exception.StorageNodeException;
import dev.nexonm.distfs.metadata.repository.ChunkPropertiesRepository;
import dev.nexonm.distfs.metadata.repository.FilePropertiesRepository;
import dev.nexonm.distfs.metadata.repository.StorageNodeRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {
    private final StorageNodeRepository storageNodeRepository;
    private final ChunkPropertiesRepository chunkPropertiesRepository;
    private final FilePropertiesRepository filePropertiesRepository;
    private final WebClient webClient;
    private final ChunkSizeCalculator chunkSizeCalculator;
    private final NodeHealthRegistry nodeHealthRegistry;

    private int replicationFactor;

    public FileStorageService(FileStorageProperties properties, StorageNodeRepository storageNodeRepository,
                              ChunkPropertiesRepository chunkPropertiesRepository,
                              FilePropertiesRepository filePropertiesRepository, WebClient webClient,
                              ChunkSizeCalculator chunkSizeCalculator, NodeHealthRegistry nodeHealthRegistry) {
        Path fileStorageLocation = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        this.storageNodeRepository = storageNodeRepository;
        this.chunkPropertiesRepository = chunkPropertiesRepository;
        this.filePropertiesRepository = filePropertiesRepository;
        this.webClient = webClient;
        this.replicationFactor = 2; // Set default replication factor
        this.chunkSizeCalculator = chunkSizeCalculator;
        this.nodeHealthRegistry = nodeHealthRegistry;

        //TODO: delete rhe fileStorageLocation.
        try {
            Files.createDirectories(fileStorageLocation);
        } catch (Exception ex) {
            throw new FileStorageException("Could not create the directory where the uploaded files will be stored.",
                    ex);
        }
    }

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
        List<ChunkDivisionResult> chunks = divideIntoChunks(fileProperties, file);
        // Add database persistence for file properties and chunks
        filePropertiesRepository.save(fileProperties);
        chunkPropertiesRepository.saveAll(fileProperties.getChunks());
        // 3+4. Create distribution
        List<DistributionResult> distribution = new ArrayList<>(distributeChunksWithReplication(chunks.size()));
        // 5. Send data to nodes
        sendDataToAllNodes(distribution, chunks);
        // return file data to client
        return FileMapper.mapFiletoFileUploadResponse(file, chunks.size(), fileProperties.getId().toString());
    }

    private List<ChunkDivisionResult> divideIntoChunks(FileProperties fileProperties, MultipartFile file) {
        List<ChunkDivisionResult> results = new LinkedList<>();

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
                results.add(new ChunkDivisionResult(chunkProperty, chunkContent));
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
        // TODO: check if there is only one node available
        List<DistributionResult> results = new LinkedList<>();
        int nodeIndex = 0;
        for (int chunkIndex = 0; chunkIndex < chunksNumber; chunkIndex++) {
            for (int i = 0; i < replicationFactor; i++) {
                if (nodeIndex >= storageNodesList.size()) {
                    nodeIndex = 0;
                }
                results.add(new DistributionResult(storageNodesList.get(nodeIndex), chunkIndex));
                nodeIndex++;
            }
        }

        return results;
    }

    private List<ChunkResponse> sendDataToAllNodes(List<DistributionResult> distribution,
                                                   List<ChunkDivisionResult> chunks) {
        List<ChunkResponse> chunkResponses = new ArrayList<>(distribution.size());
        for (int i = 0; i < distribution.size(); i++) {
            StorageNode node = distribution.get(i).storageNode();
            int index = distribution.get(i).chunkIndex();
            ChunkProperties chunkProperties = chunks.get(index).chunkProperties();
            if (sendDataToSingleNode(node, chunks.get(index).chunkData(), chunkProperties)) {
                chunkProperties.addStorageNode(node);
                // Update the chunk in database to record successful storage
                chunkPropertiesRepository.save(chunkProperties);

                chunkResponses.add(ChunkResponse.builder().chunkFileName(
                                String.format("%s_%s_chunk%d", chunkProperties.getFile().getId().toString(),
                                        chunkProperties.getId().toString(), chunkProperties.getChunkIndex()))
                        .host(String.format("http://%s:%d/api/chunk/upload", node.getHostAddr(), node.getPort()))
                        .chunkFileSizeBytes(chunkProperties.getChunkSize())
                        .build());
            } else {
                i--;
            }
        }
        return chunkResponses;
    }

    private boolean sendDataToSingleNode(StorageNode node, byte[] data, ChunkProperties chunk) {
        String url = String.format("http://%s:%d/api/chunk/upload", node.getHostAddr(), node.getPort());
        log.info("Sending chunk {} to {}", chunk.getId(), url);

        try {
            ByteArrayResource resource = new ByteArrayResource(data) {
                @Override
                public String getFilename() {
                    return chunk.getId().toString();
                }
            };

            // Create multipart body builder and add all parts
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("file", resource);
            bodyBuilder.part("chunkId", chunk.getId().toString());
            bodyBuilder.part("fileId", chunk.getFile().getId().toString());
            bodyBuilder.part("chunkIndex", chunk.getChunkIndex().toString());

            Boolean result = webClient.post().uri(url).contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build())).retrieve()
                    .onStatus(HttpStatusCode::isError, response -> {
                        log.error("Failed to send chunk {} to node {}:{}, Status: {}", chunk.getId(),
                                node.getHostAddr(), node.getPort(), response.statusCode());
                        return Mono.error(
                                new StorageNodeException("Error sending chunk with status: " + response.statusCode()));
                    }).bodyToMono(String.class).map(response -> {
                        log.info("Successfully sent chunk {} to node {}:{}", chunk.getId(), node.getHostAddr(),
                                node.getPort());
                        return true;
                    }).onErrorResume(e -> {
                        log.error("Failed to send chunk {} to node {}:{}", chunk.getId(), node.getHostAddr(),
                                node.getPort(), e);
                        return Mono.just(false);
                    }).block(Duration.ofSeconds(30)); // Add appropriate timeout

            return result != null && result;
        } catch (Exception e) {
            throw new StorageNodeException("Error sending chunk to node " + node.getHostAddr() + ":" + node.getPort(),
                    e);
        }
    }

    record ChunkDivisionResult(ChunkProperties chunkProperties, byte[] chunkData) {
    }

    record DistributionResult(StorageNode storageNode, int chunkIndex) {
    }

}
