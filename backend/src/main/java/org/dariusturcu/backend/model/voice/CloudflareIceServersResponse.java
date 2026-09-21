package org.dariusturcu.backend.model.voice;

import java.util.List;

// Cloudflare's generate-ice-servers response already shapes each entry as {urls, username,
// credential}, matching IceServer's own fields, so the wire type is reused directly instead
// of a parallel Cloudflare-specific record.
public record CloudflareIceServersResponse(List<IceServer> iceServers) {
}
