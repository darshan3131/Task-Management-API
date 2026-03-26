package com.darshan.taskapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AppResponse {

    private boolean success;
    private String message;
    private Object data;

    public static AppResponse success(String message, Object data) {
        return new AppResponse(true, message, data);
    }

    public static AppResponse error(String message) {
        return new AppResponse(false, message, null);
    }
}
