package dev.nexonm.distfs.metadata.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
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
@Table(name = "storage_nodes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "chunks")
@ToString(exclude = "chunks")
public class StorageNode {

    @Id
    @Column(name = "node_id")
    private UUID id;

    @Column(nullable = false)
    private String hostAddr;

    @Column(nullable = false)
    private Integer port;

    @ManyToMany(mappedBy = "storageNodes", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ChunkProperties> chunks = new HashSet<>();
}

