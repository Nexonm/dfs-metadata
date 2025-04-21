package dev.nexonm.distfs.metadata.exception;

public class StorageNodeException extends RuntimeException {
    public StorageNodeException(String message) {
        super(message);
    }

    public StorageNodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
