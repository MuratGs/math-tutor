package org.example.mathlearning.service;

import org.example.mathlearning.model.MathTask;
import org.example.mathlearning.model.StandardMathTask;
import org.example.mathlearning.model.TaskHistory;
import org.example.mathlearning.model.User;
import org.example.mathlearning.repository.MathTaskRepository;
import org.example.mathlearning.repository.StandardMathTaskRepository;
import org.example.mathlearning.repository.TaskHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class StandardTaskService {

    private static final Pattern INCOMPLETE_FRACTION_PATTERN = Pattern.compile("\\b\\d+\\s*/\\s*(?!\\d)");

    @Autowired
    private StandardMathTaskRepository standardMathTaskRepository;

    @Autowired
    private MathTaskRepository mathTaskRepository;

    @Autowired
    private TaskHistoryRepository taskHistoryRepository;

    @Autowired
    private OllamaService ollamaService;

    @Autowired
    private AnalyticsService analyticsService;

    public String chooseStandardTopicForUser(Long userId, String requestedTopicGroup) {
        return chooseStandardTopicForUser(userId, requestedTopicGroup, null);
    }

    public String chooseStandardTopicForUser(Long userId, String requestedTopicGroup, String topicToAvoid) {
        List<String> candidateTopics = topicsForGroup(requestedTopicGroup);
        if (candidateTopics == null || candidateTopics.isEmpty()) {
            return requestedTopicGroup;
        }

        List<String> effectiveTopics = candidateTopics;
        if (topicToAvoid != null && !topicToAvoid.trim().isEmpty() && candidateTopics.size() > 1) {
            List<String> filtered = new ArrayList<>();
            for (String t : candidateTopics) {
                if (t == null) {
                    continue;
                }
                if (t.equalsIgnoreCase(topicToAvoid)) {
                    continue;
                }
                filtered.add(t);
            }
            if (!filtered.isEmpty()) {
                effectiveTopics = filtered;
            }
        }

        String chosen = chooseTopicForUser(userId, effectiveTopics);
        if (chosen != null && !chosen.trim().isEmpty()) {
            return chosen;
        }

        return effectiveTopics.get(new Random().nextInt(effectiveTopics.size()));
    }

    @Transactional
    public void registerAdHocTaskInHistory(User user, String sessionId, String taskTopic, Integer difficulty, String statement) {
        if (user == null || sessionId == null || taskTopic == null || statement == null) {
            return;
        }
        final int diff = (difficulty == null) ? 1 : difficulty;
        String s = statement.trim();
        if (s.isEmpty()) {
            return;
        }

        MathTask mathTask = mathTaskRepository
                .findByTopicAndLevelAndTaskText(taskTopic, diff, s)
                .orElseGet(() -> mathTaskRepository.save(new MathTask(taskTopic, diff, s)));

        TaskHistory history = new TaskHistory(sessionId, user, mathTask);
        taskHistoryRepository.save(history);

        if (history.getId() != null) {
            taskHistoryRepository.abandonOtherInProgressTasks(user.getId(), history.getId());
        }
    }

    @Transactional
    public StandardMathTask deliverNextStandardTask(User user,
                                                   String sessionId,
                                                   String requestedTopicGroup,
                                                   Integer recommendedDifficulty,
                                                   Long overrideStandardTaskId) {
        if (user == null) {
            return null;
        }

        if (overrideStandardTaskId != null) {
            Optional<StandardMathTask> byId = standardMathTaskRepository.findById(overrideStandardTaskId);
            if (byId.isPresent()) {
                StandardMathTask t = byId.get();
                if (!isStatementValid(t.getStatement())) {
                    deactivateStandardTask(t.getId());
                    return null;
                }
                registerInHistory(user, sessionId, t);
                return t;
            }
        }

        int grade = inferGrade(user);

        List<String> candidateTopics = topicsForGroup(requestedTopicGroup);

        Set<String> usedInSession = loadSessionTaskFingerprints(sessionId);

        String lastTopic = null;
        if (sessionId != null) {
            Optional<TaskHistory> last = taskHistoryRepository.findTopBySessionIdOrderByCreatedAtDesc(sessionId);
            if (last.isPresent() && last.get().getTask() != null) {
                lastTopic = last.get().getTask().getTopic();
            }
        }

        List<List<String>> topicPhases = new ArrayList<>();
        if (lastTopic != null && candidateTopics != null && candidateTopics.size() > 1) {
            List<String> filtered = new ArrayList<>();
            for (String t : candidateTopics) {
                if (t == null) {
                    continue;
                }
                if (t.equalsIgnoreCase(lastTopic)) {
                    continue;
                }
                filtered.add(t);
            }
            if (!filtered.isEmpty()) {
                topicPhases.add(filtered);
            }
        }
        topicPhases.add(candidateTopics);

        List<Integer> difficulties = buildDifficultyCandidates(user.getCurrentLevel(), recommendedDifficulty);

        for (List<String> phaseTopics : topicPhases) {
            String chosenTopic = chooseTopicForUser(user.getId(), phaseTopics);

            for (Integer diff : difficulties) {
                List<StandardMathTask> candidates;
                if (chosenTopic != null) {
                    candidates = standardMathTaskRepository.findByActiveTrueAndTopicAndGradeAndDifficulty(chosenTopic, grade, diff);
                } else {
                    candidates = standardMathTaskRepository.findByActiveTrueAndTopicInAndGradeAndDifficulty(phaseTopics, grade, diff);
                }

                if (candidates == null || candidates.isEmpty()) {
                    continue;
                }

                Collections.shuffle(candidates);

                for (StandardMathTask t : candidates) {
                    if (t == null || t.getStatement() == null || t.getStatement().trim().isEmpty()) {
                        continue;
                    }

                    if (!isStatementValid(t.getStatement())) {
                        deactivateStandardTask(t.getId());
                        continue;
                    }

                    if (usedInSession.contains(normalizeTaskForDedup(t.getStatement()))) {
                        continue;
                    }

                    boolean alreadySolved = taskHistoryRepository.isTaskSolvedByUser(user.getId(), t.getStatement());
                    if (alreadySolved) {
                        continue;
                    }

                    registerInHistory(user, sessionId, t);
                    return t;
                }
            }
        }

        return null;
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

    @Transactional
    public boolean deactivateStandardTask(Long standardTaskId) {
        if (standardTaskId == null) {
            return false;
        }

        Optional<StandardMathTask> byId = standardMathTaskRepository.findById(standardTaskId);
        if (byId.isEmpty()) {
            return false;
        }

        StandardMathTask t = byId.get();
        t.setActive(false);
        standardMathTaskRepository.save(t);
        return true;
    }

    private boolean isStatementValid(String statement) {
        if (statement == null) {
            return false;
        }
        String s = statement.trim();
        if (s.isEmpty()) {
            return false;
        }

        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.endsWith("+") || lower.endsWith("-") || lower.endsWith("−") || lower.endsWith("–") || lower.endsWith("*") || lower.endsWith("×") || lower.endsWith(":") || lower.endsWith("/")) {
            return false;
        }

        if (s.length() > 1900) {
            return false;
        }

        return !INCOMPLETE_FRACTION_PATTERN.matcher(s).find();
    }

    @Transactional
    public StandardMathTask storeAiGeneratedStandardTask(String topic, Integer grade, Integer difficulty, String statement) {
        if (topic == null || topic.trim().isEmpty()) {
            return null;
        }
        if (grade == null || difficulty == null) {
            return null;
        }
        if (statement == null || statement.trim().isEmpty()) {
            return null;
        }

        if (!isStatementValid(statement)) {
            return null;
        }

        StandardMathTask t = new StandardMathTask(
                topic.trim(),
                grade,
                difficulty,
                statement.trim(),
                null,
                null,
                false,
                true,
                true
        );
        return standardMathTaskRepository.save(t);
    }

    @Transactional
    public StandardMathTask generateAndStoreSimilarTask(StandardMathTask baseTask) {
        if (baseTask == null) {
            return null;
        }

        String topic = baseTask.getTopic();
        Integer grade = baseTask.getGrade();
        Integer difficulty = baseTask.getDifficulty();

        int maxAttempts = 5;
        for (int i = 0; i < maxAttempts; i++) {
            String generated = ollamaService.generateSimilarStandardTask(topic, grade, difficulty, baseTask.getStatement());
            if (generated == null) {
                continue;
            }

            generated = generated.trim();
            if (generated.length() < 15) {
                continue;
            }

            if (!isStatementValid(generated)) {
                continue;
            }

            if (generated.equalsIgnoreCase(baseTask.getStatement() == null ? "" : baseTask.getStatement().trim())) {
                continue;
            }

            StandardMathTask t = new StandardMathTask(
                    topic,
                    grade,
                    difficulty,
                    generated,
                    null,
                    null,
                    false,
                    true,
                    true
            );
            return standardMathTaskRepository.save(t);
        }

        return null;
    }

    private void registerInHistory(User user, String sessionId, StandardMathTask standardTask) {
        MathTask mathTask = mathTaskRepository
                .findByTopicAndLevelAndTaskText(standardTask.getTopic(), standardTask.getDifficulty(), standardTask.getStatement())
                .orElseGet(() -> mathTaskRepository.save(new MathTask(standardTask.getTopic(), standardTask.getDifficulty(), standardTask.getStatement())));

        TaskHistory history = new TaskHistory(sessionId, user, mathTask);
        taskHistoryRepository.save(history);

        if (history.getId() != null) {
            taskHistoryRepository.abandonOtherInProgressTasks(user.getId(), history.getId());
        }
    }

    private int inferGrade(User user) {
        if (user.getCurrentLevel() != null && user.getCurrentLevel() >= 4) {
            return 7;
        }
        return 6;
    }

    private List<Integer> buildDifficultyCandidates(Integer currentLevel, Integer recommendedDifficulty) {
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        if (recommendedDifficulty != null) {
            set.add(clampDifficulty(recommendedDifficulty));
        }
        if (currentLevel != null) {
            set.add(clampDifficulty(currentLevel));
        }
        set.add(3);
        set.add(2);
        set.add(4);
        set.add(1);
        set.add(5);
        return new ArrayList<>(set);
    }

    private int clampDifficulty(int d) {
        return Math.max(1, Math.min(5, d));
    }

    private List<String> topicsForGroup(String requestedTopicGroup) {
        if (requestedTopicGroup == null || requestedTopicGroup.trim().isEmpty()) {
            return Arrays.asList("fractions", "percent", "equations", "areas", "speed_time_distance");
        }

        if ("algebra".equalsIgnoreCase(requestedTopicGroup)) {
            return Arrays.asList("fractions", "percent", "equations", "speed_time_distance");
        }

        if ("geometry".equalsIgnoreCase(requestedTopicGroup)) {
            return Collections.singletonList("areas");
        }

        return Collections.singletonList(requestedTopicGroup);
    }

    private String chooseTopicForUser(Long userId, List<String> candidateTopics) {
        if (userId == null || candidateTopics == null || candidateTopics.isEmpty()) {
            return null;
        }

        Map<String, Map<String, Object>> perf = analyticsService.getTopicsPerformance(userId);
        if (perf == null || perf.isEmpty()) {
            return null;
        }

        String worstTopic = null;
        double worstRate = 2.0;

        Map<String, Double> rates = new LinkedHashMap<>();

        for (String t : candidateTopics) {
            Map<String, Object> p = perf.get(t);
            if (p == null) {
                continue;
            }
            Object totalObj = p.get("total");
            Object correctObj = p.get("correct");
            if (!(totalObj instanceof Number) || !(correctObj instanceof Number)) {
                continue;
            }

            long total = ((Number) totalObj).longValue();
            long correct = ((Number) correctObj).longValue();

            if (total < 3) {
                continue;
            }

            double rate = total == 0 ? 1.0 : (double) correct / (double) total;
            rates.put(t, rate);
            if (rate < worstRate) {
                worstRate = rate;
                worstTopic = t;
            }
        }

        if (worstTopic == null) {
            return null;
        }

        if (rates.size() <= 1) {
            return worstTopic;
        }

        double exploit = 0.55;
        if (Math.random() < exploit) {
            return worstTopic;
        }

        double sum = 0.0;
        for (double r : rates.values()) {
            sum += (1.01 - Math.max(0.0, Math.min(1.0, r)));
        }
        if (sum <= 0.0) {
            List<String> keys = new ArrayList<>(rates.keySet());
            return keys.get(new Random().nextInt(keys.size()));
        }

        double pick = Math.random() * sum;
        for (Map.Entry<String, Double> e : rates.entrySet()) {
            double w = 1.01 - Math.max(0.0, Math.min(1.0, e.getValue()));
            pick -= w;
            if (pick <= 0.0) {
                return e.getKey();
            }
        }

        return worstTopic;
    }

    public static String getTopicDisplayName(String topic) {
        if (topic == null) {
            return "";
        }

        switch (topic) {
            case "algebra":
                return "Алгебра";
            case "geometry":
                return "Геометрия";
            case "expressions":
                return "Выражения";
            case "fractions":
                return "Работа с дробями";
            case "percent":
                return "Проценты";
            case "equations":
                return "Уравнения";
            case "areas":
                return "Площади";
            case "speed_time_distance":
                return "Скорость, время, расстояние";
            default:
                return topic;
        }
    }
}
