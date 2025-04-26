package dev.nexonm.distfs.metadata.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChunkSizeCalculator {

    @Value("${storage.chunk.size.min:1048576}") // 1048576 bytes = 1024^2 = 1MB
    private int minChunkSize;

    @Value("${storage.chunk.size.max:67108864}") // 67108864 bytes = 64 * 1024^2 = 64MB
    private int maxChunkSize;

    @Value("${storage.chunk.count:10}") // target chunks is 10
    private int targetChunkCount;

    private static final int MAX_CHUNK_COUNT = 20;

    public int calculateOptimalChunkSize(long fileSize){
        // Check if file size less than min chunk size
        if (fileSize < minChunkSize){
            return (int) fileSize;
        }

        // Divide into target chunk size
        int calculatedSize = (int) Math.ceil((double) fileSize / targetChunkCount);

        // Min size for one chunk in order to limit number of chunks
        int sizeToPreventTooManyChunks = (int) Math.ceil((double) fileSize / MAX_CHUNK_COUNT);

        // Take the maximum of calculated sizes to avoid too many small chunks
        calculatedSize = Math.max(calculatedSize, sizeToPreventTooManyChunks);

        // Ensure we're within the configured boundaries
        calculatedSize = Math.max(calculatedSize, minChunkSize);
        calculatedSize = Math.min(calculatedSize, maxChunkSize);
        calculatedSize = Math.min(calculatedSize, (int) fileSize);

        log.info("Calculated chunk size: {} bytes for file size: {} bytes, result in {} chunks",
                calculatedSize, fileSize, (int) Math.ceil((double) fileSize/ calculatedSize));

        return calculatedSize;
    }

}
