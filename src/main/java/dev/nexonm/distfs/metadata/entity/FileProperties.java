package dev.nexonm.distfs.metadata.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Entity
@Table(name = "file_properties")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "chunks")
@ToString(exclude = "chunks")
public class FileProperties {

    @Id
    @Column(name = "file_id")
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(name = "total_chunks", nullable = false)
    private Integer totalChunks;

    @Column(name = "total_size", nullable = false)
    private Long totalSize;

    @Column(nullable = false)
    private String hash;

    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ChunkProperties> chunks = new ArrayList<>();

    // Helper method to add chunks
    public void addChunk(ChunkProperties chunk) {
        chunks.add(chunk);
        chunk.setFile(this);
    }

    // Helper method to remove chunks
    public void removeChunk(ChunkProperties chunk) {
        chunks.remove(chunk);
        chunk.setFile(null);
    }
}