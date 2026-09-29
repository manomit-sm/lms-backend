package com.bsolz.lms.shared.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/** @param headers headers the upload request must send as given (they are part of the signature) */
public record PresignedUpload(URI url, String method, Map<String, String> headers, Instant expiresAt) {
}
