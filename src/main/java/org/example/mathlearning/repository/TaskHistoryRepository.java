package org.example.mathlearning.repository;

import org.example.mathlearning.model.TaskHistory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {

    // ============== ПО СЕССИИ ==============

    List<TaskHistory> findBySessionIdAndTopicAndSolvedTrue(String sessionId, String topic);
    List<TaskHistory> findBySessionIdAndTopic(String sessionId, String topic);

    @Query("SELECT t FROM TaskHistory t WHERE t.sessionId = :sessionId AND t.taskText = :taskText ORDER BY t.id DESC LIMIT 1")
    Optional<TaskHistory> findFirstBySessionIdAndTaskText(@Param("sessionId") String sessionId, @Param("taskText") String taskText);

    Optional<TaskHistory> findBySessionIdAndTaskText(String sessionId, String taskText);

    @Query("SELECT COUNT(t) > 0 FROM TaskHistory t WHERE t.sessionId = :sessionId AND t.taskText = :taskText AND t.solved = true")
    boolean isTaskSolved(@Param("sessionId") String sessionId, @Param("taskText") String taskText);

    @Query("SELECT t.taskText FROM TaskHistory t WHERE t.sessionId = :sessionId AND t.topic = :topic")
    List<String> findAllUsedTasks(@Param("sessionId") String sessionId, @Param("topic") String topic);

    @Query("SELECT COUNT(t) FROM TaskHistory t WHERE t.sessionId = :sessionId AND t.level = :level AND t.solved = true")
    int countSolvedByLevel(@Param("sessionId") String sessionId, @Param("level") int level);

    @Modifying
    @Transactional
    @Query("UPDATE TaskHistory t SET t.attempts = t.attempts + 1 WHERE t.sessionId = :sessionId AND t.taskText = :taskText")
    void incrementAttempts(@Param("sessionId") String sessionId, @Param("taskText") String taskText);

    @Modifying
    @Transactional
    @Query("DELETE FROM TaskHistory t WHERE t.sessionId = :sessionId AND t.topic = :topic AND t.level = :level")
    void deleteBySessionIdAndTopicAndLevel(@Param("sessionId") String sessionId, @Param("topic") String topic, @Param("level") int level);

    @Modifying
    @Transactional
    @Query("DELETE FROM TaskHistory t WHERE t.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(t) FROM TaskHistory t WHERE t.sessionId = :sessionId AND t.topic = :topic AND t.solved = false")
    int countUnsolvedBySessionIdAndTopic(@Param("sessionId") String sessionId, @Param("topic") String topic);

    List<TaskHistory> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    @Query("SELECT t FROM TaskHistory t WHERE t.sessionId = :sessionId ORDER BY t.createdAt DESC LIMIT 1")
    Optional<TaskHistory> findLastBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(t) > 0 FROM TaskHistory t WHERE t.sessionId = :sessionId AND t.taskText = :taskText")
    boolean existsBySessionIdAndTaskText(@Param("sessionId") String sessionId, @Param("taskText") String taskText);

    @Query("SELECT DISTINCT t.taskText FROM TaskHistory t WHERE t.sessionId = :sessionId")
    List<String> findDistinctTasksBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT t.level, COUNT(t), SUM(CASE WHEN t.solved THEN 1 ELSE 0 END) " +
            "FROM TaskHistory t WHERE t.sessionId = :sessionId GROUP BY t.level ORDER BY t.level")
    List<Object[]> getLevelStats(@Param("sessionId") String sessionId);

    // ============== НОВЫЕ МЕТОДЫ ПО ID ПОЛЬЗОВАТЕЛЯ ==============

    @Query("SELECT t FROM TaskHistory t WHERE t.user.id = :userId")
    List<TaskHistory> findByUserId(@Param("userId") Long userId);

    @Query("SELECT t FROM TaskHistory t WHERE t.user.id = :userId AND t.topic = :topic AND t.solved = true")
    List<TaskHistory> findByUserIdAndTopicAndSolvedTrue(@Param("userId") Long userId, @Param("topic") String topic);

    @Query("SELECT t FROM TaskHistory t WHERE t.user.id = :userId AND t.topic = :topic")
    List<TaskHistory> findByUserIdAndTopic(@Param("userId") Long userId, @Param("topic") String topic);

    @Query("SELECT t FROM TaskHistory t WHERE t.user.id = :userId ORDER BY t.createdAt DESC")
    List<TaskHistory> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    default List<TaskHistory> findRecentByUserId(Long userId, int limit) {
        return findRecentByUserId(userId, PageRequest.of(0, limit));
    }

    @Query("SELECT COUNT(t) FROM TaskHistory t WHERE t.user.id = :userId AND t.solved = true")
    int countSolvedByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM TaskHistory t WHERE t.user.id = :userId")
    int countTotalByUserId(@Param("userId") Long userId);

    @Query("SELECT t.level, COUNT(t), SUM(CASE WHEN t.solved THEN 1 ELSE 0 END) " +
            "FROM TaskHistory t WHERE t.user.id = :userId GROUP BY t.level ORDER BY t.level")
    List<Object[]> getLevelStatsByUserId(@Param("userId") Long userId);

    @Query("SELECT AVG(t.attempts) FROM TaskHistory t WHERE t.user.id = :userId AND t.solved = true")
    Double getAverageAttemptsByUserId(@Param("userId") Long userId);

    @Query("SELECT t FROM TaskHistory t WHERE t.user.id = :userId AND t.solved = false ORDER BY t.createdAt DESC")
    List<TaskHistory> findUnsolvedByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE TaskHistory t SET t.solved = true, t.solvedAt = CURRENT_TIMESTAMP WHERE t.user.id = :userId AND t.taskText = :taskText")
    void markAsSolvedByUserId(@Param("userId") Long userId, @Param("taskText") String taskText);

    @Modifying
    @Transactional
    @Query("UPDATE TaskHistory t SET t.attempts = t.attempts + 1 WHERE t.user.id = :userId AND t.taskText = :taskText")
    void incrementAttemptsByUserId(@Param("userId") Long userId, @Param("taskText") String taskText);

    @Modifying
    @Transactional
    @Query("DELETE FROM TaskHistory t WHERE t.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TaskHistory t WHERE t.user.id = :userId AND t.taskText = :taskText AND t.solved = true")
    boolean isTaskSolvedByUser(@Param("userId") Long userId, @Param("taskText") String taskText);
}