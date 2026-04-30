package org.example.mathlearning.service;

import org.example.mathlearning.model.TaskHistory;
import org.example.mathlearning.model.User;
import org.example.mathlearning.repository.TaskHistoryRepository;
import org.example.mathlearning.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskHistoryRepository taskHistoryRepository;

    public Map<Integer, Object> getLevelStats(Long userId) {
        List<Object[]> stats = taskHistoryRepository.getLevelStatsByUserId(userId);
        Map<Integer, Object> result = new HashMap<>();

        for (Object[] stat : stats) {
            Integer level = (Integer) stat[0];
            Long total = (Long) stat[1];
            Long solved = (Long) stat[2];

            Map<String, Object> levelData = new HashMap<>();
            levelData.put("total", total);
            levelData.put("solved", solved);
            levelData.put("percent", total > 0 ? (solved * 100 / total) : 0);

            result.put(level, levelData);
        }

        // Добавляем уровни, по которым ещё нет статистики
        for (int i = 1; i <= 5; i++) {
            if (!result.containsKey(i)) {
                Map<String, Object> emptyData = new HashMap<>();
                emptyData.put("total", 0);
                emptyData.put("solved", 0);
                emptyData.put("percent", 0);
                result.put(i, emptyData);
            }
        }

        return result;
    }

    public List<TaskHistory> getRecentTasks(Long userId, int limit) {
        ensureSingleInProgressTask(userId);
        return taskHistoryRepository.findRecentForProfileByUserId(userId, limit);
    }

    @Transactional
    protected void ensureSingleInProgressTask(Long userId) {
        if (userId == null) {
            return;
        }
        taskHistoryRepository.findTopByUser_IdAndSolvedFalseAndAbandonedFalseOrderByCreatedAtDesc(userId)
                .ifPresent(h -> {
                    if (h.getId() != null) {
                        taskHistoryRepository.abandonOtherInProgressTasks(userId, h.getId());
                    }
                });
    }

    public double getSuccessRate(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getTotalAttempts() == 0) return 0.0;
        return (double) user.getTotalCorrect() / user.getTotalAttempts() * 100;
    }

    public void updateUserStats(Long userId, boolean isCorrect) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setTotalAttempts(user.getTotalAttempts() + 1);
            if (isCorrect) {
                user.setTotalCorrect(user.getTotalCorrect() + 1);
            }
            userRepository.save(user);
        }
    }

    /**
     * Обновление уровня пользователя
     * @param userId ID пользователя
     * @param newLevel Новый уровень (от 1 до 5)
     */
    public void updateUserLevel(Long userId, int newLevel) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            // Проверяем, что уровень в допустимых пределах
            int validatedLevel = Math.max(1, Math.min(5, newLevel));
            user.setCurrentLevel(validatedLevel);
            userRepository.save(user);
            System.out.println("✅ Уровень пользователя " + userId + " обновлён до " + validatedLevel);
        } else {
            System.out.println("❌ Пользователь с ID " + userId + " не найден");
        }
    }
}