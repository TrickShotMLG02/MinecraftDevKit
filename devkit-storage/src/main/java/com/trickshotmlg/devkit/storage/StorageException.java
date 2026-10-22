package com.trickshotmlg.devkit.storage;

/** Base unchecked exception for storage failures. Sensitive connection details are never included. */
public class StorageException extends RuntimeException {
    public StorageException(String message) { super(message); }
    public StorageException(String message, Throwable cause) { super(message, cause); }
}
