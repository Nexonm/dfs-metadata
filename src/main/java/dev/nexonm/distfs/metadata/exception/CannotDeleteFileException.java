package dev.nexonm.distfs.metadata.exception;

public class CannotDeleteFileException extends RuntimeException {
    public CannotDeleteFileException(String message) {
        super(message);
    }
}
