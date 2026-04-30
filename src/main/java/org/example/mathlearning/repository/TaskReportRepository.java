package org.example.mathlearning.repository;

import org.example.mathlearning.model.TaskReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskReportRepository extends JpaRepository<TaskReport, Long> {

    List<TaskReport> findTop50ByOrderByCreatedAtDesc();

    List<TaskReport> findTop50ByStatusOrderByCreatedAtDesc(String status);
}
