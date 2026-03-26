package com.darshan.taskapi.repository;

import com.darshan.taskapi.entity.Task;
import com.darshan.taskapi.entity.Task.Status;
import com.darshan.taskapi.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // Paginated queries used by GET /api/tasks
    Page<Task> findByUser(User user, Pageable pageable);
    Page<Task> findByUserAndStatus(User user, Status status, Pageable pageable);
    Page<Task> findByUserAndTitleContainingIgnoreCase(User user, String keyword, Pageable pageable);
    Page<Task> findByUserAndStatusAndTitleContainingIgnoreCase(User user, Status status, String keyword, Pageable pageable);
}
