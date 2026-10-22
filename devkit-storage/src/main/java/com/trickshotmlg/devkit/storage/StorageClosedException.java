package com.trickshotmlg.devkit.storage;

/** Thrown when an operation is attempted after its storage has closed. */
public final class StorageClosedException extends StorageException {
    public StorageClosedException() { super("Storage is closed"); }
}
