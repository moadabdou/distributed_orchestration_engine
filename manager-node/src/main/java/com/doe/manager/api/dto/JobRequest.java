package com.doe.manager.api.dto;

public record JobRequest(String payload, Long timeoutMs, String label) {
}
