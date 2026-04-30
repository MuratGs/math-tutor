package org.example.mathlearning.repository;

import org.example.mathlearning.model.TopicRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicGraphRepository extends JpaRepository<TopicRelation, Long> {

    List<TopicRelation> findByTopicOrderByRelationStrengthDesc(String topic);

    Optional<TopicRelation> findByTopicAndRelatedTopic(String topic, String relatedTopic);

    @Modifying
    @Transactional
    @Query("DELETE FROM TopicRelation r WHERE r.topic = :topic AND r.relatedTopic = :relatedTopic")
    int deleteByTopicAndRelatedTopic(@Param("topic") String topic, @Param("relatedTopic") String relatedTopic);

    @Modifying
    @Transactional
    @Query("DELETE FROM TopicRelation r WHERE r.topic = :topic")
    int deleteByTopic(@Param("topic") String topic);
}
