package org.example.mathlearning.service;

import org.example.mathlearning.model.TaskHistory;
import org.example.mathlearning.model.User;
import org.example.mathlearning.repository.TaskHistoryRepository;
import org.example.mathlearning.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class AnalyticsService {

    @Autowired
    private TaskHistoryRepository taskHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Получить рекомендацию следующего уровня
     * @param userId ID пользователя
     * @return рекомендуемый уровень (1-5)
     */
    public int recommendNextLevel(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return 1;

        int currentLevel = user.getCurrentLevel();
        List<TaskHistory> recentTasks = taskHistoryRepository.findRecentByUserId(userId, 10);

        if (recentTasks.isEmpty()) {
            return currentLevel; // Нет данных - текущий уровень
        }

        // Анализируем последние задачи
        int correctCount = 0;
        int totalCount = recentTasks.size();

        for (TaskHistory task : recentTasks) {
            if (task.getSolved()) correctCount++;
        }

        double successRate = (double) correctCount / totalCount;

        // Логика адаптации
        if (successRate >= 0.8) {
            // 80%+ правильных - повышаем уровень
            return Math.min(5, currentLevel + 1);
        } else if (successRate <= 0.3) {
            // Меньше 30% правильных - понижаем
            return Math.max(1, currentLevel - 1);
        } else {
            return currentLevel; // Оставляем текущий
        }
    }

    /**
     * Получить статистику по уровням
     */
    public Map<String, Object> getLevelStatistics(Long userId) {
        Map<String, Object> stats = new HashMap<>();

        List<Object[]> levelStats = taskHistoryRepository.getLevelStatsByUserId(userId);

        for (Object[] stat : levelStats) {
            Integer level = (Integer) stat[0];
            Long total = (Long) stat[1];
            Long solved = (Long) stat[2];

            Map<String, Object> levelData = new HashMap<>();
            levelData.put("total", total);
            levelData.put("solved", solved);
            levelData.put("successRate", (double) solved / total);

            stats.put("level_" + level, levelData);
        }

        return stats;
    }

    /**
     * Определить слабые темы пользователя
     */
    public String identifyWeakTopics(Long userId) {
        List<TaskHistory> tasks = taskHistoryRepository.findByUserId(userId);

        int algebraCorrect = 0;
        int algebraTotal = 0;
        int geometryCorrect = 0;
        int geometryTotal = 0;

        for (TaskHistory task : tasks) {
            if ("algebra".equals(task.getTopic())) {
                algebraTotal++;
                if (task.getSolved()) algebraCorrect++;
            } else {
                geometryTotal++;
                if (task.getSolved()) geometryCorrect++;
            }
        }

        double algebraRate = algebraTotal > 0 ? (double) algebraCorrect / algebraTotal : 1.0;
        double geometryRate = geometryTotal > 0 ? (double) geometryCorrect / geometryTotal : 1.0;

        if (algebraRate < 0.5 && algebraTotal > 5) {
            return "algebra";
        } else if (geometryRate < 0.5 && geometryTotal > 5) {
            return "geometry";
        }

        return "balanced";
    }
}