package dev.nexonm.distfs.metadata.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "chunk_properties")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"file", "storageNodes"})
@ToString(exclude = {"file", "storageNodes"})
public class ChunkProperties {

    @Id
    @Column(name = "chunk_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileProperties file;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "chunk_size", nullable = false)
    private Long chunkSize;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinTable(
            name = "chunk_node_mapping",
            joinColumns = @JoinColumn(name = "chunk_id"),
            inverseJoinColumns = @JoinColumn(name = "node_id")
    )
    @Builder.Default
    private Set<StorageNode> storageNodes = new HashSet<>();

    // Helper methods for bidirectional relationship management
    public void addStorageNode(StorageNode node) {
        storageNodes.add(node);
        node.getChunks().add(this);
    }

    public void removeStorageNode(StorageNode node) {
        storageNodes.remove(node);
        node.getChunks().remove(this);
    }

}
