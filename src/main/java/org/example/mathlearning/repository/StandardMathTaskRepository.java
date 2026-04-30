package org.example.mathlearning.repository;

import org.example.mathlearning.model.StandardMathTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface StandardMathTaskRepository extends JpaRepository<StandardMathTask, Long> {

    List<StandardMathTask> findByActiveTrueAndTopicInAndGradeAndDifficulty(Collection<String> topics, Integer grade, Integer difficulty);

    List<StandardMathTask> findByActiveTrueAndTopicAndGradeAndDifficulty(String topic, Integer grade, Integer difficulty);
}
