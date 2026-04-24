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

    List<TaskHistory> findBySessionIdAndTask_TopicAndSolvedTrue(String sessionId, String topic);
    List<TaskHistory> findBySessionIdAndTask_Topic(String sessionId, String topic);

    Optional<TaskHistory> findTopBySessionIdAndTask_TaskTextOrderByIdDesc(String sessionId, String taskText);

    boolean existsBySessionIdAndTask_TaskTextAndSolvedTrue(String sessionId, String taskText);

    @Query("SELECT t.task.taskText FROM TaskHistory t WHERE t.sessionId = :sessionId AND t.task.topic = :topic")
    List<String> findAllUsedTasks(@Param("sessionId") String sessionId, @Param("topic") String topic);

    @Query("SELECT COUNT(t) FROM TaskHistory t WHERE t.sessionId = :sessionId AND t.task.level = :level AND t.solved = true")
    int countSolvedByLevel(@Param("sessionId") String sessionId, @Param("level") int level);

    @Modifying
    @Transactional
    @Query("DELETE FROM TaskHistory t WHERE t.sessionId = :sessionId AND t.task.topic = :topic AND t.task.level = :level")
    void deleteBySessionIdAndTopicAndLevel(@Param("sessionId") String sessionId, @Param("topic") String topic, @Param("level") int level);

    @Modifying
    @Transactional
    @Query("DELETE FROM TaskHistory t WHERE t.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(t) FROM TaskHistory t WHERE t.sessionId = :sessionId AND t.task.topic = :topic AND t.solved = false")
    int countUnsolvedBySessionIdAndTopic(@Param("sessionId") String sessionId, @Param("topic") String topic);

    List<TaskHistory> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    Optional<TaskHistory> findTopBySessionIdOrderByCreatedAtDesc(String sessionId);

    boolean existsBySessionIdAndTask_TaskText(String sessionId, String taskText);

    @Query("SELECT DISTINCT t.task.taskText FROM TaskHistory t WHERE t.sessionId = :sessionId")
    List<String> findDistinctTasksBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT t.task.level, COUNT(t), SUM(CASE WHEN t.solved THEN 1 ELSE 0 END) " +
            "FROM TaskHistory t WHERE t.sessionId = :sessionId GROUP BY t.task.level ORDER BY t.task.level")
    List<Object[]> getLevelStats(@Param("sessionId") String sessionId);

    // ============== НОВЫЕ МЕТОДЫ ПО ID ПОЛЬЗОВАТЕЛЯ ==============

    List<TaskHistory> findByUser_Id(Long userId);

    List<TaskHistory> findByUser_IdAndTask_TopicAndSolvedTrue(Long userId, String topic);

    List<TaskHistory> findByUser_IdAndTask_Topic(Long userId, String topic);

    @Query("SELECT t FROM TaskHistory t WHERE t.user.id = :userId ORDER BY t.createdAt DESC")
    List<TaskHistory> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    default List<TaskHistory> findRecentByUserId(Long userId, int limit) {
        return findRecentByUserId(userId, PageRequest.of(0, limit));
    }

    @Query("SELECT COUNT(t) FROM TaskHistory t WHERE t.user.id = :userId AND t.solved = true")
    int countSolvedByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM TaskHistory t WHERE t.user.id = :userId")
    int countTotalByUserId(@Param("userId") Long userId);

    @Query("SELECT t.task.level, COUNT(t), SUM(CASE WHEN t.solved THEN 1 ELSE 0 END) " +
            "FROM TaskHistory t WHERE t.user.id = :userId GROUP BY t.task.level ORDER BY t.task.level")
    List<Object[]> getLevelStatsByUserId(@Param("userId") Long userId);

    @Query("SELECT AVG(t.attempts) FROM TaskHistory t WHERE t.user.id = :userId AND t.solved = true")
    Double getAverageAttemptsByUserId(@Param("userId") Long userId);

    @Query("SELECT t FROM TaskHistory t WHERE t.user.id = :userId AND t.solved = false ORDER BY t.createdAt DESC")
    List<TaskHistory> findUnsolvedByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TaskHistory t WHERE t.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TaskHistory t WHERE t.user.id = :userId AND t.task.taskText = :taskText AND t.solved = true")
    boolean isTaskSolvedByUser(@Param("userId") Long userId, @Param("taskText") String taskText);

    boolean existsByUser_IdAndTask_IdAndSolvedTrue(Long userId, Long taskId);

    @Query("SELECT t.task.taskText FROM TaskHistory t WHERE t.user.id = :userId AND t.task.topic = :topic AND t.task.level = :level AND t.solved = true ORDER BY t.id DESC")
    List<String> findSolvedTaskTextsByUserIdAndTopicAndLevel(@Param("userId") Long userId,
                                                            @Param("topic") String topic,
                                                            @Param("level") int level,
                                                            Pageable pageable);

    default List<String> findSolvedTaskTextsByUserIdAndTopicAndLevel(Long userId, String topic, int level, int limit) {
        return findSolvedTaskTextsByUserIdAndTopicAndLevel(userId, topic, level, PageRequest.of(0, limit));
    }

    Optional<TaskHistory> findTopByUser_IdAndTask_TaskTextOrderByIdDesc(Long userId, String taskText);
}