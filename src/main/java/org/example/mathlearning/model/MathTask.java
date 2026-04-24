package org.example.mathlearning.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "math_tasks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_math_tasks_topic_level_text", columnNames = {"topic", "level", "task_text"})
        }
)
public class MathTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false, length = 32)
    private String topic;

    @Column(name = "level", nullable = false)
    private Integer level;

    @Column(name = "task_text", nullable = false, length = 1000)
    private String taskText;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public MathTask() {
    }

    public MathTask(String topic, Integer level, String taskText) {
        this.topic = topic;
        this.level = level;
        this.taskText = taskText;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getTaskText() {
        return taskText;
    }

    public void setTaskText(String taskText) {
        this.taskText = taskText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
