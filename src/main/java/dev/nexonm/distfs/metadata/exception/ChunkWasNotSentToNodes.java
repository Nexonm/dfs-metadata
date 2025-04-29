package dev.nexonm.distfs.metadata.exception;

public class ChunkWasNotSentToNodes extends RuntimeException{
    public ChunkWasNotSentToNodes(String message) {
        super(message);
    }
}
