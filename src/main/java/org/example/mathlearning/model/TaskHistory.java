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

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "task_text", nullable = false, length = 1000)
    private String taskText;

    @Column(name = "level", nullable = false)
    private Integer level;

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

    public TaskHistory(String sessionId, String topic, String taskText, Integer level) {
        this.sessionId = sessionId;
        this.topic = topic;
        this.taskText = taskText;
        this.level = level;
        this.solved = false;
        this.attempts = 1;
        this.createdAt = LocalDateTime.now();
    }

    public TaskHistory(String sessionId, User user, String topic, String taskText, Integer level) {
        this.sessionId = sessionId;
        this.user = user;
        this.topic = topic;
        this.taskText = taskText;
        this.level = level;
        this.solved = false;
        this.attempts = 1;
        this.createdAt = LocalDateTime.now();
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getTaskText() { return taskText; }
    public void setTaskText(String taskText) { this.taskText = taskText; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

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