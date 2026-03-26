package com.darshan.taskapi.controller;

import com.darshan.taskapi.dto.request.TaskRequest;
import com.darshan.taskapi.dto.response.AppResponse;
import com.darshan.taskapi.dto.response.PagedResponse;
import com.darshan.taskapi.dto.response.TaskResponse;
import com.darshan.taskapi.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Create and manage your personal tasks. All endpoints require a valid JWT token.")
@SecurityRequirement(name = "Bearer Authentication")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(
        tags = {"Tasks"},
        summary = "Get all my tasks",
        description = "Returns paginated tasks for the logged-in user. Supports filtering by status and searching by title keyword. Sorted newest first."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tasks retrieved — data field contains PagedResponse<TaskResponse>",
            content = @Content(schema = @Schema(implementation = PagedResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid status or page value"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token")
    })
    public ResponseEntity<AppResponse> getAllTasks(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Filter by status: TODO, IN_PROGRESS, DONE (case-insensitive)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Search tasks by title keyword (case-insensitive)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-based, default 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (default 10, max 50)")
            @RequestParam(defaultValue = "10") int size) {

        if (size > 50) size = 50;

        PagedResponse<TaskResponse> result = taskService.getAllTasks(
                userDetails.getUsername(), status, search, page, size);
        return ResponseEntity.ok(AppResponse.success("Tasks retrieved", result));
    }

    @PostMapping
    @Operation(
        tags = {"Tasks"},
        summary = "Create a task",
        description = "Creates a new task. Title is required. Status defaults to TODO. Due date format: yyyy-MM-dd (must be a future date)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Task created",
            content = @Content(schema = @Schema(implementation = AppResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed — title required or due date in the past"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token")
    })
    public ResponseEntity<AppResponse> createTask(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TaskRequest request) {
        TaskResponse task = taskService.createTask(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppResponse.success("Task created", task));
    }

    @GetMapping("/{id}")
    @Operation(
        tags = {"Tasks"},
        summary = "Get a task by ID",
        description = "Returns a single task by ID. Returns 401 if the task belongs to a different user."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Task retrieved",
            content = @Content(schema = @Schema(implementation = AppResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid ID format"),
        @ApiResponse(responseCode = "401", description = "Not your task"),
        @ApiResponse(responseCode = "404", description = "Task not found")
    })
    public ResponseEntity<AppResponse> getTask(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Task ID (numeric)") @PathVariable Long id) {
        TaskResponse task = taskService.getTask(userDetails.getUsername(), id);
        return ResponseEntity.ok(AppResponse.success("Task retrieved", task));
    }

    @PutMapping("/{id}")
    @Operation(
        tags = {"Tasks"},
        summary = "Update a task",
        description = "Updates title, description, status, and/or due date. Only the owner can update."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Task updated",
            content = @Content(schema = @Schema(implementation = AppResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Not your task"),
        @ApiResponse(responseCode = "404", description = "Task not found")
    })
    public ResponseEntity<AppResponse> updateTask(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Task ID (numeric)") @PathVariable Long id,
            @Valid @RequestBody TaskRequest request) {
        TaskResponse task = taskService.updateTask(userDetails.getUsername(), id, request);
        return ResponseEntity.ok(AppResponse.success("Task updated", task));
    }

    @DeleteMapping("/{id}")
    @Operation(
        tags = {"Tasks"},
        summary = "Delete a task",
        description = "Permanently deletes a task. Only the owner can delete."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Task deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid ID format"),
        @ApiResponse(responseCode = "401", description = "Not your task"),
        @ApiResponse(responseCode = "404", description = "Task not found")
    })
    public ResponseEntity<Void> deleteTask(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Task ID (numeric)") @PathVariable Long id) {
        taskService.deleteTask(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

}
