package dev.nexonm.distfs.metadata.service;

import dev.nexonm.distfs.metadata.entity.ChunkProperties;
import dev.nexonm.distfs.metadata.entity.FileProperties;
import dev.nexonm.distfs.metadata.exception.FileStorageException;
import dev.nexonm.distfs.metadata.service.model.ChunkDivisionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileDivisionService {

    private final ChunkSizeCalculator chunkSizeCalculator;

    public Map<Integer, ChunkDivisionResult> divideIntoChunks(FileProperties fileProperties, MultipartFile file) {
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

}
