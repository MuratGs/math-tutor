package org.example.mathlearning.repository;

import org.example.mathlearning.model.TopicNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicNodeRepository extends JpaRepository<TopicNode, Long> {

    Optional<TopicNode> findByTopicKey(String topicKey);

    List<TopicNode> findByDisciplineOrderByDisplayNameAsc(String discipline);

    List<TopicNode> findByDisciplineAndStubFalseAndActiveTrueOrderByDisplayNameAsc(String discipline);

    List<TopicNode> findByStubFalseAndActiveTrueOrderByDisplayNameAsc();

    @Query("SELECT DISTINCT t.discipline FROM TopicNode t WHERE t.discipline IS NOT NULL ORDER BY t.discipline")
    List<String> findDistinctDisciplines();
}
