package com.cyberstrike.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "batch_task_queues")
public class BatchQueue {

    @Id
    private String id;

    private String title;

    @Column(nullable = false)
    private String status; // pending, running, completed, cancelled

    private String role; // optional role to apply to all tasks

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "current_index")
    private int currentIndex = 0;

    @OneToMany(mappedBy = "queue", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC") // Assuming ID order roughly equals insertion order, or add explicit index field
    @JsonManagedReference
    private List<BatchTask> tasks = new ArrayList<>();

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    public List<BatchTask> getTasks() {
        return tasks;
    }

    public void setTasks(List<BatchTask> tasks) {
        this.tasks = tasks;
    }
}
