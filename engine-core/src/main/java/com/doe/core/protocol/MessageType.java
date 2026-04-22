package com.doe.core.protocol;

/**
 * Defines the message types used in the Manager ↔ Worker binary protocol.
 * <p>
 * Wire format: {@code [1B Type][4B Length][NB Payload]}
 */
public enum MessageType {

    REGISTER_WORKER((byte) 0x01),
    HEARTBEAT((byte) 0x02),
    ASSIGN_JOB((byte) 0x03),
    JOB_RESULT((byte) 0x04),
    REGISTER_ACK((byte) 0x05),
    JOB_RUNNING((byte) 0x06),
    CANCEL_JOB((byte) 0x07),
    XCOM_REQUEST((byte) 0x08),
    XCOM_RESPONSE((byte) 0x09),
    JOB_LOG((byte) 0x0A),
    REGISTER_JOB_EVENTS((byte) 0x0B),
    EVENT_REGISTER((byte) 0x0C),
    EVENT_SUBSCRIBE((byte) 0x0D),
    EVENT_PUBLISH((byte) 0x0E),
    EVENT_NOTIFY((byte) 0x0F);


    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    /**
     * Resolves a {@link MessageType} from its wire byte code.
     *
     * @param code the single-byte message type code
     * @return the matching {@link MessageType}
     * @throws IllegalArgumentException if the code is unknown
     */
    public static MessageType fromCode(byte code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type code: 0x%02X".formatted(code));
    }
}
