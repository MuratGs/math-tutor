package org.example.mathlearning.controller;

import org.example.mathlearning.model.TaskHistory;
import org.example.mathlearning.model.TopicNode;
import org.example.mathlearning.model.User;
import org.example.mathlearning.repository.TaskHistoryRepository;
import org.example.mathlearning.repository.TopicNodeRepository;
import org.example.mathlearning.repository.UserRepository;
import org.example.mathlearning.service.AnalyticsService;
import org.example.mathlearning.service.OllamaService;
import org.example.mathlearning.service.StandardTaskService;
import org.example.mathlearning.service.TaskReportService;
import org.example.mathlearning.service.TopicGraphService;
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

    @Autowired
    private TopicNodeRepository topicNodeRepository;

    @Autowired
    private StandardTaskService standardTaskService;

    @Autowired
    private TaskReportService taskReportService;

    @Autowired
    private TopicGraphService topicGraphService;

    // Главная страница
    @GetMapping("/")
    public String index(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }
        return "index";
    }

    @GetMapping("/topic-select")
    public String topicSelect(@RequestParam(value = "discipline", required = false) String discipline,
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

        List<String> disciplines = Arrays.asList("Алгебра", "Прикладная математика", "Геометрия");
        String selectedDiscipline = discipline;
        if (selectedDiscipline == null || selectedDiscipline.trim().isEmpty()) {
            selectedDiscipline = "Алгебра";
        }

        List<TopicNode> allActive = topicNodeRepository.findByStubFalseAndActiveTrueOrderByDisplayNameAsc();
        if (allActive == null) {
            allActive = Collections.emptyList();
        }

        Map<String, List<TopicNode>> topicsByDiscipline = new LinkedHashMap<>();
        for (String d : disciplines) {
            topicsByDiscipline.put(d, new ArrayList<>());
        }
        for (TopicNode n : allActive) {
            if (n == null) {
                continue;
            }
            String d = n.getDiscipline();
            if (d == null || d.trim().isEmpty()) {
                d = "Алгебра";
            }
            topicsByDiscipline.computeIfAbsent(d, k -> new ArrayList<>()).add(n);
        }

        Map<String, Map<String, Object>> topicsPerformance = analyticsService.getTopicsPerformance(userId);
        if (topicsPerformance == null) {
            topicsPerformance = Collections.emptyMap();
        }

        model.addAttribute("disciplines", disciplines);
        model.addAttribute("selectedDiscipline", selectedDiscipline);
        model.addAttribute("topicsByDiscipline", topicsByDiscipline);
        model.addAttribute("topics", topicsByDiscipline.getOrDefault(selectedDiscipline, Collections.emptyList()));
        model.addAttribute("topicsPerformance", topicsPerformance);
        model.addAttribute("selectedTopic", session.getAttribute("selectedTopic"));
        model.addAttribute("userLevel", user.getCurrentLevel());

        return "topic-select";
    }

    @PostMapping("/topic-select")
    public String topicSelectPost(@RequestParam("topicKey") String topicKey,
                                  HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        if (topicKey == null || topicKey.trim().isEmpty()) {
            return "redirect:/topic-select";
        }

        String key = topicKey.trim();
        session.setAttribute("selectedTopic", key);
        session.setAttribute("lastTopicGroup", key);

        session.setAttribute("topicState", "MAIN_TOPIC");
        session.setAttribute("mainTopic", key);
        session.setAttribute("relatedTopicsQueue", new ArrayList<String>());
        session.setAttribute("exploringTopic", null);
        session.setAttribute("tasksOnRelatedTopic", 0);
        session.removeAttribute("topicSwitchMessage");

        session.setAttribute("skipResumeInProgress", true);
        session.removeAttribute("currentTask");
        session.removeAttribute("attempts");
        session.removeAttribute("currentTopicGroup");
        session.removeAttribute("currentStandardTaskId");
        session.removeAttribute("currentTaskTopic");
        session.removeAttribute("currentTaskGrade");
        session.removeAttribute("currentTaskDifficulty");
        session.removeAttribute("currentTaskHint");

        return "redirect:/learn";
    }

    // Страница обучения
    @GetMapping("/learn")
    public String learn(@RequestParam(required = false) String topic,
                        @RequestParam(required = false) Boolean newTask,
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

        String topicState = (String) session.getAttribute("topicState");
        if (topicState == null || topicState.trim().isEmpty()) {
            topicState = "MAIN_TOPIC";
            session.setAttribute("topicState", "MAIN_TOPIC");
        }

        String selectedTopic = (String) session.getAttribute("selectedTopic");
        String mainTopicInSession = (String) session.getAttribute("mainTopic");

        if (selectedTopic != null && !selectedTopic.trim().isEmpty()) {
            topic = selectedTopic;
        }

        if ((selectedTopic == null || selectedTopic.trim().isEmpty()) && mainTopicInSession != null && !mainTopicInSession.trim().isEmpty()) {
            topic = mainTopicInSession;
        }

        String topicSwitchMessage = (String) session.getAttribute("topicSwitchMessage");
        if (topicSwitchMessage != null && !topicSwitchMessage.trim().isEmpty()) {
            model.addAttribute("topicSwitchMessage", topicSwitchMessage);
            session.removeAttribute("topicSwitchMessage");
        }

        if ("EXPLORING_RELATED".equals(topicState)) {
            ensureExploringTopicSelected(session);
            String exploringTopic = (String) session.getAttribute("exploringTopic");
            if (exploringTopic != null && !exploringTopic.isBlank()) {
                topic = exploringTopic;
                session.setAttribute("lastTopicGroup", topic);
            }
        }

        if (topic == null || topic.trim().isEmpty()) {
            String last = (String) session.getAttribute("lastTopicGroup");
            topic = (last == null || last.trim().isEmpty()) ? "algebra" : last;
        }
        session.setAttribute("lastTopicGroup", topic);

        // Получаем рекомендацию от аналитики
        int recommendedLevel = analyticsService.recommendNextLevel(userId);
        session.setAttribute("recommendedLevel", recommendedLevel);
        session.setAttribute("userLevel", user.getCurrentLevel());

        String sessionId = session.getId();

        log.info("===== ГЕНЕРАЦИЯ ЗАДАЧИ ДЛЯ ПОЛЬЗОВАТЕЛЯ {} =====", userId);
        log.info("Тема: {}, Текущий уровень: {}, Рекомендованный уровень: {}", topic, user.getCurrentLevel(), recommendedLevel);

        String currentTask = (String) session.getAttribute("currentTask");
        String currentTopicGroup = (String) session.getAttribute("currentTopicGroup");
        String currentTaskTopic = (String) session.getAttribute("currentTaskTopic");
        String currentTaskHint = (String) session.getAttribute("currentTaskHint");
        String lastTask = (String) session.getAttribute("lastTask");
        boolean requestNewTask = Boolean.TRUE.equals(newTask);

        Boolean skipResumeInProgress = (Boolean) session.getAttribute("skipResumeInProgress");
        if (Boolean.TRUE.equals(skipResumeInProgress)) {
            session.removeAttribute("skipResumeInProgress");
        }

        if (!requestNewTask && !Boolean.TRUE.equals(skipResumeInProgress) && (currentTask == null || currentTask.trim().isEmpty())) {
            Optional<TaskHistory> inProgress = taskHistoryRepository.findTopByUser_IdAndSolvedFalseAndAbandonedFalseOrderByCreatedAtDesc(userId);
            if (inProgress.isPresent() && inProgress.get().getTask() != null) {
                String taskText = inProgress.get().getTaskText();
                if (taskText != null && !taskText.trim().isEmpty()) {
                    session.setAttribute("currentTask", taskText);
                    session.setAttribute("currentTopicGroup", topic);
                    session.setAttribute("currentStandardTaskId", null);
                    session.setAttribute("currentTaskTopic", inProgress.get().getTopic());
                    session.setAttribute("currentTaskGrade", null);
                    session.setAttribute("currentTaskDifficulty", inProgress.get().getLevel());
                    session.setAttribute("currentTaskHint", null);
                    if (session.getAttribute("attempts") == null) {
                        session.setAttribute("attempts", 0);
                    }

                    model.addAttribute("task", taskText);
                    model.addAttribute("topic", topic);
                    model.addAttribute("topicDisplay", StandardTaskService.getTopicDisplayName(topic));
                    model.addAttribute("taskTopic", inProgress.get().getTopic());
                    model.addAttribute("taskTopicDisplay", StandardTaskService.getTopicDisplayName(inProgress.get().getTopic()));
                    model.addAttribute("taskHint", null);
                    model.addAttribute("level", user.getCurrentLevel());
                    model.addAttribute("recommendedLevel", recommendedLevel);
                    model.addAttribute("attempts", session.getAttribute("attempts"));
                    return "learn";
                }
            }
        }

        Boolean retrySimilarGeneration = (Boolean) session.getAttribute("retrySimilarGeneration");
        if (Boolean.TRUE.equals(retrySimilarGeneration)) {
            String baseStatement = (String) session.getAttribute("retrySimilarBaseStatement");
            String forcedTopic = (String) session.getAttribute("retrySimilarTopic");
            Integer forcedGrade = (Integer) session.getAttribute("retrySimilarGrade");
            Integer forcedDifficulty = (Integer) session.getAttribute("retrySimilarDifficulty");

            String topicForSimilar = inferTopicForSimilar(baseStatement, forcedTopic, topic);
            Integer gradeForSimilar = forcedGrade != null ? forcedGrade : inferGradeFromLevel(user.getCurrentLevel());
            Integer difficultyForSimilar = forcedDifficulty != null ? forcedDifficulty : (user.getCurrentLevel() != null ? user.getCurrentLevel() : 1);

            String generated = null;
            int maxAttempts = 6;
            Set<String> usedInSession = loadSessionTaskFingerprints(sessionId);
            for (int i = 0; i < maxAttempts; i++) {
                String candidate = null;
                try {
                    if (i < 4) {
                        candidate = ollamaService.generateSimilarStandardTask(topicForSimilar, gradeForSimilar, difficultyForSimilar, baseStatement);
                    } else {
                        candidate = ollamaService.generateNewStandardTask(topicForSimilar, gradeForSimilar, difficultyForSimilar);
                    }
                } catch (Exception ex) {
                    log.warn("Не удалось сгенерировать задачу (retry): {}", ex.getMessage());
                }

                if (candidate == null) {
                    continue;
                }
                candidate = candidate.trim();
                if (candidate.isEmpty()) {
                    continue;
                }

                if (baseStatement != null && areTasksEquivalent(candidate, baseStatement)) {
                    continue;
                }
                if (currentTask != null && areTasksEquivalent(candidate, currentTask)) {
                    continue;
                }
                if (lastTask != null && areTasksEquivalent(candidate, lastTask)) {
                    continue;
                }
                if (usedInSession.contains(normalizeTaskForDedup(candidate))) {
                    continue;
                }
                if (taskHistoryRepository.isTaskSolvedByUser(userId, candidate)) {
                    continue;
                }

                generated = candidate;
                break;
            }

            if (generated == null || generated.trim().isEmpty()) {
                model.addAttribute("error", "Не удалось сгенерировать похожую задачу. Проверь, что Ollama запущена, и попробуй ещё раз.");
                return "error";
            }

            standardTaskService.registerAdHocTaskInHistory(user, sessionId, topicForSimilar, difficultyForSimilar, generated);

            session.setAttribute("currentTask", generated.trim());
            session.setAttribute("currentTopicGroup", topic);
            session.setAttribute("currentStandardTaskId", null);
            session.setAttribute("currentTaskTopic", topicForSimilar);
            session.setAttribute("currentTaskGrade", gradeForSimilar);
            session.setAttribute("currentTaskDifficulty", difficultyForSimilar);
            session.setAttribute("currentTaskHint", null);
            session.removeAttribute("retrySimilarGeneration");
            session.removeAttribute("retrySimilarBaseStatement");
            session.removeAttribute("retrySimilarTopic");
            session.removeAttribute("retrySimilarGrade");
            session.removeAttribute("retrySimilarDifficulty");

            model.addAttribute("task", generated.trim());
            model.addAttribute("topic", topic);
            model.addAttribute("topicDisplay", StandardTaskService.getTopicDisplayName(topic));
            model.addAttribute("taskTopic", topicForSimilar);
            model.addAttribute("taskTopicDisplay", StandardTaskService.getTopicDisplayName(topicForSimilar));
            model.addAttribute("taskHint", null);
            model.addAttribute("level", user.getCurrentLevel());
            model.addAttribute("recommendedLevel", recommendedLevel);
            model.addAttribute("attempts", session.getAttribute("attempts"));
            return "learn";
        }

        if (!requestNewTask && currentTask != null && !currentTask.isEmpty() && currentTopicGroup != null && currentTopicGroup.equals(topic)) {
            log.info("Используем существующую задачу для повторной попытки");
            model.addAttribute("task", currentTask);
            model.addAttribute("topic", topic);
            model.addAttribute("topicDisplay", StandardTaskService.getTopicDisplayName(topic));
            model.addAttribute("taskTopic", currentTaskTopic);
            model.addAttribute("taskTopicDisplay", StandardTaskService.getTopicDisplayName(currentTaskTopic));
            model.addAttribute("taskHint", currentTaskHint);
            model.addAttribute("level", user.getCurrentLevel());
            model.addAttribute("recommendedLevel", recommendedLevel);
            model.addAttribute("attempts", session.getAttribute("attempts"));
            return "learn";
        }

        try {
            String requestedTopicForTask = resolveTaskTopicForLearn(session, topic);

            Long overrideStandardTaskId = (Long) session.getAttribute("nextStandardTaskId");

            Boolean forceSimilarGeneration = (Boolean) session.getAttribute("forceSimilarGeneration");
            if (overrideStandardTaskId == null && Boolean.TRUE.equals(forceSimilarGeneration)) {
                String forcedBaseStatement = (String) session.getAttribute("forceSimilarBaseStatement");
                String forcedTopic = (String) session.getAttribute("forceSimilarTopic");
                Integer forcedGrade = (Integer) session.getAttribute("forceSimilarGrade");
                Integer forcedDifficulty = (Integer) session.getAttribute("forceSimilarDifficulty");

                String topicForSimilar = inferTopicForSimilar(forcedBaseStatement, forcedTopic, topic);
                Integer gradeForSimilar = forcedGrade != null ? forcedGrade : inferGradeFromLevel(user.getCurrentLevel());
                Integer difficultyForSimilar = forcedDifficulty != null ? forcedDifficulty : (user.getCurrentLevel() != null ? user.getCurrentLevel() : 1);

                int maxAttempts = 6;
                Set<String> usedInSession = loadSessionTaskFingerprints(sessionId);
                for (int i = 0; i < maxAttempts && overrideStandardTaskId == null; i++) {
                    String candidate = null;
                    try {
                        if (i < 4) {
                            candidate = ollamaService.generateSimilarStandardTask(topicForSimilar, gradeForSimilar, difficultyForSimilar, forcedBaseStatement);
                        } else {
                            candidate = ollamaService.generateNewStandardTask(topicForSimilar, gradeForSimilar, difficultyForSimilar);
                        }
                    } catch (Exception ex) {
                        log.warn("Не удалось сгенерировать задачу (retry в /learn): {}", ex.getMessage());
                    }

                    if (candidate == null) {
                        continue;
                    }
                    candidate = candidate.trim();
                    if (candidate.isEmpty()) {
                        continue;
                    }
                    if (forcedBaseStatement != null && areTasksEquivalent(candidate, forcedBaseStatement)) {
                        continue;
                    }
                    if (lastTask != null && areTasksEquivalent(candidate, lastTask)) {
                        continue;
                    }
                    if (usedInSession.contains(normalizeTaskForDedup(candidate))) {
                        continue;
                    }
                    if (taskHistoryRepository.isTaskSolvedByUser(userId, candidate)) {
                        continue;
                    }

                    var stored = standardTaskService.storeAiGeneratedStandardTask(topicForSimilar, gradeForSimilar, difficultyForSimilar, candidate);
                    if (stored != null && stored.getId() != null) {
                        overrideStandardTaskId = stored.getId();
                        session.setAttribute("nextStandardTaskId", stored.getId());
                        session.removeAttribute("forceSimilarGeneration");
                        session.removeAttribute("forceSimilarBaseStatement");
                        session.removeAttribute("forceSimilarTopic");
                        session.removeAttribute("forceSimilarGrade");
                        session.removeAttribute("forceSimilarDifficulty");
                        log.info("🤖 Сгенерирована новая похожая задача (retry в /learn) и добавлена в стандартную базу: id={}", stored.getId());
                    }
                }
            }

            if (overrideStandardTaskId == null && Boolean.TRUE.equals(forceSimilarGeneration)) {
                log.warn("Не удалось сгенерировать новую задачу после 3 попыток: Ollama недоступна или генерация не удалась");
                model.addAttribute("error", "Не удалось сгенерировать новую задачу. Проверь, что Ollama запущена, и попробуй ещё раз.");
                return "error";
            }

            // Явный запрос "Новая задача" должен генерировать новую задачу через Ollama как раньше,
            // а не выбирать следующую из готовой стандартной базы.
            if (overrideStandardTaskId == null && Boolean.TRUE.equals(newTask)) {
                String specificTopic = requestedTopicForTask;
                String stateForNewTask = (String) session.getAttribute("topicState");
                if (!"EXPLORING_RELATED".equals(stateForNewTask)) {
                    specificTopic = (String) session.getAttribute("currentTaskTopic");
                    if (specificTopic == null || specificTopic.isBlank()) {
                        specificTopic = (String) session.getAttribute("selectedTopic");
                    }
                    if (specificTopic == null || specificTopic.isBlank()) {
                        specificTopic = requestedTopicForTask;
                    }
                }

                String standardTopic = (specificTopic != null && !specificTopic.isBlank()
                        && !"algebra".equalsIgnoreCase(specificTopic)
                        && !"geometry".equalsIgnoreCase(specificTopic))
                        ? specificTopic
                        : standardTaskService.chooseStandardTopicForUser(userId, requestedTopicForTask);
                Integer grade = inferGradeFromLevel(user.getCurrentLevel());
                Integer difficulty = user.getCurrentLevel() != null ? user.getCurrentLevel() : 1;

                int maxAttempts = 6;
                Set<String> usedInSession = loadSessionTaskFingerprints(sessionId);
                for (int i = 0; i < maxAttempts && overrideStandardTaskId == null; i++) {
                    String candidateTopic = standardTopic;
                    String candidate = null;
                    try {
                        candidate = ollamaService.generateNewStandardTask(candidateTopic, grade, difficulty);
                    } catch (Exception ex) {
                        log.warn("Не удалось сгенерировать новую задачу (newTask): topic={} grade={} difficulty={} attempt={}/{}",
                                candidateTopic, grade, difficulty, i + 1, maxAttempts, ex);
                    }

                    if (candidate == null) {
                        log.warn("⚠️ newTask: Ollama вернула null/невалидную задачу: topic={} grade={} difficulty={} attempt={}/{}",
                                candidateTopic, grade, difficulty, i + 1, maxAttempts);
                        continue;
                    }
                    candidate = candidate.trim();
                    if (candidate.isEmpty()) {
                        log.warn("⚠️ newTask: отклонена пустая задача: topic={} grade={} difficulty={} attempt={}/{}",
                                candidateTopic, grade, difficulty, i + 1, maxAttempts);
                        continue;
                    }
                    if (currentTask != null && areTasksEquivalent(candidate, currentTask)) {
                        log.warn("⚠️ newTask: отклонён дубль текущей задачи: topic={} attempt={} task='{}'",
                                candidateTopic, i + 1, normalizeTaskForDedup(candidate));
                        continue;
                    }
                    if (lastTask != null && areTasksEquivalent(candidate, lastTask)) {
                        log.warn("⚠️ newTask: отклонён дубль последней задачи: topic={} attempt={} task='{}'",
                                candidateTopic, i + 1, normalizeTaskForDedup(candidate));
                        continue;
                    }
                    if (taskHistoryRepository.existsBySessionIdAndTask_TaskText(sessionId, candidate)) {
                        log.warn("⚠️ newTask: отклонён дубль в текущей сессии: topic={} attempt={} task='{}'",
                                candidateTopic, i + 1, normalizeTaskForDedup(candidate));
                        continue;
                    }
                    if (taskHistoryRepository.isTaskSolvedByUser(userId, candidate)) {
                        log.warn("⚠️ newTask: отклонена уже решённая пользователем задача: userId={} topic={} attempt={} task='{}'",
                                userId, candidateTopic, i + 1, normalizeTaskForDedup(candidate));
                        continue;
                    }

                    var stored = standardTaskService.storeAiGeneratedStandardTask(candidateTopic, grade, difficulty, candidate);
                    if (stored != null && stored.getId() != null) {
                        overrideStandardTaskId = stored.getId();
                    } else {
                        log.warn("⚠️ newTask: задача не сохранилась в standard tasks: topic={} grade={} difficulty={} attempt={} task='{}'",
                                candidateTopic, grade, difficulty, i + 1, normalizeTaskForDedup(candidate));
                    }
                }

                if (overrideStandardTaskId == null) {
                    log.warn("⚠️ newTask: после {} попыток не удалось создать AI-задачу, продолжаю обычную выдачу: topic={} standardTopic={} grade={} difficulty={}",
                            maxAttempts, requestedTopicForTask, standardTopic, grade, difficulty);
                }
            }

            var standardTask = standardTaskService.deliverNextStandardTask(user, sessionId, requestedTopicForTask, recommendedLevel, overrideStandardTaskId);
            if (standardTask != null) {
                String task = standardTask.getStatement();
                String src = Boolean.TRUE.equals(standardTask.getAiGenerated()) ? "🤖" : "📚";
                log.info("{} Выбрана задача из стандартной базы: {}", src, task);

                session.setAttribute("currentTask", task);
                session.setAttribute("currentTopicGroup", topic);
                session.setAttribute("currentStandardTaskId", standardTask.getId());
                session.setAttribute("currentTaskTopic", standardTask.getTopic());
                session.setAttribute("currentTaskGrade", standardTask.getGrade());
                session.setAttribute("currentTaskDifficulty", standardTask.getDifficulty());
                session.setAttribute("currentTaskHint", standardTask.getHint());
                session.setAttribute("attempts", 0);
                session.removeAttribute("nextStandardTaskId");

                model.addAttribute("task", task);
                model.addAttribute("topic", topic);
                model.addAttribute("topicDisplay", StandardTaskService.getTopicDisplayName(topic));
                model.addAttribute("taskTopic", standardTask.getTopic());
                model.addAttribute("taskTopicDisplay", StandardTaskService.getTopicDisplayName(standardTask.getTopic()));
                model.addAttribute("taskHint", standardTask.getHint());
                model.addAttribute("level", user.getCurrentLevel());
                model.addAttribute("recommendedLevel", recommendedLevel);
                model.addAttribute("attempts", 0);

                return "learn";
            }

            String fallbackTopic = requestedTopicForTask;
            if (fallbackTopic == null || fallbackTopic.isBlank()
                    || "algebra".equalsIgnoreCase(fallbackTopic)
                    || "geometry".equalsIgnoreCase(fallbackTopic)) {
                fallbackTopic = standardTaskService.chooseStandardTopicForUser(userId, requestedTopicForTask, null);
            }
            if (fallbackTopic == null || fallbackTopic.trim().isEmpty()) {
                fallbackTopic = requestedTopicForTask;
            }

            String task = ollamaService.generateTask(fallbackTopic, user.getCurrentLevel(), sessionId, userId);
            log.info("Сгенерированная задача (fallback): {}", task);

            session.setAttribute("currentTask", task);
            session.setAttribute("currentTopicGroup", topic);
            session.setAttribute("currentStandardTaskId", null);
            session.setAttribute("currentTaskTopic", fallbackTopic);
            session.setAttribute("currentTaskGrade", null);
            session.setAttribute("currentTaskDifficulty", user.getCurrentLevel());
            session.setAttribute("currentTaskHint", null);
            session.setAttribute("attempts", 0);

            model.addAttribute("task", task);
            model.addAttribute("topic", topic);
            model.addAttribute("topicDisplay", StandardTaskService.getTopicDisplayName(topic));
            model.addAttribute("taskTopic", fallbackTopic);
            model.addAttribute("taskTopicDisplay", StandardTaskService.getTopicDisplayName(fallbackTopic));
            model.addAttribute("taskHint", null);
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
        boolean isCorrect = isCorrectCheckResult(result);

        log.info("Результат проверки: {} (правильно: {})", result, isCorrect);

        session.setAttribute("lastTask", task);
        session.setAttribute("lastAnswer", answer);
        session.setAttribute("lastResult", result);
        session.setAttribute("lastIsCorrect", isCorrect);
        session.setAttribute("lastStandardTaskId", session.getAttribute("currentStandardTaskId"));
        session.setAttribute("lastTaskTopic", session.getAttribute("currentTaskTopic"));
        session.setAttribute("lastTaskGrade", session.getAttribute("currentTaskGrade"));
        session.setAttribute("lastTaskDifficulty", session.getAttribute("currentTaskDifficulty"));
        session.setAttribute("lastTaskHint", session.getAttribute("currentTaskHint"));

        userService.updateUserStats(userId, isCorrect);
        user = userRepository.findById(userId).orElse(user);

        if (isCorrect) {
            ollamaService.markTaskAsSolved(sessionId, task, userId);

            String topicState = (String) session.getAttribute("topicState");
            if (topicState == null || topicState.isBlank()) {
                topicState = "MAIN_TOPIC";
            }

            boolean wasExploring = "EXPLORING_RELATED".equals(topicState);
            String solvedTopic = (String) session.getAttribute("currentTaskTopic");
            if (wasExploring) {
                wrongInRow = 0;
                correctInRow = 0;
                session.setAttribute("wrongInRow", 0);
                session.setAttribute("correctInRow", 0);

                String mainTopic = (String) session.getAttribute("mainTopic");
                session.setAttribute("topicState", "MAIN_TOPIC");
                session.setAttribute("exploringTopic", null);
                session.setAttribute("relatedTopicsQueue", new ArrayList<String>());
                session.setAttribute("tasksOnRelatedTopic", 0);
                if (mainTopic != null && !mainTopic.isBlank()) {
                    session.setAttribute("lastTopicGroup", mainTopic);
                    session.setAttribute("selectedTopic", mainTopic);
                }

                result += " Отлично! Возвращаемся к основной теме: " + StandardTaskService.getTopicDisplayName(mainTopic);
            } else {
                correctInRow++;
                wrongInRow = 0;
                session.setAttribute("correctInRow", correctInRow);
                session.setAttribute("wrongInRow", wrongInRow);
                if (solvedTopic != null && !solvedTopic.isBlank()
                        && !"algebra".equalsIgnoreCase(solvedTopic)
                        && !"geometry".equalsIgnoreCase(solvedTopic)) {
                    session.setAttribute("selectedTopic", solvedTopic);
                    session.setAttribute("lastTopicGroup", solvedTopic);
                }
            }

            session.removeAttribute("currentTask");
            session.removeAttribute("attempts");
            session.removeAttribute("currentTopicGroup");
            session.removeAttribute("currentStandardTaskId");
            session.removeAttribute("currentTaskTopic");
            session.removeAttribute("currentTaskGrade");
            session.removeAttribute("currentTaskDifficulty");
            session.removeAttribute("currentTaskHint");

            if (!wasExploring) {
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
            }

            model.addAttribute("result", result);
            model.addAttribute("task", task);
            model.addAttribute("answer", answer);
            model.addAttribute("level", user.getCurrentLevel());
            model.addAttribute("isCorrect", true);
            model.addAttribute("taskHint", session.getAttribute("lastTaskHint"));
            model.addAttribute("standardTaskId", session.getAttribute("lastStandardTaskId"));
            model.addAttribute("taskTopic", session.getAttribute("lastTaskTopic"));
            model.addAttribute("taskTopicDisplay", StandardTaskService.getTopicDisplayName((String) session.getAttribute("lastTaskTopic")));

            session.removeAttribute("lastAttemptsLeft");
            session.removeAttribute("lastNewTask");

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
            }

            if (wrongInRow >= 3) {
                session.setAttribute("wrongInRow", 0);

                String currentTopicForGraph = (String) session.getAttribute("currentTaskTopic");
                String topicState = (String) session.getAttribute("topicState");
                if (topicState == null || topicState.isBlank()) {
                    topicState = "MAIN_TOPIC";
                }

                if (currentTopicForGraph != null && !currentTopicForGraph.isBlank()) {
                    if ("EXPLORING_RELATED".equals(topicState)) {
                        ArrayList<String> queue = getRelatedTopicsQueue(session);
                        if (!queue.isEmpty()) {
                            String nextRelated = queue.remove(0);
                            session.setAttribute("relatedTopicsQueue", new ArrayList<String>(queue));
                            session.setAttribute("exploringTopic", nextRelated);
                            session.setAttribute("tasksOnRelatedTopic", 0);
                            clearCurrentTask(session);
                            taskHistoryRepository.abandonAllInProgressTasks(userId);
                            session.setAttribute("skipResumeInProgress", true);
                            String switchMsg = "Ты часто ошибаешься. Переходим на следующую связанную тему: " +
                                    StandardTaskService.getTopicDisplayName(nextRelated);
                            return buildSwitchResult(model, switchMsg, task, answer, user, session, true,
                                    StandardTaskService.getTopicDisplayName(nextRelated));
                        } else {
                            String mainTopic = (String) session.getAttribute("mainTopic");
                            session.setAttribute("topicState", "MAIN_TOPIC");
                            session.setAttribute("exploringTopic", null);
                            session.setAttribute("relatedTopicsQueue", new ArrayList<String>());
                            session.setAttribute("tasksOnRelatedTopic", 0);
                            if (mainTopic != null && !mainTopic.isBlank()) {
                                session.setAttribute("lastTopicGroup", mainTopic);
                            }
                            clearCurrentTask(session);
                            taskHistoryRepository.abandonAllInProgressTasks(userId);
                            session.setAttribute("skipResumeInProgress", true);
                            String switchMsg = "Связанные темы закончились. Возвращаемся к основной теме: " +
                                    StandardTaskService.getTopicDisplayName(mainTopic);
                            return buildSwitchResult(model, switchMsg, task, answer, user, session, false, null);
                        }
                    } else {
                        List<String> related = topicGraphService.getRelatedTopics(currentTopicForGraph);
                        if (related != null && !related.isEmpty()) {
                            session.setAttribute("topicState", "EXPLORING_RELATED");
                            session.setAttribute("mainTopic", currentTopicForGraph);
                            session.setAttribute("exploringTopic", related.get(0));
                            ArrayList<String> queue = related.size() > 1
                                    ? new ArrayList<String>(related.subList(1, related.size()))
                                    : new ArrayList<String>();
                            session.setAttribute("relatedTopicsQueue", queue);
                            session.setAttribute("tasksOnRelatedTopic", 0);
                            clearCurrentTask(session);
                            taskHistoryRepository.abandonAllInProgressTasks(userId);
                            session.setAttribute("skipResumeInProgress", true);
                            String switchMsg = "Ты часто ошибаешься. Переходим на связанную тему: " +
                                    StandardTaskService.getTopicDisplayName(related.get(0));
                            return buildSwitchResult(model, switchMsg, task, answer, user, session, true,
                                    StandardTaskService.getTopicDisplayName(related.get(0)));
                        }
                    }
                }
            }

            if (attempts >= 3) {
                result = "❌ К сожалению, ты не справился с задачей после 3 попыток. " +
                        "Попробуй решить другую задачу того же уровня.";

                Long standardTaskId = (Long) session.getAttribute("currentStandardTaskId");
                String baseTopic = (String) session.getAttribute("currentTaskTopic");
                String baseTopicGroup = (String) session.getAttribute("currentTopicGroup");
                Integer baseGrade = (Integer) session.getAttribute("currentTaskGrade");
                Integer baseDifficulty = (Integer) session.getAttribute("currentTaskDifficulty");

                String topicForSimilar = inferTopicForSimilar(task, baseTopic, baseTopicGroup);
                Integer gradeForSimilar = baseGrade != null ? baseGrade : inferGradeFromLevel(user.getCurrentLevel());
                Integer difficultyForSimilar = baseDifficulty != null ? baseDifficulty : (user.getCurrentLevel() != null ? user.getCurrentLevel() : 1);

                session.setAttribute("forceSimilarGeneration", true);
                session.setAttribute("forceSimilarBaseStatement", task);
                session.setAttribute("forceSimilarTopic", topicForSimilar);
                session.setAttribute("forceSimilarGrade", gradeForSimilar);
                session.setAttribute("forceSimilarDifficulty", difficultyForSimilar);

                if (topicForSimilar != null && gradeForSimilar != null && difficultyForSimilar != null) {
                    int maxAttempts = 6;
                    for (int i = 0; i < maxAttempts; i++) {
                        Set<String> usedInSession = loadSessionTaskFingerprints(sessionId);
                        String candidate = null;
                        try {
                            if (i < 4) {
                                candidate = ollamaService.generateSimilarStandardTask(topicForSimilar, gradeForSimilar, difficultyForSimilar, task);
                            } else {
                                candidate = ollamaService.generateNewStandardTask(topicForSimilar, gradeForSimilar, difficultyForSimilar);
                            }
                        } catch (Exception ex) {
                            log.warn("Не удалось сгенерировать задачу после 3 попыток: {}", ex.getMessage());
                        }

                        if (candidate == null) {
                            continue;
                        }
                        candidate = candidate.trim();
                        if (candidate.isEmpty()) {
                            continue;
                        }

                        if (areTasksEquivalent(candidate, task)) {
                            continue;
                        }
                        if (usedInSession.contains(normalizeTaskForDedup(candidate))) {
                            continue;
                        }
                        if (taskHistoryRepository.isTaskSolvedByUser(userId, candidate)) {
                            continue;
                        }

                        var stored = standardTaskService.storeAiGeneratedStandardTask(topicForSimilar, gradeForSimilar, difficultyForSimilar, candidate);
                        if (stored != null && stored.getId() != null) {
                            session.setAttribute("nextStandardTaskId", stored.getId());
                            session.removeAttribute("forceSimilarGeneration");
                            session.removeAttribute("forceSimilarBaseStatement");
                            session.removeAttribute("forceSimilarTopic");
                            session.removeAttribute("forceSimilarGrade");
                            session.removeAttribute("forceSimilarDifficulty");
                            log.info("🤖 Сгенерирована похожая задача и добавлена в стандартную базу: id={}", stored.getId());
                            break;
                        }
                    }
                }

                session.removeAttribute("currentTask");
                session.removeAttribute("attempts");
                session.removeAttribute("currentTopicGroup");
                session.removeAttribute("currentStandardTaskId");
                session.removeAttribute("currentTaskTopic");
                session.removeAttribute("currentTaskGrade");
                session.removeAttribute("currentTaskDifficulty");
                session.removeAttribute("currentTaskHint");

                model.addAttribute("result", result);
                model.addAttribute("task", task);
                model.addAttribute("answer", answer);
                model.addAttribute("level", user.getCurrentLevel());
                model.addAttribute("isCorrect", false);
                model.addAttribute("newTask", true);
                model.addAttribute("taskHint", session.getAttribute("lastTaskHint"));
                model.addAttribute("standardTaskId", session.getAttribute("lastStandardTaskId"));
                model.addAttribute("taskTopic", session.getAttribute("lastTaskTopic"));
                model.addAttribute("taskTopicDisplay", StandardTaskService.getTopicDisplayName((String) session.getAttribute("lastTaskTopic")));

                session.setAttribute("lastNewTask", true);
                session.removeAttribute("lastAttemptsLeft");
            } else {
                result += " Попробуй еще раз. Осталось попыток: " + (3 - attempts);

                String baseTopic = (String) session.getAttribute("currentTaskTopic");
                String baseTopicGroup = (String) session.getAttribute("currentTopicGroup");
                Integer baseGrade = (Integer) session.getAttribute("currentTaskGrade");
                Integer baseDifficulty = (Integer) session.getAttribute("currentTaskDifficulty");

                String topicForSimilar = inferTopicForSimilar(task, baseTopic, baseTopicGroup);
                Integer gradeForSimilar = baseGrade != null ? baseGrade : inferGradeFromLevel(user.getCurrentLevel());
                Integer difficultyForSimilar = baseDifficulty != null ? baseDifficulty : (user.getCurrentLevel() != null ? user.getCurrentLevel() : 1);

                session.setAttribute("retrySimilarGeneration", true);
                session.setAttribute("retrySimilarBaseStatement", task);
                session.setAttribute("retrySimilarTopic", topicForSimilar);
                session.setAttribute("retrySimilarGrade", gradeForSimilar);
                session.setAttribute("retrySimilarDifficulty", difficultyForSimilar);

                model.addAttribute("result", result);
                model.addAttribute("task", task);
                model.addAttribute("answer", answer);
                model.addAttribute("level", user.getCurrentLevel());
                model.addAttribute("isCorrect", false);
                model.addAttribute("attemptsLeft", 3 - attempts);
                model.addAttribute("taskHint", session.getAttribute("lastTaskHint"));
                model.addAttribute("standardTaskId", session.getAttribute("lastStandardTaskId"));
                model.addAttribute("taskTopic", session.getAttribute("lastTaskTopic"));
                model.addAttribute("taskTopicDisplay", StandardTaskService.getTopicDisplayName((String) session.getAttribute("lastTaskTopic")));

                session.setAttribute("lastAttemptsLeft", 3 - attempts);
                session.removeAttribute("lastNewTask");
            }
        }

        return "result";
    }

    private String resolveTaskTopicForLearn(HttpSession session, String mainTopic) {
        String state = (String) session.getAttribute("topicState");
        if (!"EXPLORING_RELATED".equals(state)) {
            return mainTopic;
        }

        ensureExploringTopicSelected(session);
        String exploring = (String) session.getAttribute("exploringTopic");
        if (exploring == null || exploring.trim().isEmpty()) {
            return mainTopic;
        }
        return exploring;
    }

    @SuppressWarnings("unchecked")
    private ArrayList<String> getRelatedTopicsQueue(HttpSession session) {
        Object q = session.getAttribute("relatedTopicsQueue");
        if (q instanceof List) {
            return new ArrayList<>((List<String>) q);
        }
        return new ArrayList<>();
    }

    private void ensureExploringTopicSelected(HttpSession session) {
        String exploringTopic = (String) session.getAttribute("exploringTopic");
        if (exploringTopic != null && !exploringTopic.trim().isEmpty()) {
            return;
        }

        ArrayList<String> queue = getRelatedTopicsQueue(session);
        if (queue.isEmpty()) {
            session.setAttribute("topicState", "MAIN_TOPIC");
            session.setAttribute("exploringTopic", null);
            session.setAttribute("tasksOnRelatedTopic", 0);
            session.setAttribute("relatedTopicsQueue", new ArrayList<String>());
            return;
        }

        session.setAttribute("exploringTopic", queue.get(0));
        if (session.getAttribute("tasksOnRelatedTopic") == null) {
            session.setAttribute("tasksOnRelatedTopic", 0);
        }
    }

    private void advanceExploringProgress(HttpSession session) {
        String state = (String) session.getAttribute("topicState");
        if (!"EXPLORING_RELATED".equals(state)) {
            return;
        }

        Integer count = (Integer) session.getAttribute("tasksOnRelatedTopic");
        if (count == null) {
            count = 0;
        }
        count++;
        session.setAttribute("tasksOnRelatedTopic", count);

        if (count < 3) {
            return;
        }

        String exploringTopic = (String) session.getAttribute("exploringTopic");
        ArrayList<String> queue = getRelatedTopicsQueue(session);
        if (exploringTopic != null && !exploringTopic.trim().isEmpty()) {
            queue.remove(exploringTopic);
        }

        session.setAttribute("relatedTopicsQueue", new ArrayList<String>(queue));
        session.setAttribute("exploringTopic", null);
        session.setAttribute("tasksOnRelatedTopic", 0);

        if (queue.isEmpty()) {
            session.setAttribute("topicState", "MAIN_TOPIC");
            String mainTopic = (String) session.getAttribute("mainTopic");
            if (mainTopic != null && !mainTopic.trim().isEmpty()) {
                session.setAttribute("lastTopicGroup", mainTopic);
                Object sel = session.getAttribute("selectedTopic");
                if (!(sel instanceof String) || ((String) sel).trim().isEmpty()) {
                    session.setAttribute("selectedTopic", mainTopic);
                }
            }
            session.setAttribute("topicSwitchMessage", "Отлично! Возвращаемся к основной теме.");
        }
    }

    private boolean isCorrectCheckResult(String result) {
        if (result == null) {
            return false;
        }
        String r = result.trim();
        if (r.isEmpty()) {
            return false;
        }

        String normalizedForVerdict = normalizeTaskForDedup(result);
        String firstLine = r.split("\\R", 2)[0].trim();
        if (firstLine.contains("✅")) {
            return normalizedForVerdict.contains("✅") && !normalizedForVerdict.contains("❌");
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

        return false;
    }

    private Set<String> loadSessionTaskFingerprints(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Collections.emptySet();
        }
        List<String> used = taskHistoryRepository.findDistinctTasksBySessionId(sessionId);
        if (used == null || used.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> set = new HashSet<>();
        for (String t : used) {
            set.add(normalizeTaskForDedup(t));
        }
        return set;
    }

    private boolean areTasksEquivalent(String a, String b) {
        return normalizeTaskForDedup(a).equals(normalizeTaskForDedup(b));
    }

    private String normalizeTaskForDedup(String s) {
        if (s == null) {
            return "";
        }

        String x = s.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
        x = x.replace('−', '-').replace('–', '-').replace('—', '-');
        x = x.replace('×', '*').replace('∙', '*').replace('·', '*');
        x = x.replace('÷', '/');

        x = x.replaceAll("\\s+", " ");
        x = x.replaceAll("\\s*([+\\-*/:=()])\\s*", "$1");
        return x;
    }

    private Integer inferGradeFromLevel(Integer level) {
        if (level != null && level >= 4) {
            return 7;
        }
        return 6;
    }

    private String inferTopicForSimilar(String taskText, String currentTaskTopic, String currentTopicGroup) {
        if (currentTaskTopic != null && !currentTaskTopic.trim().isEmpty() && !"algebra".equalsIgnoreCase(currentTaskTopic) && !"geometry".equalsIgnoreCase(currentTaskTopic)) {
            return currentTaskTopic;
        }

        String t = taskText == null ? "" : taskText.toLowerCase(Locale.ROOT);
        if (t.contains("%") || t.contains("процент")) {
            return "percent";
        }
        if (t.contains("/") || t.contains("дроб")) {
            return "fractions";
        }
        if (t.contains("уравн") || t.contains(" x") || t.contains("x ") || t.contains("x=") || t.contains("x =")) {
            return "equations";
        }
        if (t.contains("площад") || t.contains("прямоуголь") || t.contains("треуголь") || t.contains("квадрат")) {
            return "areas";
        }
        if (t.contains("км/ч") || t.contains("скорост") || t.contains("время") || t.contains("расстояни")) {
            return "speed_time_distance";
        }

        if (currentTopicGroup != null && "geometry".equalsIgnoreCase(currentTopicGroup)) {
            return "areas";
        }
        return "equations";
    }

    @PostMapping("/task/report")
    public String reportTask(@RequestParam(name = "reportType") String reportType,
                             @RequestParam(name = "reportText") String reportText,
                             @RequestParam(name = "standardTaskId", required = false) Long standardTaskId,
                             HttpSession session,
                             Model model) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        String task = (String) session.getAttribute("lastTask");
        String answer = (String) session.getAttribute("lastAnswer");
        String result = (String) session.getAttribute("lastResult");
        Boolean isCorrect = (Boolean) session.getAttribute("lastIsCorrect");

        if (task == null || result == null || isCorrect == null) {
            return "redirect:/learn";
        }

        Long resolvedStandardId = standardTaskId != null ? standardTaskId : (Long) session.getAttribute("lastStandardTaskId");
        taskReportService.createReport(userId, resolvedStandardId, task, answer, isCorrect, reportType, reportText);

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("result", result);
        model.addAttribute("task", task);
        model.addAttribute("answer", answer);
        model.addAttribute("level", user.getCurrentLevel());
        model.addAttribute("isCorrect", isCorrect);
        model.addAttribute("reportSuccess", "Спасибо! Сообщение о проблеме сохранено и будет проверено.");

        Integer attemptsLeft = (Integer) session.getAttribute("lastAttemptsLeft");
        Boolean newTask = (Boolean) session.getAttribute("lastNewTask");
        if (attemptsLeft != null) {
            model.addAttribute("attemptsLeft", attemptsLeft);
        }
        if (newTask != null) {
            model.addAttribute("newTask", newTask);
        }

        model.addAttribute("standardTaskId", session.getAttribute("lastStandardTaskId"));
        model.addAttribute("taskTopic", session.getAttribute("lastTaskTopic"));
        model.addAttribute("taskTopicDisplay", StandardTaskService.getTopicDisplayName((String) session.getAttribute("lastTaskTopic")));
        model.addAttribute("taskHint", session.getAttribute("lastTaskHint"));

        return "result";
    }

    @PostMapping("/dispute")
    public String dispute(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        String task = (String) session.getAttribute("lastTask");
        String answer = (String) session.getAttribute("lastAnswer");
        String result = (String) session.getAttribute("lastResult");
        Boolean isCorrect = (Boolean) session.getAttribute("lastIsCorrect");

        if (task == null || answer == null || result == null || isCorrect == null) {
            return "redirect:/learn";
        }

        String disputeResult = ollamaService.disputeAnswer(task, answer);

        model.addAttribute("result", result);
        model.addAttribute("task", task);
        model.addAttribute("answer", answer);
        model.addAttribute("level", user.getCurrentLevel());
        model.addAttribute("isCorrect", isCorrect);
        model.addAttribute("disputeResult", disputeResult);

        Integer attemptsLeft = (Integer) session.getAttribute("lastAttemptsLeft");
        Boolean newTask = (Boolean) session.getAttribute("lastNewTask");
        if (attemptsLeft != null) {
            model.addAttribute("attemptsLeft", attemptsLeft);
        }
        if (newTask != null) {
            model.addAttribute("newTask", newTask);
        }

        model.addAttribute("standardTaskId", session.getAttribute("lastStandardTaskId"));
        model.addAttribute("taskTopic", session.getAttribute("lastTaskTopic"));
        model.addAttribute("taskTopicDisplay", StandardTaskService.getTopicDisplayName((String) session.getAttribute("lastTaskTopic")));
        model.addAttribute("taskHint", session.getAttribute("lastTaskHint"));

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
        session.removeAttribute("currentTopicGroup");
        session.removeAttribute("currentStandardTaskId");
        session.removeAttribute("currentTaskTopic");
        session.removeAttribute("currentTaskGrade");
        session.removeAttribute("currentTaskDifficulty");
        session.removeAttribute("currentTaskHint");
        session.removeAttribute("lastTopicGroup");
        session.removeAttribute("topicState");
        session.removeAttribute("mainTopic");
        session.removeAttribute("relatedTopicsQueue");
        session.removeAttribute("exploringTopic");
        session.removeAttribute("tasksOnRelatedTopic");
        session.removeAttribute("selectedTopic");

        log.info("Прогресс сброшен для пользователя: {}", userId);
        return "redirect:/profile";
    }

    private void clearCurrentTask(HttpSession session) {
        session.removeAttribute("currentTask");
        session.removeAttribute("attempts");
        session.removeAttribute("currentTopicGroup");
        session.removeAttribute("currentStandardTaskId");
        session.removeAttribute("currentTaskTopic");
        session.removeAttribute("currentTaskGrade");
        session.removeAttribute("currentTaskDifficulty");
        session.removeAttribute("currentTaskHint");
    }

    private String buildSwitchResult(Model model,
                                     String result,
                                     String task,
                                     String answer,
                                     User user,
                                     HttpSession session,
                                     boolean topicSwitched,
                                     String switchedToTopic) {
        model.addAttribute("result", result);
        model.addAttribute("task", task);
        model.addAttribute("answer", answer);
        model.addAttribute("level", user.getCurrentLevel());
        model.addAttribute("isCorrect", false);
        model.addAttribute("newTask", true);
        model.addAttribute("topicSwitched", topicSwitched);
        model.addAttribute("switchedToTopic", switchedToTopic);
        model.addAttribute("taskHint", session.getAttribute("lastTaskHint"));
        model.addAttribute("standardTaskId", session.getAttribute("lastStandardTaskId"));
        model.addAttribute("taskTopic", session.getAttribute("lastTaskTopic"));
        model.addAttribute("taskTopicDisplay", StandardTaskService.getTopicDisplayName((String) session.getAttribute("lastTaskTopic")));

        session.setAttribute("lastNewTask", true);
        session.removeAttribute("lastAttemptsLeft");
        return "result";
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
        stats.put("topicsPerformance", analyticsService.getTopicsPerformance(userId));

        return stats;
    }
}