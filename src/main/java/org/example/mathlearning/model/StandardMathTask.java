package org.example.mathlearning.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "standard_math_tasks")
public class StandardMathTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false, length = 64)
    private String topic;

    @Column(name = "grade", nullable = false)
    private Integer grade;

    @Column(name = "difficulty", nullable = false)
    private Integer difficulty;

    @Column(name = "statement", nullable = false, length = 2000)
    private String statement;

    @Column(name = "correct_answer", length = 255)
    private String correctAnswer;

    @Column(name = "hint", length = 1000)
    private String hint;

    @Column(name = "is_manual", nullable = false)
    private Boolean manual = true;

    @Column(name = "is_ai_generated", nullable = false)
    private Boolean aiGenerated = false;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public StandardMathTask() {
    }

    public StandardMathTask(String topic,
                            Integer grade,
                            Integer difficulty,
                            String statement,
                            String correctAnswer,
                            String hint,
                            Boolean manual,
                            Boolean aiGenerated,
                            Boolean active) {
        this.topic = topic;
        this.grade = grade;
        this.difficulty = difficulty;
        this.statement = statement;
        this.correctAnswer = correctAnswer;
        this.hint = hint;
        this.manual = manual;
        this.aiGenerated = aiGenerated;
        this.active = active;
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

    public Integer getGrade() {
        return grade;
    }

    public void setGrade(Integer grade) {
        this.grade = grade;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public Boolean getManual() {
        return manual;
    }

    public void setManual(Boolean manual) {
        this.manual = manual;
    }

    public Boolean getAiGenerated() {
        return aiGenerated;
    }

    public void setAiGenerated(Boolean aiGenerated) {
        this.aiGenerated = aiGenerated;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
