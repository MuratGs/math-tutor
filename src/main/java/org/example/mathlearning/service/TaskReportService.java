package org.example.mathlearning.service;

import org.example.mathlearning.model.StandardMathTask;
import org.example.mathlearning.model.TaskReport;
import org.example.mathlearning.model.User;
import org.example.mathlearning.repository.StandardMathTaskRepository;
import org.example.mathlearning.repository.TaskReportRepository;
import org.example.mathlearning.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TaskReportService {

    @Autowired
    private TaskReportRepository taskReportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StandardMathTaskRepository standardMathTaskRepository;

    @Transactional
    public TaskReport createReport(Long userId,
                                   Long standardTaskId,
                                   String taskText,
                                   String userAnswer,
                                   Boolean isCorrect,
                                   String reportType,
                                   String reportText) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (taskText == null || taskText.trim().isEmpty()) {
            throw new IllegalArgumentException("taskText is required");
        }
        if (reportType == null || reportType.trim().isEmpty()) {
            throw new IllegalArgumentException("reportType is required");
        }
        if (reportText == null || reportText.trim().isEmpty()) {
            throw new IllegalArgumentException("reportText is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        StandardMathTask standardTask = null;
        if (standardTaskId != null) {
            standardTask = standardMathTaskRepository.findById(standardTaskId).orElse(null);
        }

        TaskReport r = new TaskReport();
        r.setUser(user);
        r.setStandardTask(standardTask);
        r.setTaskText(taskText.trim());
        r.setUserAnswer(userAnswer);
        r.setCorrect(isCorrect);
        r.setReportType(reportType.trim());
        r.setReportText(reportText.trim());
        r.setStatus("NEW");
        r.setCreatedAt(LocalDateTime.now());

        return taskReportRepository.save(r);
    }

    @Transactional
    public void resolveReport(Long reportId, String adminNote) {
        TaskReport r = taskReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));

        r.setStatus("RESOLVED");
        r.setResolvedAt(LocalDateTime.now());
        r.setAdminNote(adminNote);
        taskReportRepository.save(r);
    }
}
