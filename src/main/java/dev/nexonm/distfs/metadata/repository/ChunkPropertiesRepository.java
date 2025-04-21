package dev.nexonm.distfs.metadata.repository;

import dev.nexonm.distfs.metadata.entity.ChunkProperties;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChunkPropertiesRepository extends JpaRepository<ChunkProperties, UUID> {

    List<ChunkProperties> findByFileIdOrderByChunkIndexAsc(UUID fileId);

    Optional<ChunkProperties> findByFileIdAndChunkIndex(UUID fileId, Integer chunkIndex);

    @Query("SELECT c FROM ChunkProperties c JOIN c.storageNodes n WHERE n.id = :nodeId")
    List<ChunkProperties> findChunksStoredOnNode(@Param("nodeId") UUID nodeId);

    @Query("SELECT c FROM ChunkProperties c " +
            "WHERE SIZE(c.storageNodes) < :replicationCount")
    List<ChunkProperties> findChunksWithReplicationCountLessThan(@Param("replicationCount") Integer replicationCount);
}

