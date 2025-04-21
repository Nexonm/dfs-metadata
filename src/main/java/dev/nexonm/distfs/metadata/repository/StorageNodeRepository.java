package dev.nexonm.distfs.metadata.repository;

import dev.nexonm.distfs.metadata.entity.StorageNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StorageNodeRepository extends JpaRepository<StorageNode, UUID> {

    Optional<StorageNode> findByHostAddrAndPort(String hostAddr, Integer port);
    boolean existsByHostAddrAndPort(String hostAddr, Integer port);

    @Query(value = "SELECT n.* FROM storage_nodes n " +
            "WHERE n.node_id NOT IN " +
            "(SELECT m.node_id FROM chunk_node_mapping m WHERE m.chunk_id = :chunkId)",
            nativeQuery = true)
    List<StorageNode> findNodesNotContainingChunk(@Param("chunkId") UUID chunkId);

    @Query("SELECT DISTINCT n FROM StorageNode n " +
            "JOIN n.chunks c WHERE c.file.id = :fileId")
    List<StorageNode> findNodesContainingFileChunks(@Param("fileId") UUID fileId);
}

