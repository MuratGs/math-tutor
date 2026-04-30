package org.example.mathlearning.service;

import org.example.mathlearning.model.MathTask;
import org.example.mathlearning.model.TaskHistory;
import org.example.mathlearning.model.User;
import org.example.mathlearning.repository.MathTaskRepository;
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
    private MathTaskRepository mathTaskRepository;

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

    public String generateNewStandardTask(String topic, int grade, int difficulty) {
        String topicRu;
        switch (topic) {
            case "fractions":
                topicRu = "дробям";
                break;
            case "percent":
                topicRu = "процентам";
                break;
            case "equations":
                topicRu = "уравнениям";
                break;
            case "areas":
                topicRu = "площадям";
                break;
            case "speed_time_distance":
                topicRu = "задачам на скорость, время и расстояние";
                break;
            default:
                topicRu = topic;
        }

        String prompt = String.format(
                "Ты - учитель математики. Сгенерируй ОДНУ задачу по теме, корректную и однозначную.\n\n" +
                        "Тема: %s\n" +
                        "Класс: %d\n" +
                        "Сложность: %d из 5\n\n" +
                        "ТРЕБОВАНИЯ:\n" +
                        "1. Напиши ТОЛЬКО условие задачи, без решения и без ответа.\n" +
                        "2. Задача должна быть на русском языке.\n" +
                        "3. Если используешь дроби, каждая дробь должна быть полностью записана как a/b (с числителем и знаменателем).\n" +
                        "4. Не оставляй незавершённых дробей вида '7/'.\n\n" +
                        "5. Не используй смешанные дроби вида '1 1/2' — используй неправильную дробь (например 3/2).\n\n" +
                        "6. Условие должно быть однозначным: все числа/единицы/данные должны быть указаны, один чёткий вопрос.\n\n" +
                        "Напиши только условие:",
                topicRu, grade, difficulty
        );

        try {
            String task = callOllama(prompt);
            task = cleanTask(task);
            if (task == null || task.trim().isEmpty()) {
                return null;
            }
            return task;
        } catch (Exception e) {
            log.error("❌ Ошибка генерации новой стандартной задачи: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public String generateTask(String topic, int level, String sessionId, Long userId) {
        log.info("🤖 Генерация задачи для пользователя {}... Тема: {}, Уровень: {}", userId, topic, level);

        String topicRu = StandardTaskService.getTopicDisplayName(topic);

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        List<String> recentSolvedTasks = Collections.emptyList();
        if (user != null) {
            recentSolvedTasks = taskHistoryRepository.findSolvedTaskTextsByUserIdAndTopicAndLevel(user.getId(), topic, level, 20);
        }

        String prompt = String.format(
                "Ты - учитель математики в российской школе. Придумай математическую задачу по теме '%s' для %d класса.\n\n" +
                        "ТРЕБОВАНИЯ К ЗАДАЧЕ:\n" +
                        "1. Задача должна быть на русском языке\n" +
                        "2. Напиши ТОЛЬКО условие задачи, без решения и без ответа\n" +
                        "3. Задача должна быть интересной и понятной\n" +
                        "4. Уровень сложности %d из 5\n\n" +
                        "ПРИМЕРЫ ЗАДАЧ ДЛЯ ЭТОГО УРОВНЯ:\n" +
                        "%s\n\n" +
                        "ВАЖНО: не повторяй задачи, которые ученик уже решал успешно.\n" +
                        "Придумай свою уникальную задачу, непохожую на примеры. Напиши только условие:",
                topicRu, level, level, getExamplesForLevel(topic, level)
        );

        if (user != null && !recentSolvedTasks.isEmpty()) {
            prompt = prompt + "\n\nУченик уже решал успешно (не повторяй):\n" + String.join("\n", recentSolvedTasks);
        }

        try {
            int maxGenerationAttempts = 6;
            for (int i = 0; i < maxGenerationAttempts; i++) {
                String task = callOllama(prompt);
                task = cleanTask(task);

                if (task.length() <= 15) {
                    continue;
                }

                if (taskHistoryRepository.existsBySessionIdAndTask_TaskText(sessionId, task)) {
                    log.debug("Сгенерирована повторяющаяся задача в рамках сессии, пробую ещё раз");
                    continue;
                }

                MathTask mathTask = getOrCreateMathTask(topic, level, task);

                if (user != null && mathTask.getId() != null) {
                    boolean alreadySolved = taskHistoryRepository.existsByUser_IdAndTask_IdAndSolvedTrue(user.getId(), mathTask.getId());
                    if (alreadySolved) {
                        log.debug("Пользователь {} уже решал эту задачу успешно, пробую сгенерировать другую", user.getId());
                        continue;
                    }
                }

                log.info("✅ Задача сгенерирована: {}", task);

                TaskHistory history = (user != null)
                        ? new TaskHistory(sessionId, user, mathTask)
                        : new TaskHistory(sessionId, mathTask);
                taskHistoryRepository.save(history);

                if (user != null && history.getId() != null) {
                    taskHistoryRepository.abandonOtherInProgressTasks(user.getId(), history.getId());
                }
                return task;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка генерации: {}", e.getMessage());
        }

        List<String> fallbackTasks = getRussianFallbackTasks(topic, level);
        for (String fallbackTask : fallbackTasks) {
            MathTask fallbackMathTask = getOrCreateMathTask(topic, level, fallbackTask);
            if (user != null && fallbackMathTask.getId() != null) {
                boolean alreadySolved = taskHistoryRepository.existsByUser_IdAndTask_IdAndSolvedTrue(user.getId(), fallbackMathTask.getId());
                if (alreadySolved) {
                    continue;
                }
            }

            log.info("📚 Использую запасную задачу: {}", fallbackTask);
            TaskHistory history = (user != null)
                    ? new TaskHistory(sessionId, user, fallbackMathTask)
                    : new TaskHistory(sessionId, fallbackMathTask);
            taskHistoryRepository.save(history);

            if (user != null && history.getId() != null) {
                taskHistoryRepository.abandonOtherInProgressTasks(user.getId(), history.getId());
            }
            return fallbackTask;
        }

        String fallbackTask = getRussianFallbackTask(topic, level);
        log.info("📚 Использую запасную задачу: {}", fallbackTask);
        MathTask fallbackMathTask = getOrCreateMathTask(topic, level, fallbackTask);
        TaskHistory history = (user != null)
                ? new TaskHistory(sessionId, user, fallbackMathTask)
                : new TaskHistory(sessionId, fallbackMathTask);
        taskHistoryRepository.save(history);

        if (user != null && history.getId() != null) {
            taskHistoryRepository.abandonOtherInProgressTasks(user.getId(), history.getId());
        }
        return fallbackTask;
    }

    public String generateSimilarStandardTask(String topic, int grade, int difficulty, String baseStatement) {
        String topicRu;
        switch (topic) {
            case "fractions":
                topicRu = "дробям";
                break;
            case "percent":
                topicRu = "процентам";
                break;
            case "equations":
                topicRu = "уравнениям";
                break;
            case "areas":
                topicRu = "площадям";
                break;
            case "speed_time_distance":
                topicRu = "задачам на скорость, время и расстояние";
                break;
            default:
                topicRu = topic;
        }

        String prompt = String.format(
                "Ты - учитель математики. Сгенерируй ПОДОБНУЮ задачу, максимально близкую по типу, теме и уровню сложности. " +
                        "НО числа должны быть другими (и можно чуть изменить формулировку).\n\n" +
                        "Тема: %s\n" +
                        "Класс: %d\n" +
                        "Сложность: %d из 5\n\n" +
                        "Исходная задача: %s\n\n" +
                        "ТРЕБОВАНИЯ:\n" +
                        "1. Напиши ТОЛЬКО условие задачи, без решения и без ответа.\n" +
                        "2. Задача должна быть на русском языке.\n" +
                        "3. Сохрани тот же тип навыка (тот же формат), но измени числа.\n" +
                        "4. Условие должно быть корректным и однозначным.\n\n" +
                        "5. Если используешь дроби — не оставляй незавершённых дробей вида '7/'.\n" +
                        "6. Не используй смешанные дроби вида '1 1/2' — используй неправильную дробь (например 3/2).\n\n" +
                        "7. Условие должно быть однозначным: все числа/единицы/данные должны быть указаны, один чёткий вопрос.\n\n" +
                        "Напиши только условие:",
                topicRu, grade, difficulty, baseStatement
        );

        try {
            String task = callOllama(prompt);
            task = cleanTask(task);
            if (task == null || task.trim().isEmpty()) {
                return null;
            }
            return task;
        } catch (Exception e) {
            log.error("❌ Ошибка генерации похожей задачи: {}", e.getMessage());
            return null;
        }
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
                        "ФОРМАТ ОТВЕТА (ОБЯЗАТЕЛЬНО):\n" +
                        "1) Первая строка должна НАЧИНАТЬСЯ строго с ✅ или ❌ (без текста до эмодзи).\n" +
                        "2) Вторая строка должна быть строго в формате: ОТВЕТ=... (без лишнего текста).\n" +
                        "   Примеры: ОТВЕТ=12  или  ОТВЕТ=3;8\n\n" +
                        "ОБЯЗАТЕЛЬНЫЕ ПРАВИЛА СРАВНЕНИЯ ОТВЕТА (НЕ НАРУШАЙ):\n" +
                        "1. Никогда не считай ответ неверным только из-за формата записи (пробелы, запятые, слова, единицы измерения).\n" +
                        "2. Десятичные дроби: запятая и точка эквивалентны (12,4 = 12.4).\n" +
                        "3. Если по условию требуется ДВА значения (например: 'длина и ширина', 'x и y', 'два числа'),\n" +
                        "   то ответ вида '3,8' / '3 8' / '3;8' трактуй как ДВА числа {3 и 8}, а НЕ как десятичную дробь 3.8.\n" +
                        "4. Если порядок значений не указан явно, принимай любой порядок (например 3,8 = 8,3).\n\n" +
                        "ИНСТРУКЦИЯ ПО ПРОВЕРКЕ:\n" +
                        "1. Сначала РЕШИ ЭТУ ЗАДАЧУ САМОСТОЯТЕЛЬНО. Выполни все вычисления шаг за шагом.\n" +
                        "2. Запиши свой вычисленный ответ в строке ОТВЕТ=...\n" +
                        "3. Сравни с ответом ученика.\n" +
                        "4. Если ответ правильный - первая строка ✅. Если неправильный - первая строка ❌.\n" +
                        "5. С третьей строки (необязательно) можешь дать короткое объяснение.\n\n" +
                        "Твой ответ:",
                task, answer
        );

        try {
            String response = callOllama(prompt);
            log.info("📦 Ответ от Ollama: {}", response);

            Boolean verdict = parseCheckVerdict(response);
            if (verdict != null) {
                String correctAnswer = extractCorrectAnswer(response);
                if (correctAnswer != null) {
                    answerCache.put(task, correctAnswer);
                }

                if (Boolean.TRUE.equals(verdict)) {
                    if (correctAnswer != null) {
                        boolean ok = compareAnswers(answer, correctAnswer);
                        if (!ok) {
                            return "❌ Неправильно. Правильный ответ: " + correctAnswer;
                        }
                    }
                    return response;
                }

                return response;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка проверки: {}", e.getMessage());
        }

        return localCheck(task, answer);
    }

    private Boolean parseCheckVerdict(String response) {
        if (response == null) {
            return null;
        }
        String r = response.trim();
        if (r.isEmpty()) {
            return null;
        }

        String firstLine = r.split("\\R", 2)[0].trim();
        if (firstLine.contains("✅")) {
            return true;
        }
        if (firstLine.contains("❌")) {
            return false;
        }

        int ok = r.indexOf("✅");
        int bad = r.indexOf("❌");
        if (ok >= 0 && bad >= 0) {
            return ok < bad;
        }
        if (ok >= 0) {
            return true;
        }
        if (bad >= 0) {
            return false;
        }

        String firstLineLower = firstLine.toLowerCase(Locale.ROOT);
        if (firstLineLower.startsWith("правильно") || firstLineLower.startsWith("верно")) {
            return true;
        }

        if (firstLineLower.startsWith("неправильно") || firstLineLower.startsWith("неверно")) {
            return false;
        }
        return null;
    }

    public String disputeAnswer(String task, String answer) {
        log.info("🤖 Перепроверка ответа (оспаривание)...");
        log.info("Задача: {}", task);
        log.info("Ответ: {}", answer);

        String prompt = String.format(
                "Ты - строгий, но справедливый учитель математики. Ученик оспаривает проверку ответа.\n\n" +
                        "Задача: %s\n" +
                        "Ответ ученика: %s\n\n" +
                        "ОБЯЗАТЕЛЬНЫЕ ПРАВИЛА (НЕ НАРУШАЙ):\n" +
                        "1. Запрещено объявлять ответ неверным только из-за оформления (пробелы, запятые, единицы измерения, слова).\n" +
                        "2. Десятичные дроби: 12,4 и 12.4 — одно и то же число.\n" +
                        "3. Если по смыслу задачи требуется НЕ ОДНО число (например 'длина и ширина'),\n" +
                        "   то ответ вида '3,8' / '3 8' / '3;8' трактуй как два числа {3 и 8}, а НЕ как десятичную дробь 3.8.\n" +
                        "4. Если порядок значений не указан, принимай любой порядок.\n" +
                        "5. Перед вердиктом обязательно распарсь ответ ученика в список чисел и явно выпиши: 'Я распознал числа: ...'.\n\n" +
                        "ИНСТРУКЦИЯ:\n" +
                        "1. Реши задачу полностью сам, шаг за шагом.\n" +
                        "2. Сформулируй корректный ответ.\n" +
                        "3. Сравни с ответом ученика.\n" +
                        "5. Если ответ ученика верный (в том числе из-за формата записи) - напиши: ✅ Пересмотр: ответ верный.\n" +
                        "6. Если ответ неверный - напиши: ❌ Пересмотр: ответ неверный.\n" +
                        "7. В случае неверного ответа дай развернутое объяснение: где ошибка, как правильно решить, и какой правильный ответ.\n\n" +
                        "Формат ответа:\n" +
                        "- Вердикт (первая строка)\n" +
                        "- Я распознал числа (вторая строка)\n" +
                        "- Решение (коротко, но понятно)\n" +
                        "- Сравнение ответа ученика с правильным\n" +
                        "- Итог\n",
                task, answer
        );

        try {
            return callOllama(prompt);
        } catch (Exception e) {
            log.error("❌ Ошибка перепроверки: {}", e.getMessage());
            return "❌ Не удалось перепроверить ответ. Попробуй еще раз позже.";
        }
    }

    // ==================== УПРАВЛЕНИЕ ПОПЫТКАМИ ====================

    @Transactional
    public void incrementTaskAttempts(String sessionId, String taskText) {
        incrementTaskAttempts(sessionId, taskText, null);
    }

    @Transactional
    public void incrementTaskAttempts(String sessionId, String taskText, Long userId) {
        Optional<TaskHistory> bySession = taskHistoryRepository.findTopBySessionIdAndTask_TaskTextOrderByIdDesc(sessionId, taskText);
        if (bySession.isPresent()) {
            TaskHistory history = bySession.get();
            history.setAttempts(history.getAttempts() + 1);
            taskHistoryRepository.save(history);
            return;
        }

        if (userId != null) {
            Optional<TaskHistory> byUser = taskHistoryRepository.findTopByUser_IdAndTask_TaskTextOrderByIdDesc(userId, taskText);
            if (byUser.isPresent()) {
                TaskHistory history = byUser.get();
                history.setAttempts(history.getAttempts() + 1);
                taskHistoryRepository.save(history);
                log.debug("Увеличено число попыток для пользователя {} по задаче: {}", userId, taskText);
            }
        }
    }

    // ==================== ОТМЕТКА РЕШЁННЫХ ЗАДАЧ ====================

    @Transactional
    public void markTaskAsSolved(String sessionId, String taskText) {
        markTaskAsSolved(sessionId, taskText, null);
    }

    @Transactional
    public void markTaskAsSolved(String sessionId, String taskText, Long userId) {
        Optional<TaskHistory> bySession = taskHistoryRepository.findTopBySessionIdAndTask_TaskTextOrderByIdDesc(sessionId, taskText);
        if (bySession.isPresent()) {
            TaskHistory history = bySession.get();

            if (userId != null && history.getTask() != null && history.getTask().getId() != null && Boolean.FALSE.equals(history.getSolved())) {
                boolean alreadySolved = taskHistoryRepository.existsByUser_IdAndTask_IdAndSolvedTrue(userId, history.getTask().getId());
                if (alreadySolved) {
                    taskHistoryRepository.delete(history);
                    log.info("Задача уже была решена пользователем ранее, дубликат в истории удалён: {}", taskText);
                    return;
                }
            }

            history.setSolved(true);
            taskHistoryRepository.save(history);
            log.info("Задача отмечена как решенная по сессии: {}", taskText);
            return;
        }

        if (userId != null) {
            Optional<TaskHistory> byUser = taskHistoryRepository.findTopByUser_IdAndTask_TaskTextOrderByIdDesc(userId, taskText);
            if (byUser.isPresent()) {
                TaskHistory history = byUser.get();

                if (history.getTask() != null && history.getTask().getId() != null && Boolean.FALSE.equals(history.getSolved())) {
                    boolean alreadySolved = taskHistoryRepository.existsByUser_IdAndTask_IdAndSolvedTrue(userId, history.getTask().getId());
                    if (alreadySolved) {
                        taskHistoryRepository.delete(history);
                        log.info("Задача уже была решена пользователем ранее, дубликат в истории удалён: {}", taskText);
                        return;
                    }
                }

                history.setSolved(true);
                taskHistoryRepository.save(history);
                log.info("Задача отмечена как решенная для пользователя {}: {}", userId, taskText);
            }
        }
    }

    private MathTask getOrCreateMathTask(String topic, int level, String taskText) {
        return mathTaskRepository.findByTopicAndLevelAndTaskText(topic, level, taskText)
                .orElseGet(() -> mathTaskRepository.save(new MathTask(topic, level, taskText)));
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    public String askModel(String prompt) {
        return callOllama(prompt);
    }

    public String generateTopicRelations(String newTopicKey, String newTopicDisplayName, List<String> existingTopics) {
        String topicsBlock = existingTopics == null || existingTopics.isEmpty()
                ? ""
                : String.join("\n", existingTopics);

        String prompt = String.format(
                "You are a math curriculum expert. A new topic '%s' (key: '%s') is being added to a math tutoring system.\n" +
                        "Existing topics in the system:\n" +
                        "%s\n\n" +
                        "Determine which existing topics are related to '%s' and how strongly (0.0 to 1.0).\n" +
                        "Return ONLY a JSON array, no explanation: [{\"related_topic\":\"topic_key\",\"strength\":0.85}, ...]\n" +
                        "Only include topics with strength > 0.3. Maximum 6 relations.",
                newTopicDisplayName, newTopicKey, topicsBlock, newTopicDisplayName
        );

        String response = callOllama(prompt);
        return extractJsonArray(response);
    }

    private String extractJsonArray(String response) {
        if (response == null) {
            return null;
        }
        String r = response.trim();

        int firstBracket = r.indexOf('[');
        int lastBracket = r.lastIndexOf(']');
        if (firstBracket >= 0 && lastBracket >= 0 && lastBracket > firstBracket) {
            return r.substring(firstBracket, lastBracket + 1).trim();
        }
        return r;
    }

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
        if (response == null) {
            return null;
        }

        String[] lines = response.split("\\R");
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String t = line.trim();
            if (t.toUpperCase(Locale.ROOT).startsWith("ОТВЕТ=")) {
                String v = t.substring("ОТВЕТ=".length()).trim();
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }

        Pattern pattern = Pattern.compile("\\d+(?:[\\.,]\\d+)?");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group().replace(',', '.');
        }
        return null;
    }

    private boolean compareAnswers(String userAnswer, String correctAnswer) {
        String normalizedUser = userAnswer == null ? "" : userAnswer.trim().replace(',', '.');
        String normalizedCorrect = correctAnswer == null ? "" : correctAnswer.trim().replace(',', '.');

        String cleanUser = normalizedUser.replaceAll("\\s+", "");
        String cleanCorrect = normalizedCorrect.replaceAll("\\s+", "");

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

    private List<String> getRussianFallbackTasks(String topic, int level) {
        if (topic.equals("algebra")) {
            switch (level) {
                case 1: return Arrays.asList(
                        "Сколько будет 2 + 2?",
                        "Сколько будет 3 + 5?",
                        "Сколько будет 7 - 2?",
                        "Сколько будет 6 + 4?"
                );
                case 2: return Arrays.asList(
                        "Реши уравнение: x + 3 = 8",
                        "Реши уравнение: x - 4 = 9",
                        "Реши уравнение: x + 7 = 15",
                        "Реши уравнение: x - 6 = 11"
                );
                case 3: return Arrays.asList(
                        "Реши уравнение: 2x + 3 = 11",
                        "Реши уравнение: 3x - 7 = 14",
                        "Реши уравнение: 2x - 5 = 9"
                );
                case 4: return Arrays.asList(
                        "Реши уравнение: 2(x + 3) = 16",
                        "Реши уравнение: 3(x - 2) = 12",
                        "Реши уравнение: 4(x + 1) = 20"
                );
                case 5: return Arrays.asList(
                        "Реши уравнение: x² - 5x + 6 = 0",
                        "Реши уравнение: x² - 7x + 12 = 0",
                        "Реши уравнение: x² - 9x + 20 = 0"
                );
                default: return Collections.singletonList(getRussianFallbackTask(topic, level));
            }
        }

        switch (level) {
            case 1: return Arrays.asList(
                    "Найди периметр квадрата со стороной 4",
                    "Найди периметр прямоугольника со сторонами 6 и 2",
                    "Найди площадь прямоугольника 5×3"
            );
            case 2: return Arrays.asList(
                    "Найди площадь прямоугольника со сторонами 5 и 3",
                    "Найди площадь прямоугольника со сторонами 7 и 4",
                    "Вычисли длину окружности радиусом 4 (π≈3.14)"
            );
            case 3: return Arrays.asList(
                    "Найди гипотенузу треугольника с катетами 3 и 4",
                    "Найди гипотенузу треугольника с катетами 6 и 8",
                    "Найди объем куба со стороной 5"
            );
            case 4: return Arrays.asList(
                    "Найди объем прямоугольного параллелепипеда 3×4×5",
                    "Найди площадь трапеции с основаниями 4 и 6 и высотой 3",
                    "Вычисли объем цилиндра радиусом 2 и высотой 5 (π≈3.14)"
            );
            case 5: return Arrays.asList(
                    "Найди объем шара радиусом 3 (π≈3.14)",
                    "Вычисли площадь поверхности сферы радиусом 4 (π≈3.14)",
                    "Найди объем конуса радиусом 3 и высотой 6 (π≈3.14)"
            );
            default: return Collections.singletonList(getRussianFallbackTask(topic, level));
        }
    }

    private String cleanTask(String task) {
        if (task == null) {
            return "";
        }

        String t = task;
        t = t.replace("Задача:", "")
                .replace("задача:", "")
                .replace("Ответ:", "")
                .replace("ответ:", "")
                .replace("Решение:", "")
                .replace("решение:", "");

        // Убираем нумерацию только в начале строк (например: "1. ..."),
        // чтобы не ломать дроби вида "3/4." (раньше удалялось "4." и получалось "3/")
        t = t.replaceAll("(?m)^\\s*\\d+\\.\\s*", "");

        t = t.replaceAll("\\n", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return t;
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