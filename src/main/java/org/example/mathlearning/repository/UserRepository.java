package org.example.mathlearning.repository;

import org.example.mathlearning.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    List<User> findTop10ByOrderByCreatedAtDesc();

    @Query("SELECT u.currentLevel, COUNT(u) FROM User u GROUP BY u.currentLevel ORDER BY u.currentLevel")
    List<Object[]> countUsersByLevel();

    @Query("SELECT COALESCE(SUM(u.totalCorrect), 0), COALESCE(SUM(u.totalAttempts), 0) FROM User u")
    List<Object[]> getTotals();

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :since")
    long countNewUsersSince(@Param("since") LocalDateTime since);

    @Query("SELECT u.currentLevel FROM User u WHERE u.id = :userId")
    Integer findCurrentLevelByUserId(@Param("userId") Long userId);
}