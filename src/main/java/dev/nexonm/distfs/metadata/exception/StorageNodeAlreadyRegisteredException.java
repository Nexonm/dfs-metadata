package dev.nexonm.distfs.metadata.exception;

public class StorageNodeAlreadyRegisteredException extends RuntimeException {
    public StorageNodeAlreadyRegisteredException(String host, int port) {
        super(String.format("The node located at 'http://%s:%d' already exists.", host, port));
    }
}
