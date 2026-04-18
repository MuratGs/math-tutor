package org.example.mathlearning.service;

import org.example.mathlearning.model.TaskHistory;
import org.example.mathlearning.model.User;
import org.example.mathlearning.repository.TaskHistoryRepository;
import org.example.mathlearning.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    @Autowired
    private TaskHistoryRepository taskHistoryRepository;

    @Autowired
    private UserRepository userRepository;  // Добавлено для получения пользователя

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ollama.model:gemma3:4b}")
    private String modelName;

    private final RestTemplate restTemplate = new RestTemplate();

    // Кэш для правильных ответов (чтобы перепроверять)
    private final Map<String, String> answerCache = new HashMap<>();

    // ==================== ГЕНЕРАЦИЯ ЗАДАЧ ====================

    @Transactional
    public String generateTask(String topic, int level, String sessionId) {
        return generateTask(topic, level, sessionId, null);
    }

    @Transactional
    public String generateTask(String topic, int level, String sessionId, Long userId) {
        log.info("🤖 Генерация задачи для пользователя {}... Тема: {}, Уровень: {}", userId, topic, level);

        String topicRu = topic.equals("algebra") ? "алгебре" : "геометрии";

        String prompt = String.format(
                "Ты - учитель математики в российской школе. Придумай математическую задачу по %s для %d класса.\n\n" +
                        "ТРЕБОВАНИЯ К ЗАДАЧЕ:\n" +
                        "1. Задача должна быть на русском языке\n" +
                        "2. Напиши ТОЛЬКО условие задачи, без решения и без ответа\n" +
                        "3. Задача должна быть интересной и понятной\n" +
                        "4. Уровень сложности %d из 5\n\n" +
                        "ПРИМЕРЫ ЗАДАЧ ДЛЯ ЭТОГО УРОВНЯ:\n" +
                        "%s\n\n" +
                        "Придумай свою уникальную задачу, непохожую на примеры. Напиши только условие:",
                topicRu, level, level, getExamplesForLevel(topic, level)
        );

        try {
            String task = callOllama(prompt);
            task = cleanTask(task);

            if (task.length() > 15) {
                log.info("✅ Задача сгенерирована: {}", task);

                TaskHistory history;
                if (userId != null) {
                    // Получаем пользователя из БД
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        history = new TaskHistory(sessionId, user, topic, task, level);
                    } else {
                        history = new TaskHistory(sessionId, topic, task, level);
                    }
                } else {
                    history = new TaskHistory(sessionId, topic, task, level);
                }
                taskHistoryRepository.save(history);
                return task;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка генерации: {}", e.getMessage());
        }

        String fallbackTask = getRussianFallbackTask(topic, level);
        log.info("📚 Использую запасную задачу: {}", fallbackTask);

        TaskHistory history;
        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                history = new TaskHistory(sessionId, user, topic, fallbackTask, level);
            } else {
                history = new TaskHistory(sessionId, topic, fallbackTask, level);
            }
        } else {
            history = new TaskHistory(sessionId, topic, fallbackTask, level);
        }
        taskHistoryRepository.save(history);
        return fallbackTask;
    }

    // ==================== ПРОВЕРКА ОТВЕТОВ ====================

    public String checkAnswer(String task, String answer) {
        log.info("🤖 Проверка ответа на русском...");
        log.info("Задача: {}", task);
        log.info("Ответ: {}", answer);

        if (answerCache.containsKey(task)) {
            String correctAnswer = answerCache.get(task);
            if (compareAnswers(answer, correctAnswer)) {
                return "✅ Правильно! Молодец!";
            }
        }

        String prompt = String.format(
                "Ты - учитель математики. Проверь ответ ученика.\n\n" +
                        "Задача: %s\n" +
                        "Ответ ученика: %s\n\n" +
                        "ИНСТРУКЦИЯ ПО ПРОВЕРКЕ:\n" +
                        "1. Сначала РЕШИ ЭТУ ЗАДАЧУ САМОСТОЯТЕЛЬНО. Выполни все вычисления шаг за шагом.\n" +
                        "2. Запиши свой вычисленный ответ.\n" +
                        "3. Сравни свой ответ с ответом ученика.\n" +
                        "4. Если ответ правильный - напиши только: ✅ Правильно! Молодец!\n" +
                        "5. Если ответ неправильный - напиши только: ❌ Неправильно. Правильный ответ: [число]\n\n" +
                        "ВАЖНО: Ты обязан выполнить вычисления самостоятельно, а не просто оценивать ответ ученика.\n\n" +
                        "Твой ответ:",
                task, answer
        );

        try {
            String response = callOllama(prompt);
            log.info("📦 Ответ от Ollama: {}", response);

            if (response.contains("✅")) {
                extractAndCacheCorrectAnswer(task, answer);
                return response;
            } else if (response.contains("❌")) {
                String correctAnswer = extractCorrectAnswer(response);
                if (correctAnswer != null) {
                    answerCache.put(task, correctAnswer);
                }
                return response;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка проверки: {}", e.getMessage());
        }

        return localCheck(task, answer);
    }

    // ==================== УПРАВЛЕНИЕ ПОПЫТКАМИ ====================

    @Transactional
    public void incrementTaskAttempts(String sessionId, String taskText) {
        incrementTaskAttempts(sessionId, taskText, null);
    }

    @Transactional
    public void incrementTaskAttempts(String sessionId, String taskText, Long userId) {
        Optional<TaskHistory> bySession = taskHistoryRepository.findFirstBySessionIdAndTaskText(sessionId, taskText);
        if (bySession.isPresent()) {
            TaskHistory history = bySession.get();
            history.setAttempts(history.getAttempts() + 1);
            taskHistoryRepository.save(history);
            return;
        }

        if (userId != null) {
            taskHistoryRepository.incrementAttemptsByUserId(userId, taskText);
            log.debug("Увеличено число попыток для пользователя {} по задаче: {}", userId, taskText);
        }
    }

    // ==================== ОТМЕТКА РЕШЁННЫХ ЗАДАЧ ====================

    @Transactional
    public void markTaskAsSolved(String sessionId, String taskText) {
        markTaskAsSolved(sessionId, taskText, null);
    }

    @Transactional
    public void markTaskAsSolved(String sessionId, String taskText, Long userId) {
        Optional<TaskHistory> bySession = taskHistoryRepository.findFirstBySessionIdAndTaskText(sessionId, taskText);
        if (bySession.isPresent()) {
            TaskHistory history = bySession.get();
            history.setSolved(true);
            taskHistoryRepository.save(history);
            log.info("Задача отмечена как решенная по сессии: {}", taskText);
            return;
        }

        if (userId != null) {
            taskHistoryRepository.markAsSolvedByUserId(userId, taskText);
            log.info("Задача отмечена как решенная для пользователя {}: {}", userId, taskText);
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private String callOllama(String prompt) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", modelName);
        request.put("prompt", prompt);
        request.put("stream", false);
        request.put("temperature", 0.3);

        String url = ollamaUrl + "/api/generate";
        Map response = restTemplate.postForObject(url, request, Map.class);

        if (response != null && response.containsKey("response")) {
            return response.get("response").toString();
        } else {
            throw new RuntimeException("Пустой ответ от Ollama");
        }
    }

    private String extractCorrectAnswer(String response) {
        Pattern pattern = Pattern.compile("\\d+(\\.\\d+)?");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private void extractAndCacheCorrectAnswer(String task, String answer) {
        answerCache.put(task, answer.trim());
    }

    private boolean compareAnswers(String userAnswer, String correctAnswer) {
        String cleanUser = userAnswer.trim().replaceAll("\\s+", "");
        String cleanCorrect = correctAnswer.trim().replaceAll("\\s+", "");

        if (cleanUser.equals(cleanCorrect)) {
            return true;
        }

        String userNumber = cleanUser.replaceAll("[^0-9.-]", "");
        String correctNumber = cleanCorrect.replaceAll("[^0-9.-]", "");

        return userNumber.equals(correctNumber);
    }

    private String localCheck(String task, String answer) {
        String taskLower = task.toLowerCase();
        String answerClean = answer.trim().toLowerCase().replaceAll("\\s+", "");

        if (taskLower.contains("2+2") && answerClean.contains("4")) {
            return "✅ Правильно! Молодец!";
        }
        if (taskLower.contains("3+5") && answerClean.contains("8")) {
            return "✅ Правильно! Молодец!";
        }
        if (taskLower.contains("10-4") && answerClean.contains("6")) {
            return "✅ Правильно! Молодец!";
        }
        if (taskLower.contains("x + 3 = 8") && answerClean.contains("5")) {
            return "✅ Правильно! x = 5";
        }
        if (taskLower.contains("2x = 10") && answerClean.contains("5")) {
            return "✅ Правильно! x = 5";
        }

        return "❌ Неправильно. Попробуй еще раз!";
    }

    private String getExamplesForLevel(String topic, int level) {
        if (topic.equals("algebra")) {
            switch (level) {
                case 1: return "- Сколько будет 5 + 3?\n- Найди сумму чисел 7 и 2";
                case 2: return "- Реши уравнение: x + 4 = 12\n- Найди x: x - 3 = 8";
                case 3: return "- Реши уравнение: 2x + 5 = 19\n- Найди x: 3x - 7 = 14";
                case 4: return "- Реши уравнение: 3(x + 2) = 21\n- Найди x: 4x - 8 = 24";
                case 5: return "- Реши уравнение: x² - 7x + 12 = 0\n- Найди корни уравнения: 2x² - 18 = 0";
                default: return "- Пример задачи";
            }
        } else {
            switch (level) {
                case 1: return "- Найди периметр квадрата со стороной 4\n- Чему равна площадь прямоугольника 5×3?";
                case 2: return "- Найди площадь круга радиусом 3 (π≈3.14)\n- Вычисли длину окружности радиусом 4";
                case 3: return "- Найди гипотенузу треугольника с катетами 6 и 8\n- Найди объем куба со стороной 5";
                case 4: return "- Найди площадь трапеции с основаниями 4 и 6 и высотой 3\n- Вычисли объем цилиндра радиусом 2 и высотой 5";
                case 5: return "- Найди объем шара радиусом 3 (π≈3.14)\n- Вычисли площадь поверхности сферы радиусом 4";
                default: return "- Пример задачи";
            }
        }
    }

    private String getRussianFallbackTask(String topic, int level) {
        if (topic.equals("algebra")) {
            switch (level) {
                case 1: return "Сколько будет 2 + 2?";
                case 2: return "Реши уравнение: x + 3 = 8";
                case 3: return "Реши уравнение: 2x + 3 = 11";
                case 4: return "Реши уравнение: 2(x + 3) = 16";
                case 5: return "Реши уравнение: x² - 5x + 6 = 0";
                default: return "Сколько будет 2 + 2?";
            }
        } else {
            switch (level) {
                case 1: return "Найди периметр квадрата со стороной 4";
                case 2: return "Найди площадь прямоугольника со сторонами 5 и 3";
                case 3: return "Найди гипотенузу треугольника с катетами 3 и 4";
                case 4: return "Найди объем прямоугольного параллелепипеда 3×4×5";
                case 5: return "Найди объем шара радиусом 3";
                default: return "Найди периметр квадрата со стороной 4";
            }
        }
    }

    private String cleanTask(String task) {
        return task.replace("Задача:", "")
                .replace("задача:", "")
                .replace("Ответ:", "")
                .replace("ответ:", "")
                .replace("Решение:", "")
                .replace("решение:", "")
                .replaceAll("\\d+\\.\\s*", "")
                .replaceAll("\\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public int getSolvedTasksCountByLevel(String sessionId, int level) {
        return taskHistoryRepository.countSolvedByLevel(sessionId, level);
    }

    @Transactional
    public void clearHistory(String sessionId) {
        taskHistoryRepository.deleteBySessionId(sessionId);
    }

    private String getSimpleTask(String topic, int level) {
        return getRussianFallbackTask(topic, level);
    }
}