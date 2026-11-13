package com.chaos.storage.local;

import com.chaos.storage.FileObject;
import com.chaos.storage.ObjectStorageArguments;
import com.chaos.storage.ObjectStorageClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Explicit local-volume adapter. Each bucket is one directory beneath a trusted root.
 * The host must prevent untrusted processes from modifying this volume concurrently.
 */
public final class LocalObjectStorageClient implements ObjectStorageClient {
    private final LocalObjectStorage objects;

    /**
     * Creates a local adapter without publishing a file URL or registering an HTTP endpoint.
     * @param root trusted local volume, which may itself be a host-managed symbolic link
     */
    public LocalObjectStorageClient(Path root) {
        objects = new LocalObjectStorage(root, Pattern.compile("[^/]+/.+"));
    }

    @Override
    public FileObject put(
            String bucket, String objectKey, InputStream input, long size, String contentType) {
        try {
            var stored = objects.write(key(bucket, objectKey), input);
            return new FileObject(bucket, objectKey, contentType, stored.size(), Instant.now());
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot write local object", ex);
        }
    }

    @Override
    public InputStream get(String bucket, String objectKey) {
        try {
            return objects.open(key(bucket, objectKey));
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot open local object", ex);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            objects.delete(key(bucket, objectKey));
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot delete local object", ex);
        }
    }

    /**
     * Local volumes have no public URL. Serve downloads through an authorized application endpoint.
     * @param bucket bucket name
     * @param objectKey relative object key
     * @param ttl requested URL lifetime
     * @return never returns a URL
     * @throws UnsupportedOperationException always, because local signing is unavailable
     */
    @Override
    public URL presignedGetUrl(String bucket, String objectKey, Duration ttl) {
        key(bucket, objectKey);
        ObjectStorageArguments.requirePresignedTtl(ttl);
        throw new UnsupportedOperationException(
                "Local storage requires an authorized download endpoint");
    }

    private static String key(String bucket, String objectKey) {
        ObjectStorageArguments.requireBucket(bucket);
        if (!bucket.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Invalid local bucket");
        }
        return bucket + "/" + ObjectStorageArguments.requireObjectKey(objectKey);
    }
}
