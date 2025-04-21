package dev.nexonm.distfs.metadata.repository;

import dev.nexonm.distfs.metadata.entity.FileProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FilePropertiesRepository extends JpaRepository<FileProperties, UUID> {

    Optional<FileProperties> findByFilename(String filename);

    Page<FileProperties> findAllByOrderByTotalSizeDesc(Pageable pageable);

    List<FileProperties> findByTotalSizeBetween(Long minSize, Long maxSize);

    @Query("SELECT f FROM FileProperties f JOIN f.chunks c JOIN c.storageNodes n " +
            "WHERE n.id = :nodeId GROUP BY f.id")
    List<FileProperties> findFilesStoredOnNode(@Param("nodeId") UUID nodeId);
}

