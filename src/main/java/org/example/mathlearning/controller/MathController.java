package org.example.mathlearning.controller;

import org.example.mathlearning.model.TaskHistory;
import org.example.mathlearning.model.User;
import org.example.mathlearning.repository.TaskHistoryRepository;
import org.example.mathlearning.repository.UserRepository;
import org.example.mathlearning.service.AnalyticsService;
import org.example.mathlearning.service.OllamaService;
import org.example.mathlearning.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;

@Controller
public class MathController {

    private static final Logger log = LoggerFactory.getLogger(MathController.class);

    @Autowired
    private OllamaService ollamaService;

    @Autowired
    private UserService userService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskHistoryRepository taskHistoryRepository;

    // Главная страница
    @GetMapping("/")
    public String index(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }
        return "index";
    }

    // Страница обучения
    @GetMapping("/learn")
    public String learn(@RequestParam(defaultValue = "algebra") String topic,
                        HttpSession session,
                        Model model) {

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        // Получаем рекомендацию от аналитики
        int recommendedLevel = analyticsService.recommendNextLevel(userId);
        session.setAttribute("recommendedLevel", recommendedLevel);
        session.setAttribute("userLevel", user.getCurrentLevel());

        String sessionId = session.getId();

        log.info("===== ГЕНЕРАЦИЯ ЗАДАЧИ ДЛЯ ПОЛЬЗОВАТЕЛЯ {} =====", userId);
        log.info("Тема: {}, Текущий уровень: {}, Рекомендованный уровень: {}", topic, user.getCurrentLevel(), recommendedLevel);

        String currentTask = (String) session.getAttribute("currentTask");
        String currentTopic = (String) session.getAttribute("currentTopic");

        if (currentTask != null && !currentTask.isEmpty() && currentTopic != null && currentTopic.equals(topic)) {
            log.info("Используем существующую задачу для повторной попытки");
            model.addAttribute("task", currentTask);
            model.addAttribute("topic", topic);
            model.addAttribute("level", user.getCurrentLevel());
            model.addAttribute("recommendedLevel", recommendedLevel);
            model.addAttribute("attempts", session.getAttribute("attempts"));
            return "learn";
        }

        try {
            String task = ollamaService.generateTask(topic, user.getCurrentLevel(), sessionId, userId);
            log.info("Сгенерированная задача: {}", task);

            session.setAttribute("currentTask", task);
            session.setAttribute("currentTopic", topic);
            session.setAttribute("attempts", 0);

            model.addAttribute("task", task);
            model.addAttribute("topic", topic);
            model.addAttribute("level", user.getCurrentLevel());
            model.addAttribute("recommendedLevel", recommendedLevel);
            model.addAttribute("attempts", 0);

            return "learn";

        } catch (Exception e) {
            log.error("ОШИБКА при генерации задачи: {}", e.getMessage());
            model.addAttribute("error", "Ошибка: " + e.getMessage());
            return "error";
        }
    }

    // Проверка ответа
    @PostMapping("/check")
    public String check(@RequestParam String task,
                        @RequestParam String answer,
                        HttpSession session,
                        Model model) {

        log.info("===== ПРОВЕРКА ОТВЕТА =====");
        log.info("Задача: {}", task);
        log.info("Ответ: {}", answer);

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        String sessionId = session.getId();
        Integer level = user.getCurrentLevel();
        Integer attempts = (Integer) session.getAttribute("attempts");
        Integer correctInRow = (Integer) session.getAttribute("correctInRow");
        Integer wrongInRow = (Integer) session.getAttribute("wrongInRow");

        if (attempts == null) attempts = 0;
        if (correctInRow == null) correctInRow = 0;
        if (wrongInRow == null) wrongInRow = 0;

        attempts++;
        session.setAttribute("attempts", attempts);

        ollamaService.incrementTaskAttempts(sessionId, task, userId);

        String result = ollamaService.checkAnswer(task, answer);
        boolean isCorrect = result.contains("✅") || result.contains("Правильно") || result.contains("Верно");

        log.info("Результат проверки: {} (правильно: {})", result, isCorrect);

        userService.updateUserStats(userId, isCorrect);
        user = userRepository.findById(userId).orElse(user);

        if (isCorrect) {
            ollamaService.markTaskAsSolved(sessionId, task, userId);

            correctInRow++;
            wrongInRow = 0;
            session.setAttribute("correctInRow", correctInRow);
            session.setAttribute("wrongInRow", wrongInRow);

            session.removeAttribute("currentTask");
            session.removeAttribute("attempts");

            if (correctInRow >= 3 && user.getCurrentLevel() < 5) {
                int newLevel = user.getCurrentLevel() + 1;
                userService.updateUserLevel(userId, newLevel);
                user = userRepository.findById(userId).orElse(user);
                session.setAttribute("userLevel", newLevel);
                result += " 🎉 УРОВЕНЬ ПОВЫШЕН ДО " + newLevel + "! Поздравляю!";
                session.setAttribute("correctInRow", 0);
            } else {
                int needed = 3 - correctInRow;
                result += " Осталось " + needed + " правильных ответов подряд до следующего уровня.";
            }

            model.addAttribute("result", result);
            model.addAttribute("task", task);
            model.addAttribute("level", user.getCurrentLevel());
            model.addAttribute("isCorrect", true);

        } else {
            wrongInRow++;
            correctInRow = 0;
            session.setAttribute("wrongInRow", wrongInRow);
            session.setAttribute("correctInRow", 0);

            log.info("Неправильный ответ. Попытка {} из 3", attempts);

            if (wrongInRow >= 2 && user.getCurrentLevel() > 1) {
                int newLevel = user.getCurrentLevel() - 1;
                userService.updateUserLevel(userId, newLevel);
                user = userRepository.findById(userId).orElse(user);
                session.setAttribute("userLevel", newLevel);
                result += " ⚠️ УРОВЕНЬ ПОНИЖЕН ДО " + newLevel + " для повторения материала.";
                session.setAttribute("wrongInRow", 0);
            }

            if (attempts >= 3) {
                result = "❌ К сожалению, ты не справился с задачей после 3 попыток. " +
                        "Попробуй решить другую задачу того же уровня.";

                session.removeAttribute("currentTask");
                session.removeAttribute("attempts");

                model.addAttribute("result", result);
                model.addAttribute("task", task);
                model.addAttribute("level", user.getCurrentLevel());
                model.addAttribute("isCorrect", false);
                model.addAttribute("newTask", true);
            } else {
                result += " Попробуй еще раз. Осталось попыток: " + (3 - attempts);

                model.addAttribute("result", result);
                model.addAttribute("task", task);
                model.addAttribute("level", user.getCurrentLevel());
                model.addAttribute("isCorrect", false);
                model.addAttribute("attemptsLeft", 3 - attempts);
            }
        }

        return "result";
    }

    // Сброс прогресса
    @GetMapping("/reset")
    public String reset(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setCurrentLevel(1);
            user.setTotalCorrect(0);
            user.setTotalAttempts(0);
            userRepository.save(user);
        }

        taskHistoryRepository.deleteByUserId(userId);

        session.setAttribute("userLevel", 1);
        session.setAttribute("correctInRow", 0);
        session.setAttribute("wrongInRow", 0);
        session.removeAttribute("currentTask");
        session.removeAttribute("attempts");

        log.info("Прогресс сброшен для пользователя: {}", userId);
        return "redirect:/profile";
    }

    // Статистика в JSON
    @GetMapping("/api/stats")
    @ResponseBody
    public Map<String, Object> apiStats(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        Map<String, Object> stats = new HashMap<>();

        if (userId == null) {
            stats.put("error", "Not logged in");
            return stats;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            stats.put("error", "User not found");
            return stats;
        }

        stats.put("userId", userId);
        stats.put("username", user.getUsername());
        stats.put("currentLevel", user.getCurrentLevel());
        stats.put("totalCorrect", user.getTotalCorrect());
        stats.put("totalAttempts", user.getTotalAttempts());
        stats.put("successRate", userService.getSuccessRate(userId));
        stats.put("levelStats", userService.getLevelStats(userId));
        stats.put("analytics", analyticsService.getLevelStatistics(userId));
        stats.put("weakTopic", analyticsService.identifyWeakTopics(userId));
        stats.put("recommendedLevel", analyticsService.recommendNextLevel(userId));

        return stats;
    }
}