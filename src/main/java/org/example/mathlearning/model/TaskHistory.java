package org.example.mathlearning.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_history")
public class TaskHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private MathTask task;

    @Column(name = "solved")
    private Boolean solved = false;

    @Column(name = "attempts")
    private Integer attempts = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "solved_at")
    private LocalDateTime solvedAt;

    // Конструкторы
    public TaskHistory() {}

    public TaskHistory(String sessionId, MathTask task) {
        this.sessionId = sessionId;
        this.task = task;
        this.solved = false;
        this.attempts = 0;
        this.createdAt = LocalDateTime.now();
    }

    public TaskHistory(String sessionId, User user, MathTask task) {
        this.sessionId = sessionId;
        this.user = user;
        this.task = task;
        this.solved = false;
        this.attempts = 0;
        this.createdAt = LocalDateTime.now();
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public MathTask getTask() { return task; }
    public void setTask(MathTask task) { this.task = task; }

    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    public String getTopic() { return task != null ? task.getTopic() : null; }

    public String getTaskText() { return task != null ? task.getTaskText() : ""; }

    public Integer getLevel() { return task != null ? task.getLevel() : null; }

    public Boolean getSolved() { return solved; }
    public void setSolved(Boolean solved) {
        this.solved = solved;
        if (solved) {
            this.solvedAt = LocalDateTime.now();
        }
    }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getSolvedAt() { return solvedAt; }
    public void setSolvedAt(LocalDateTime solvedAt) { this.solvedAt = solvedAt; }
}