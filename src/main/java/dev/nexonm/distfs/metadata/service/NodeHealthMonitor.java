package dev.nexonm.distfs.metadata.service;

import dev.nexonm.distfs.metadata.entity.StorageNode;
import dev.nexonm.distfs.metadata.repository.StorageNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class NodeHealthMonitor {
    private final StorageNodeRepository storageNodeRepository;
    private final NodeHealthRegistry nodeHealthRegistry;
    private final WebClient webClient;

    @Value("${health-check.timeout:2s}")
    private Duration timeout;

    @Value("${health-check.path:/api/node/health}")
    private String healthEndpointPath;


    /**
     * Checks health of all storage nodes every 15 seconds
     */
    @Scheduled(fixedRateString = "${health-check.interval}", timeUnit = TimeUnit.MILLISECONDS)
    public void checkNodesHealth() {
        log.debug("Starting scheduled health check for all storage nodes");

        List<StorageNode> nodes = storageNodeRepository.findAll();
        if (nodes.isEmpty()) {
            log.warn("No storage nodes registered in the system");
            return;
        }

        // Count health status before checks
        log.info("Current node health status: {}/{} healthy", nodeHealthRegistry.getHealthyNodesCount(), nodes.size());

        // Check all nodes in parallel for efficiency
        List<Mono<StorageNode>> checks = nodes.stream()
                .map(this::checkNodeHealthAsync)
                .toList(); // Create mono objects. Similar to list of threads that will be executed further

        Flux.fromIterable(checks)
                .flatMap(check -> check) // Executes all mono objects concurrently
                .collectList()
                .block(); // Get result of all the operations

        // Count health status after checks
        log.info("Updated node health status: {}/{} healthy", nodeHealthRegistry.getHealthyNodesCount(), nodes.size());
    }

    /**
     * Asynchronously checks a single node's health
     * @param node to be checked
     * @return async result of the check
     */
    private Mono<StorageNode> checkNodeHealthAsync(StorageNode node) {
        String healthUrl = String.format("http://%s:%d%s",
                node.getHostAddr(), node.getPort(), healthEndpointPath);

        return webClient.get()
                .uri(healthUrl)
                .retrieve()
                .toBodilessEntity()
                .timeout(timeout) // suppose there is an error after {timeout} seconds
                .map(response -> {
                    updateNodeStatus(node, true);
                    return node;
                })
                .onErrorResume(e -> {
                    log.debug("Health check failed for node {}:{}: {}",
                            node.getHostAddr(), node.getPort(), e.getMessage());
                    updateNodeStatus(node, false);
                    return Mono.just(node);
                });
    }

    /**
     * Updates node status based on health check result
     */
    private void updateNodeStatus(StorageNode node, boolean isHealthy) {

        if (isHealthy) {
            // Node is healthy
            if (!nodeHealthRegistry.isNodeHealthy(node.getId())) {
                log.info("Node {}:{} is now healthy (recovered)",
                        node.getHostAddr(), node.getPort());
            }
        } else {
            if (nodeHealthRegistry.isNodeHealthy(node.getId())) {
                // Mark as unhealthy after failure
                log.warn("Node {}:{} is now marked UNHEALTHY",
                        node.getHostAddr(), node.getPort());
            }  else {
                log.debug("Node {}:{} remains unhealthy",
                        node.getHostAddr(), node.getPort());
            }
        }

        nodeHealthRegistry.updateNodeHealth(node.getId(), isHealthy);
    }
}
