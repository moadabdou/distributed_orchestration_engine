package com.doe.core.protocol;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decodes {@link Message} objects from the binary wire format.
 * <p>
 * Wire format: {@code [1B Type][4B Payload-Length (big-endian)][NB Payload]}
 * <p>
 * This class is stateless and thread-safe.
 */
public final class ProtocolDecoder {

    /** Maximum allowed payload size: 10 MB. */
    public static final int MAX_PAYLOAD_SIZE = 10 * 1024 * 1024;

    private ProtocolDecoder() {
        // utility class
    }

    /**
     * Reads exactly one {@link Message} from the given input stream.
     * <p>
     * Blocks until the full message (header + payload) is available.
     *
     * @param inputStream the stream to read from
     * @return the decoded message
     * @throws IOException              if an I/O error occurs or the stream ends prematurely
     * @throws IllegalArgumentException if the declared payload length is negative or exceeds
     *                                  {@link #MAX_PAYLOAD_SIZE}
     */
    public static Message decode(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream must not be null");
        }

        DataInputStream dis = (inputStream instanceof DataInputStream d)
                ? d
                : new DataInputStream(inputStream);

        byte typeByte;
        try {
            typeByte = dis.readByte();
        } catch (EOFException e) {
            throw new EOFException("Stream ended before message type could be read");
        }

        MessageType type = MessageType.fromCode(typeByte);

        int payloadLength;
        try {
            payloadLength = dis.readInt();
        } catch (EOFException e) {
            throw new EOFException("Stream ended before payload length could be read");
        }

        if (payloadLength < 0) {
            throw new IllegalArgumentException(
                    "Negative payload length: %d".formatted(payloadLength));
        }
        if (payloadLength > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "Payload length %d exceeds maximum allowed %d bytes"
                            .formatted(payloadLength, MAX_PAYLOAD_SIZE));
        }
        
        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            dis.readFully(payload);
        }

        return new Message(type, payload);
    }
}
