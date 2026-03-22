package com.doe.core.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the binary protocol codec: {@link ProtocolEncoder} and {@link ProtocolDecoder}.
 */
class ProtocolCodecTest {

    // ──── Round-trip tests ──────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(MessageType.class)
    @DisplayName("Round-trip: encode → decode produces identical Message for every MessageType")
    void roundTrip_allMessageTypes(MessageType type) throws IOException {
        String json = """
                {"workerId":"%s","hostname":"node-1"}""".formatted(UUID.randomUUID());
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);

        byte[] wire = ProtocolEncoder.encode(type, payload);
        Message decoded = ProtocolDecoder.decode(new ByteArrayInputStream(wire));

        assertEquals(type, decoded.type());
        assertArrayEquals(payload, decoded.payload());
    }

    @Test
    @DisplayName("Round-trip via Message overload")
    void roundTrip_viaMessageObject() throws IOException {
        Message original = new Message(
                MessageType.ASSIGN_JOB,
                """
                {"jobId":"abc-123","payload":{"cmd":"echo hello"}}"""
                        .getBytes(StandardCharsets.UTF_8));

        byte[] wire = ProtocolEncoder.encode(original);
        Message decoded = ProtocolDecoder.decode(new ByteArrayInputStream(wire));

        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("Round-trip via String convenience overload")
    void roundTrip_stringPayload() throws IOException {
        String json = """
                {"workerId":"w-1","timestamp":1700000000}""";

        byte[] wire = ProtocolEncoder.encode(MessageType.HEARTBEAT, json);
        Message decoded = ProtocolDecoder.decode(new ByteArrayInputStream(wire));

        assertEquals(MessageType.HEARTBEAT, decoded.type());
        assertEquals(json, decoded.payloadAsString());
    }

    // ──── Empty payload ────────────────────────────────────────────

    @Test
    @DisplayName("Empty payload (N = 0) encodes and decodes correctly")
    void emptyPayload() throws IOException {
        byte[] wire = ProtocolEncoder.encode(MessageType.HEARTBEAT, new byte[0]);

        // Wire should be exactly 5 bytes: 1 type + 4 length
        assertEquals(5, wire.length);

        Message decoded = ProtocolDecoder.decode(new ByteArrayInputStream(wire));
        assertEquals(MessageType.HEARTBEAT, decoded.type());
        assertEquals(0, decoded.payload().length);
    }

    @Test
    @DisplayName("Null payload treated as empty")
    void nullPayload() throws IOException {
        byte[] wire = ProtocolEncoder.encode(MessageType.REGISTER_WORKER, (byte[]) null);
        Message decoded = ProtocolDecoder.decode(new ByteArrayInputStream(wire));

        assertEquals(0, decoded.payload().length);
    }

    // ──── Max payload guard ────────────────────────────────────────

    @Test
    @DisplayName("Encoder rejects payload exceeding 10 MB")
    void encoder_rejectsOversizedPayload() {
        byte[] oversized = new byte[ProtocolEncoder.MAX_PAYLOAD_SIZE + 1];

        assertThrows(IllegalArgumentException.class,
                () -> ProtocolEncoder.encode(MessageType.JOB_RESULT, oversized));
    }

    @Test
    @DisplayName("Decoder rejects declared payload length exceeding 10 MB")
    void decoder_rejectsOversizedDeclaredLength() {
        // Hand-craft a wire frame with length = MAX + 1
        byte[] wire = new byte[5];
        wire[0] = MessageType.JOB_RESULT.getCode();
        int badLength = ProtocolDecoder.MAX_PAYLOAD_SIZE + 1;
        wire[1] = (byte) (badLength >>> 24);
        wire[2] = (byte) (badLength >>> 16);
        wire[3] = (byte) (badLength >>> 8);
        wire[4] = (byte) badLength;

        assertThrows(IllegalArgumentException.class,
                () -> ProtocolDecoder.decode(new ByteArrayInputStream(wire)));
    }

    // ──── MessageType lookup ───────────────────────────────────────

    @Test
    @DisplayName("MessageType.fromCode resolves all known codes")
    void messageType_fromCode_ok() {
        assertEquals(MessageType.REGISTER_WORKER, MessageType.fromCode((byte) 0x01));
        assertEquals(MessageType.HEARTBEAT, MessageType.fromCode((byte) 0x02));
        assertEquals(MessageType.ASSIGN_JOB, MessageType.fromCode((byte) 0x03));
        assertEquals(MessageType.JOB_RESULT, MessageType.fromCode((byte) 0x04));
    }

    @Test
    @DisplayName("MessageType.fromCode throws on unknown code")
    void messageType_fromCode_unknown() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageType.fromCode((byte) 0xFF));
    }

    // ──── Error cases ──────────────────────────────────────────────

    @Test
    @DisplayName("Decoder throws EOFException on empty stream")
    void decoder_emptyStream() {
        assertThrows(EOFException.class,
                () -> ProtocolDecoder.decode(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    @DisplayName("Decoder throws EOFException when stream ends before payload length")
    void decoder_truncatedHeader() {
        // Only 1 byte (type), missing the 4-byte length
        byte[] partial = new byte[]{MessageType.HEARTBEAT.getCode()};
        assertThrows(EOFException.class,
                () -> ProtocolDecoder.decode(new ByteArrayInputStream(partial)));
    }

    @Test
    @DisplayName("Decoder throws EOFException when stream ends mid-payload")
    void decoder_truncatedPayload() {
        // Header says 100 bytes, but only provide 5
        byte[] wire = new byte[1 + 4 + 5];
        wire[0] = MessageType.HEARTBEAT.getCode();
        wire[1] = 0; wire[2] = 0; wire[3] = 0; wire[4] = 100; // length = 100
        // only 5 bytes of payload follow

        assertThrows(EOFException.class,
                () -> ProtocolDecoder.decode(new ByteArrayInputStream(wire)));
    }

    @Test
    @DisplayName("Encoder rejects null MessageType")
    void encoder_rejectsNullType() {
        assertThrows(IllegalArgumentException.class,
                () -> ProtocolEncoder.encode(null, new byte[0]));
    }

    @Test
    @DisplayName("Decoder rejects null InputStream")
    void decoder_rejectsNullStream() {
        assertThrows(IllegalArgumentException.class,
                () -> ProtocolDecoder.decode(null));
    }

    // ──── Wire format structure ────────────────────────────────────

    @Test
    @DisplayName("Encoded wire bytes have correct structure: [1B type][4B length][NB payload]")
    void wireFormat_structure() {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] wire = ProtocolEncoder.encode(MessageType.REGISTER_WORKER, payload);

        assertEquals(1 + 4 + payload.length, wire.length);
        assertEquals(MessageType.REGISTER_WORKER.getCode(), wire[0]);

        // Big-endian length
        int decodedLength = ((wire[1] & 0xFF) << 24)
                | ((wire[2] & 0xFF) << 16)
                | ((wire[3] & 0xFF) << 8)
                | (wire[4] & 0xFF);
        assertEquals(payload.length, decodedLength);
    }
}
