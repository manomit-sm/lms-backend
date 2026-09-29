package com.bsolz.lms.shared.storage;

import java.net.URI;
import java.time.Instant;

public record PresignedDownload(URI url, Instant expiresAt) {
}
