package dev.nexonm.distfs.metadata.service;

import dev.nexonm.distfs.metadata.entity.ChunkProperties;
import dev.nexonm.distfs.metadata.entity.StorageNode;
import dev.nexonm.distfs.metadata.exception.StorageNodeException;
import dev.nexonm.distfs.metadata.service.model.ChunkDivisionResult;
import dev.nexonm.distfs.metadata.service.model.ChunkSendResult;
import dev.nexonm.distfs.metadata.service.model.DistributionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ParallelChunkSender {

    private final WebClient webClient;

    @Value("${chunk.send.timeout.all:120}")
    private long distributionTimeoutSeconds;

    @Value("${chunk.send.timeout.single:30}")
    private long sendTimeoutSeconds;

    @Value("${chunk.send.max-retries:3}")
    private int maxRetries;

    @Value("${chunk.send.concurrency:10}")
    private int concurrencyLimit;

    /**
     * Parallel sending of all chunks to the destinations
     * @param chunkMap map of <chunk index, chunk division>
     * @param distribution list of distributions
     */
    public List<ChunkSendResult> sendAllChunks(Map<Integer, ChunkDivisionResult> chunkMap,
                              List<DistributionResult> distribution) {
        log.info("Starting parallel sending of {} chunks to {} destinations", chunkMap.size(), distribution.size());

        // Process all distribution entries in parallel
        List<ChunkSendResult> results =  Flux.fromIterable(distribution)
                .flatMap(dist -> {
                    StorageNode node = dist.storageNode();
                    int chunkIndex = dist.chunkIndex();
                    ChunkDivisionResult chunk = chunkMap.get(chunkIndex);

                    return sendChunkToNodeWithRetry(node, chunk.chunkData(), chunk.chunkProperties())
                            .map(success -> {

                                return new ChunkSendResult(chunk.chunkProperties(), node, success);
                            });
                }, concurrencyLimit) // Control parallelism
                .collectList()
                .block(Duration.ofSeconds(distributionTimeoutSeconds));
        log.info("Finished parallel sending");
        return results;
    }

    /**
     * Retry logic for sending a chunk.
     *
     * @param node  node
     * @param data  byte array
     * @param chunk chunk properties
     * @return boolean result
     */
    private Mono<Boolean> sendChunkToNodeWithRetry(StorageNode node, byte[] data, ChunkProperties chunk) {

        return Mono.defer(() -> sendChunkToNode(node, data, chunk))
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(500)).jitter(0.5))
                .onErrorResume(e -> {
                    log.warn("Failed to send chunk {} to node {}:{} after {} attempts: {}",
                            chunk.getId(), node.getHostAddr(), node.getPort(), maxRetries, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Sends single chunk of data to the destination node.
     * @param node where to send
     * @param data what to send
     * @param chunk properties of data
     * @return boolean result
     */
    private Mono<Boolean> sendChunkToNode(StorageNode node, byte[] data, ChunkProperties chunk) {
        String url = String.format("http://%s:%d/api/chunk/upload", node.getHostAddr(), node.getPort());
        log.info("Sending chunk {} to {}:{}", chunk.getId(), node.getHostAddr(), node.getPort());

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
            bodyBuilder.part("hash", chunk.getHash());

            return webClient.post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> {
                        log.error("Failed to send chunk {} to node {}:{}, Status: {}", chunk.getId(),
                                node.getHostAddr(), node.getPort(), response.statusCode());
                        return Mono.error(
                                new StorageNodeException("Error sending chunk with status: " + response.statusCode()));
                    })
                    .bodyToMono(String.class)
                    .map(response -> {
                        log.info("Successfully sent chunk {} to node {}:{}", chunk.getId(), node.getHostAddr(),
                                node.getPort());
                        return true;
                    })
                    .onErrorResume(e -> Mono.just(false));
        } catch (Exception e) {
            return Mono.just(false);
        }
    }
}

