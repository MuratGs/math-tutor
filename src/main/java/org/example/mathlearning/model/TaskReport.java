package org.example.mathlearning.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_reports")
public class TaskReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_task_id")
    private StandardMathTask standardTask;

    @Column(name = "task_text", nullable = false, length = 2000)
    private String taskText;

    @Column(name = "user_answer", length = 1000)
    private String userAnswer;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "report_type", nullable = false, length = 32)
    private String reportType;

    @Column(name = "report_text", nullable = false, length = 2000)
    private String reportText;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "NEW";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    public TaskReport() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public StandardMathTask getStandardTask() {
        return standardTask;
    }

    public void setStandardTask(StandardMathTask standardTask) {
        this.standardTask = standardTask;
    }

    public String getTaskText() {
        return taskText;
    }

    public void setTaskText(String taskText) {
        this.taskText = taskText;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getReportText() {
        return reportText;
    }

    public void setReportText(String reportText) {
        this.reportText = reportText;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }
}
