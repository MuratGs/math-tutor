package org.example.mathlearning.repository;

import org.example.mathlearning.model.MathTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MathTaskRepository extends JpaRepository<MathTask, Long> {
    Optional<MathTask> findByTopicAndLevelAndTaskText(String topic, Integer level, String taskText);
}
