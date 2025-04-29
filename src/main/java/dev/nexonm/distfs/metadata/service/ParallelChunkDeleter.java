package dev.nexonm.distfs.metadata.service;


import dev.nexonm.distfs.metadata.dto.request.ChunkDeleteRequest;
import dev.nexonm.distfs.metadata.entity.ChunkProperties;
import dev.nexonm.distfs.metadata.entity.FileProperties;
import dev.nexonm.distfs.metadata.entity.StorageNode;
import dev.nexonm.distfs.metadata.exception.StorageNodeException;
import dev.nexonm.distfs.metadata.service.model.ChunkDeleteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class ParallelChunkDeleter {

    private final WebClient webClient;

    @Value("${chunk.delete.timeout.all:120}")
    private long deletionTimeoutSeconds;

    @Value("${chunk.delete.timeout.single:30}")
    private long singleDeleteTimeoutSeconds;

    @Value("${chunk.delete.max-retries:3}")
    private int maxRetries;

    @Value("${chunk.delete.concurrency:10}")
    private int concurrencyLimit;

    /**
     * Sends delete requests to all storage nodes to delete chunks for a file
     *
     * @param fileProperties The file properties containing all chunks to delete
     * @return List of delete results for tracking operations
     */
    public List<ChunkDeleteResult> deleteAllChunks(FileProperties fileProperties) {
        log.info("Starting parallel deletion of chunks for file UUID: {}", fileProperties.getId());

        List<ChunkProperties> chunks = fileProperties.getChunks();
        List<ChunkDeleteResult> results = new ArrayList<>();

        // For each chunk, contact every storage node that has it
        results = Flux.fromIterable(chunks)
                .flatMap(chunk -> {
                    Set<StorageNode> nodes = chunk.getStorageNodes();
                    return Flux.fromIterable(nodes)
                            .flatMap(node -> deleteChunkFromNodeWithRetry(node, chunk)
                                    .map(success -> new ChunkDeleteResult(chunk, node, success)));
                }, concurrencyLimit)
                .collectList()
                .block(Duration.ofSeconds(deletionTimeoutSeconds));

        int successCount = (int) results.stream().filter(ChunkDeleteResult::success).count();
        log.info("Completed chunk deletion for file {}. Success: {}/{}",
                fileProperties.getId(), successCount, results.size());

        return results;
    }

    /**
     * Retry logic for deleting a chunk from a node.
     *
     * @param node The storage node to contact
     * @param chunk The chunk to delete
     * @return Boolean indicating success or failure
     */
    private Mono<Boolean> deleteChunkFromNodeWithRetry(StorageNode node, ChunkProperties chunk) {
        return Mono.defer(() -> deleteChunkFromNode(node, chunk))
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(500)).jitter(0.5))
                .onErrorResume(e -> {
                    log.warn("Failed to delete chunk {} from node {}:{} after {} attempts: {}",
                            chunk.getId(), node.getHostAddr(), node.getPort(), maxRetries, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Sends a delete request for a single chunk to a specific storage node.
     *
     * @param node The storage node to contact
     * @param chunk The chunk to delete
     * @return Boolean indicating success or failure
     */
    private Mono<Boolean> deleteChunkFromNode(StorageNode node, ChunkProperties chunk) {
        String url = String.format("http://%s:%d/api/chunk/delete", node.getHostAddr(), node.getPort());
        log.info("Sending delete request for chunk {} to {}:{}", chunk.getId(), node.getHostAddr(), node.getPort());

        try {
            ChunkDeleteRequest deleteRequest = new ChunkDeleteRequest(
                    chunk.getFile().getId().toString(),
                    chunk.getId().toString(),
                    chunk.getChunkIndex()
            );

            return webClient.method(HttpMethod.DELETE)
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(deleteRequest))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> {
                        log.error("Failed to delete chunk {} from node {}:{}, Status: {}",
                                chunk.getId(), node.getHostAddr(), node.getPort(), response.statusCode());
                        return Mono.error(
                                new StorageNodeException("Error deleting chunk with status: " + response.statusCode()));
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(singleDeleteTimeoutSeconds))
                    .map(response -> {
                        log.info("Successfully deleted chunk {} from node {}:{}",
                                chunk.getId(), node.getHostAddr(), node.getPort());
                        return true;
                    })
                    .onErrorResume(e -> {
                        log.error("Error deleting chunk {} from node {}:{}: {}",
                                chunk.getId(), node.getHostAddr(), node.getPort(), e.getMessage());
                        return Mono.just(false);
                    });
        } catch (Exception e) {
            log.error("Exception while attempting to delete chunk {} from node {}:{}: {}",
                    chunk.getId(), node.getHostAddr(), node.getPort(), e.getMessage());
            return Mono.just(false);
        }
    }
}
