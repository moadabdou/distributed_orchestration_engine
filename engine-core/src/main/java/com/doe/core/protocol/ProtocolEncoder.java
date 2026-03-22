package com.doe.core.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Encodes {@link Message} objects into the binary wire format.
 * <p>
 * Wire format: {@code [1B Type][4B Payload-Length (big-endian)][NB Payload]}
 * <p>
 * This class is stateless and thread-safe.
 */
public final class ProtocolEncoder {

    /** Maximum allowed payload size: 10 MB. */
    public static final int MAX_PAYLOAD_SIZE = 10 * 1024 * 1024;

    private ProtocolEncoder() {
        // utility class
    }

    /**
     * Encodes a message type and raw payload into a wire-format byte array.
     *
     * @param type    the message type
     * @param payload the raw payload bytes (may be empty or {@code null})
     * @return the encoded byte array: {@code [1B type][4B length][NB payload]}
     * @throws IllegalArgumentException if the payload exceeds {@link #MAX_PAYLOAD_SIZE}
     */
    public static byte[] encode(MessageType type, byte[] payload) {
        if (type == null) {
            throw new IllegalArgumentException("Message type must not be null");
        }
        byte[] safePayload = (payload == null) ? new byte[0] : payload;

        if (safePayload.length > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "Payload size %d exceeds maximum allowed %d bytes"
                            .formatted(safePayload.length, MAX_PAYLOAD_SIZE));
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1 + 4 + safePayload.length);
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(type.getCode());
            dos.writeInt(safePayload.length);
            dos.write(safePayload);
            dos.flush();

            return baos.toByteArray();
        } catch (IOException e) {
            // Should never happen with ByteArrayOutputStream
            throw new RuntimeException("Unexpected I/O error during encoding", e);
        }
    }

    /**
     * Convenience overload: encodes a message type with a UTF-8 JSON string payload.
     *
     * @param type        the message type
     * @param jsonPayload the JSON string payload
     * @return the encoded byte array
     */
    public static byte[] encode(MessageType type, String jsonPayload) {
        byte[] payloadBytes = (jsonPayload == null)
                ? new byte[0]
                : jsonPayload.getBytes(StandardCharsets.UTF_8);
        return encode(type, payloadBytes);
    }

    /**
     * Encodes a {@link Message} object into a wire-format byte array.
     *
     * @param message the message to encode
     * @return the encoded byte array
     */
    public static byte[] encode(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
        return encode(message.type(), message.payload());
    }
}
