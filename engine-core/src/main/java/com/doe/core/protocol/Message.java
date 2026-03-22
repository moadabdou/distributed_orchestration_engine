package com.doe.core.protocol;

import java.util.Arrays;

/**
 * Immutable representation of a protocol message.
 * <p>
 * Wire format: {@code [1B Type][4B Length][NB Payload]}
 *
 * @param type    the message type
 * @param payload the raw payload bytes (may be empty, never {@code null})
 */
public record Message(MessageType type, byte[] payload) {

    /**
     * Compact constructor — defensively copies the payload and rejects {@code null}.
     */
    public Message {
        if (type == null) {
            throw new IllegalArgumentException("Message type must not be null");
        }
        payload = (payload == null) ? new byte[0] : payload.clone();
    }

    /**
     * Returns a defensive copy of the payload.
     */
    @Override
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * Convenience: interpret the payload as a UTF-8 string.
     */
    public String payloadAsString() {
        return new String(payload, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message other)) return false;
        return type == other.type && Arrays.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        // 31 is a common prime multiplier for hash codes; we combine the type's hash and the payload's hash
        return 31 * type.hashCode() + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        return "Message[type=%s, payloadLength=%d]".formatted(type, payload.length);
    }
}
