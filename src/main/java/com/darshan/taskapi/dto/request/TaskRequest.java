package com.darshan.taskapi.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TaskRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String status;

    @Future(message = "Due date must be a future date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;
}
