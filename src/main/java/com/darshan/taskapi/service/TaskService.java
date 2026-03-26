package com.darshan.taskapi.service;

import com.darshan.taskapi.dto.TaskStatus;
import com.darshan.taskapi.dto.request.TaskRequest;
import com.darshan.taskapi.dto.response.PagedResponse;
import com.darshan.taskapi.dto.response.TaskResponse;
import com.darshan.taskapi.entity.Task;
import com.darshan.taskapi.entity.Task.Status;
import com.darshan.taskapi.entity.User;
import com.darshan.taskapi.exception.ResourceNotFoundException;
import com.darshan.taskapi.exception.UnauthorizedException;
import com.darshan.taskapi.repository.TaskRepository;
import com.darshan.taskapi.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    // Maps entity Status to DTO TaskStatus — decoupled from entity
    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                TaskStatus.valueOf(task.getStatus().name()),
                task.getDueDate(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> getAllTasks(String email, String status, String search, int page, int size) {
        User user = getUser(email);

        boolean hasStatus = status != null && !status.isBlank();
        boolean hasSearch = search != null && !search.isBlank();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Search input intentionally excluded from logs to avoid logging user-provided PII/data
        log.info("Fetching tasks for user: {} | status={} | page={} | size={}", email, status, page, size);

        Page<Task> taskPage;
        if (hasStatus && hasSearch) {
            taskPage = taskRepository.findByUserAndStatusAndTitleContainingIgnoreCase(
                    user, Status.valueOf(status.toUpperCase()), search, pageable);
        } else if (hasStatus) {
            taskPage = taskRepository.findByUserAndStatus(user, Status.valueOf(status.toUpperCase()), pageable);
        } else if (hasSearch) {
            taskPage = taskRepository.findByUserAndTitleContainingIgnoreCase(user, search, pageable);
        } else {
            taskPage = taskRepository.findByUser(user, pageable);
        }

        log.info("Found {} tasks (page {}/{}) for user: {}", taskPage.getNumberOfElements(), page + 1, taskPage.getTotalPages(), email);
        return new PagedResponse<>(taskPage.map(this::toResponse));
    }

    @Transactional
    public TaskResponse createTask(String email, TaskRequest request) {
        log.info("Creating task for user: {} | title: {}", email, request.getTitle());
        User user = getUser(email);
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setDueDate(request.getDueDate());
        task.setUser(user);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            task.setStatus(Status.valueOf(request.getStatus().toUpperCase()));
        }
        TaskResponse response = toResponse(taskRepository.save(task));
        log.info("Task created with id: {} for user: {}", response.getId(), email);
        return response;
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(String email, Long id) {
        log.info("Fetching task id: {} for user: {}", id, email);
        User user = getUser(email);
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        if (!task.getUser().getId().equals(user.getId())) {
            log.warn("Access denied: user {} tried to access task id: {}", email, id);
            throw new UnauthorizedException("Access denied");
        }
        return toResponse(task);
    }

    @Transactional
    public TaskResponse updateTask(String email, Long id, TaskRequest request) {
        log.info("Updating task id: {} for user: {}", id, email);
        User user = getUser(email);
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        if (!task.getUser().getId().equals(user.getId())) {
            log.warn("Access denied: user {} tried to update task id: {}", email, id);
            throw new UnauthorizedException("Access denied");
        }
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setDueDate(request.getDueDate());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            task.setStatus(Status.valueOf(request.getStatus().toUpperCase()));
        }
        log.info("Task id: {} updated successfully", id);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(String email, Long id) {
        log.info("Deleting task id: {} for user: {}", id, email);
        User user = getUser(email);
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        if (!task.getUser().getId().equals(user.getId())) {
            log.warn("Access denied: user {} tried to delete task id: {}", email, id);
            throw new UnauthorizedException("Access denied");
        }
        taskRepository.delete(task);
        log.info("Task id: {} deleted successfully", id);
    }
}
