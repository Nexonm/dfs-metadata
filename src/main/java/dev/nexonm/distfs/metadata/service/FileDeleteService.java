package dev.nexonm.distfs.metadata.service;

import dev.nexonm.distfs.metadata.dto.request.FileDeleteRequest;
import dev.nexonm.distfs.metadata.dto.response.FileDeleteResponse;
import dev.nexonm.distfs.metadata.entity.ChunkProperties;
import dev.nexonm.distfs.metadata.entity.FileProperties;
import dev.nexonm.distfs.metadata.exception.CannotDeleteFileException;
import dev.nexonm.distfs.metadata.exception.FileNotFoundException;
import dev.nexonm.distfs.metadata.repository.ChunkPropertiesRepository;
import dev.nexonm.distfs.metadata.repository.FilePropertiesRepository;
import dev.nexonm.distfs.metadata.service.model.ChunkDeleteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileDeleteService {

    private final FilePropertiesRepository filePropertiesRepository;
    private final ChunkPropertiesRepository chunkPropertiesRepository;
    private final ParallelChunkDeleter parallelChunkDeleter;

    @Transactional
    public FileDeleteResponse deleteFile(FileDeleteRequest request) {
        FileProperties fileProperties = validateRequest(request);

        String filename = fileProperties.getFilename();
        log.info("Deleting file: {} (UUID: {})", filename, fileProperties.getId().toString());

        // Delete chunks from storage nodes
        List<ChunkDeleteResult> deleteResults = parallelChunkDeleter.deleteAllChunks(fileProperties);
        logChunkDeleteResults(deleteResults);
        // Delete from database
        deleteFromDatabase(fileProperties);

        log.info("Successfully deleted file: {} with UUID: {}", filename, fileProperties.getId().toString());

        return new FileDeleteResponse(
                true,
                "File deleted successfully",
                fileProperties.getId().toString(),
                filename
        );
    }

    private void deleteFromDatabase(FileProperties fileProperties) {
        // Update database
        List<ChunkProperties> chunks = fileProperties.getChunks();
        // For each chunk, properly remove all associations with storage nodes
        for (ChunkProperties chunk : chunks) {
            // Create a copy of the set to avoid ConcurrentModificationException
            new HashSet<>(chunk.getStorageNodes()).forEach(chunk::removeStorageNode);
        }

        // Save the updated chunks to persist the removal of associations (delete nodes allocation first)
        chunkPropertiesRepository.saveAll(chunks);
        // Delete the file (will cascade delete the chunks due to orphanRemoval=true in entity description)
        filePropertiesRepository.delete(fileProperties);
    }

    private void logChunkDeleteResults(List<ChunkDeleteResult> deleteResults) {
        int successCount = (int) deleteResults.stream().filter(ChunkDeleteResult::success).count();
        if (successCount==0){
            throw new CannotDeleteFileException(String.format("No chunks were deleted for file UUID: %s",
                    deleteResults.getFirst().chunkProperties().getFile().getId().toString()));
        }
        if (successCount < deleteResults.size()) {
            // Some chunks weren't deleted. Log the failures but continue with metadata removal
            log.warn("Failed to delete all chunks from storage nodes. Proceeding with metadata cleanup anyway.");
            deleteResults.stream()
                    .filter(result -> !result.success())
                    .forEach(result -> log.warn("Failed to delete chunk {} from node {}:{}",
                            result.chunkProperties().getId(),
                            result.storageNode().getHostAddr(),
                            result.storageNode().getPort()));
        }
    }

    private FileProperties validateRequest(FileDeleteRequest request) {
        UUID fileId;
        try {
            fileId = UUID.fromString(request.getFileUUID());
        } catch (IllegalArgumentException e) {
            log.error("Invalid file UUID format: {}", request.getFileUUID());
            throw new IllegalArgumentException(String.format("There is no file with UUID: %s", request.getFileUUID()));
//          for controller advice: new FileDeleteResponse(false, "Invalid file UUID", request.getFileUUID(), null);
        }

        // Find the file by UUID
        FileProperties fileProperties = filePropertiesRepository.findById(fileId)
                .orElse(null);

        if (fileProperties == null) {
            log.warn("File not found with UUID: {}", fileId);
            throw new FileNotFoundException(String.format("There is no file with UUID: %s", request.getFileUUID()));
//          for controller advice   new FileDeleteResponse(false, "File not found", fileId.toString(), null);
        }
        return fileProperties;
    }
}
