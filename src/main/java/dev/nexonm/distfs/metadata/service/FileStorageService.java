package dev.nexonm.distfs.metadata.service;


import dev.nexonm.distfs.metadata.dto.FileMapper;
import dev.nexonm.distfs.metadata.dto.response.FileUploadResponse;
import dev.nexonm.distfs.metadata.entity.ChunkProperties;
import dev.nexonm.distfs.metadata.entity.FileProperties;
import dev.nexonm.distfs.metadata.exception.ChunkWasNotSentToNodes;
import dev.nexonm.distfs.metadata.exception.HashIsNotEqualException;
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
    private final HashGenerationService hashGenerationService;


    public FileUploadResponse storeFileChunked(MultipartFile file, String fileHash) {
        /** The flow of the file storage process is as follows:
         * 1. Get file
         * 2. Divide into chunks
         * 3. Get number of available hosts and replication factor -> Get for multiple hosts
         * 4. Create distribution -> Distribute over multiple hosts with replication factor
         * 5. Send data to nodes -> send to all nodes (need change of node process if node is down)
         * 6. Receive answer from nodes
         * 7. Save data into database
         * 8. Return answer
         */

        // Check that the hash is correct.
        String calculatedHash = validateFileAndHash(file, fileHash);

        // 1. Got a file
        FileProperties fileProperties = FileProperties.builder()
                .id(UUID.randomUUID())
                .filename(StringUtils.cleanPath(file.getOriginalFilename()))
                .hash(calculatedHash)
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
        validateAndPersistChunksInDB(sendResults);
        // return file data to client
        return FileMapper.mapFiletoFileUploadResponse(file, chunks.size(), fileProperties.getId().toString());
    }

    private void validateAndPersistChunksInDB(List<ChunkSendResult> results) {
        HashSet<ChunkProperties> chunkSet = new HashSet<>();
        results.forEach(result -> {
            if (result.result()) {
                result.chunkProperties().addStorageNode(result.storageNode());
            }
            chunkSet.add(result.chunkProperties());
        });
        chunkSet.stream().forEach( chunk -> {
            if (chunk.getStorageNodes().size()==0){
                throw new ChunkWasNotSentToNodes(String.format("Chunk ID: %s", chunk.getId()));
            }
        });
        chunkPropertiesRepository.saveAll(chunkSet.stream().toList());
    }

    /**
     * Checks if file and hash are correctly provided and no corruption occurred.
     * @param file file data
     * @param hash file hash from frontend
     * @return hash of file as a string
     */
    private String validateFileAndHash(MultipartFile file, String hash) {
        if (file == null) {
            log.error("Provided file is null.");
            throw new IllegalArgumentException("File is null.");
        }
        if (hash == null || hash.isBlank()) {
            log.error("Provided hash string is null or empty.");
            throw new IllegalArgumentException("Hash is null or empty.");
        }
        String calculatedHash = hashGenerationService.generateFileHash(file);

        if (!calculatedHash.equalsIgnoreCase(hash)) {
            log.error("Hash Verification: FALSE. Provided hash \"{}\" doesn't equal to local test \"{}\"", hash, calculatedHash);
            throw new HashIsNotEqualException(String.format("Provided hash \"%s\" doesn't equal to local test \"%s\"", hash, calculatedHash));
        }
        log.info("Hash Verification: TRUE. Provided hash equals to local test \"{}\"", calculatedHash);
        return calculatedHash;
    }


}
