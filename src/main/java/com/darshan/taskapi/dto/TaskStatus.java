package com.darshan.taskapi.dto;

/**
 * Standalone status enum for API layer.
 * Decoupled from Task entity — changes to the entity enum won't silently break the API contract.
 */
public enum TaskStatus {
    TODO, IN_PROGRESS, DONE
}
