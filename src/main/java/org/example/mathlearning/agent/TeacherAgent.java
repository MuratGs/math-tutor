package org.example.mathlearning.agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.mathlearning.service.OllamaService;
import org.example.mathlearning.service.UserService;
import org.example.mathlearning.service.AnalyticsService;
import org.example.mathlearning.service.TopicGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TeacherAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(TeacherAgent.class);

    @Autowired
    private OllamaService ollamaService;

    @Autowired
    private UserService userService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private TopicGraphService topicGraphService;

    private Long currentUserId;
    private int currentLevel = 1;
    private int correctInRow = 0;
    private int wrongInRow = 0;
    private String currentTask;
    private String currentTopic = "fractions";

    private enum State {
        MAIN_TOPIC,
        EXPLORING_RELATED,
        RETURNING_TO_MAIN
    }

    private State currentState = State.MAIN_TOPIC;
    private String mainTopic;
    private java.util.List<String> relatedTopicsQueue = new java.util.ArrayList<>();
    private String exploringTopic;
    private int tasksOnRelatedTopic = 0;

    // Буфер для хранения последних результатов
    private final java.util.Queue<Boolean> recentResults = new java.util.LinkedList<>();

    @Override
    protected void setup() {
        log.info("👨‍🏫 Учитель {} начал работу", getLocalName());

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    String content = msg.getContent();
                    log.info("Учитель получил: {}", content);

                    ACLMessage reply = msg.createReply();

                    if (content.startsWith("LOGIN:")) {
                        Long userId = Long.parseLong(content.substring(6));
                        handleLogin(userId, reply);
                    } else if (content.startsWith("GET_TASK:")) {
                        handleGetTask(reply);
                    } else if (content.startsWith("CHECK:")) {
                        String[] parts = content.substring(6).split("\\|");
                        if (parts.length == 2) {
                            handleCheck(parts[0], parts[1], reply);
                        }
                    }

                    send(reply);
                } else {
                    block();
                }
            }
        });
    }

    private void handleLogin(Long userId, ACLMessage reply) {
        this.currentUserId = userId;

        // Анализируем историю пользователя
        int recommendedLevel = analyticsService.recommendNextLevel(userId);
        this.currentLevel = recommendedLevel;

        // Определяем слабые темы
        String weakTopic = analyticsService.identifyWeakTopics(userId);
        if (weakTopic != null && !"balanced".equals(weakTopic)) {
            this.currentTopic = weakTopic;
            log.info("Рекомендована тема {} для пользователя {}", weakTopic, userId);
        }

        this.mainTopic = this.currentTopic;
        this.currentState = State.MAIN_TOPIC;
        this.relatedTopicsQueue = new java.util.ArrayList<>();
        this.exploringTopic = null;
        this.tasksOnRelatedTopic = 0;

        reply.setContent("LOGIN_OK:" + currentLevel + ":" + currentTopic);
    }

    private void handleGetTask(ACLMessage reply) {
        try {
            String topicToUse = currentTopic;

            if (currentState == State.EXPLORING_RELATED) {
                if (exploringTopic == null || exploringTopic.trim().isEmpty()) {
                    if (relatedTopicsQueue != null && !relatedTopicsQueue.isEmpty()) {
                        exploringTopic = relatedTopicsQueue.remove(0);
                        tasksOnRelatedTopic = 0;
                        log.info("Переход к смежной теме {} для пользователя {}", exploringTopic, currentUserId);
                    } else {
                        currentState = State.RETURNING_TO_MAIN;
                    }
                }
                if (currentState == State.EXPLORING_RELATED) {
                    topicToUse = exploringTopic;
                }
            }

            if (currentState == State.RETURNING_TO_MAIN) {
                currentState = State.MAIN_TOPIC;
                exploringTopic = null;
                tasksOnRelatedTopic = 0;
                relatedTopicsQueue = new java.util.ArrayList<>();
                currentTopic = mainTopic != null ? mainTopic : currentTopic;
                topicToUse = currentTopic;
                log.info("Возврат к основной теме {} для пользователя {}", currentTopic, currentUserId);
            }

            currentTopic = topicToUse;
            currentTask = ollamaService.generateTask(topicToUse, currentLevel, getLocalName(), currentUserId);
            reply.setContent("TASK:" + currentTask);
        } catch (Exception e) {
            log.error("Ошибка генерации задачи: {}", e.getMessage());
            reply.setContent("ERROR:Не удалось сгенерировать задачу");
        }
    }

    private void handleCheck(String task, String answer, ACLMessage reply) {
        if (task != null) {
            ollamaService.incrementTaskAttempts(getLocalName(), task, currentUserId);
        }

        String result = ollamaService.checkAnswer(task, answer);
        boolean isCorrect = false;
        if (result != null) {
            String r = result.trim();
            if (!r.isEmpty()) {
                String firstLine = r.split("\\R", 2)[0].trim();
                isCorrect = firstLine.startsWith("✅") || firstLine.contains("✅");
                if (firstLine.contains("❌")) {
                    isCorrect = false;
                }
            }
        }

        if (isCorrect && task != null) {
            ollamaService.markTaskAsSolved(getLocalName(), task, currentUserId);
        }

        // Сохраняем результат в буфер
        recentResults.offer(isCorrect);
        if (recentResults.size() > 5) {
            recentResults.poll(); // Оставляем только 5 последних
        }

        // Обновляем статистику пользователя
        if (currentUserId != null) {
            userService.updateUserStats(currentUserId, isCorrect);
        }

        // Адаптация сложности на основе анализа
        if (isCorrect) {
            correctInRow++;
            wrongInRow = 0;

            // Если 3 правильных подряд - повышаем уровень
            if (correctInRow >= 3 && currentLevel < 5) {
                currentLevel++;
                correctInRow = 0;
                result += " 🎉 Уровень повышен до " + currentLevel + "!";
            }
        } else {
            wrongInRow++;
            correctInRow = 0;

            // Анализируем общую успеваемость
            double successRate = calculateSuccessRate();

            if (successRate < 0.4 && currentLevel > 1) {
                // Если успеваемость низкая - понижаем уровень
                currentLevel--;
                wrongInRow = 0;
                result += " ⚠️ Уровень понижен до " + currentLevel + " для повторения";
            } else if (wrongInRow >= 2 && currentLevel > 1) {
                // Если 2 неправильных подряд - понижаем
                currentLevel--;
                wrongInRow = 0;
                result += " ⚠️ Уровень понижен до " + currentLevel + " для повторения";
            }
        }

        // Сохраняем новый уровень
        if (currentUserId != null) {
            userService.updateUserLevel(currentUserId, currentLevel);
        }

        if (currentUserId != null && currentState == State.MAIN_TOPIC) {
            boolean shouldSwitch = topicGraphService.shouldSwitchToRelatedTopics(currentUserId, currentTopic, analyticsService);
            if (shouldSwitch) {
                java.util.List<String> related = topicGraphService.getRelatedTopics(currentTopic);
                if (related != null && !related.isEmpty()) {
                    mainTopic = currentTopic;
                    relatedTopicsQueue = new java.util.ArrayList<>(related);
                    exploringTopic = null;
                    tasksOnRelatedTopic = 0;
                    currentState = State.EXPLORING_RELATED;
                    log.info("Переходим к исследованию смежных тем {} для пользователя {} от темы {}", relatedTopicsQueue, currentUserId, mainTopic);
                }
            }
        } else if (currentState == State.EXPLORING_RELATED) {
            tasksOnRelatedTopic++;
            if (tasksOnRelatedTopic >= 3) {
                tasksOnRelatedTopic = 0;
                if (relatedTopicsQueue != null && !relatedTopicsQueue.isEmpty()) {
                    exploringTopic = relatedTopicsQueue.remove(0);
                    log.info("Следующая смежная тема {} для пользователя {}", exploringTopic, currentUserId);
                } else {
                    currentState = State.RETURNING_TO_MAIN;
                    log.info("Смежные темы закончились, возвращаемся к основной теме {} для пользователя {}", mainTopic, currentUserId);
                }
            }
        }

        reply.setContent("RESULT:" + result + "|LEVEL:" + currentLevel);
    }

    private double calculateSuccessRate() {
        if (recentResults.isEmpty()) return 1.0;

        long correct = recentResults.stream().filter(r -> r).count();
        return (double) correct / recentResults.size();
    }

    @Override
    protected void takeDown() {
        log.info("👨‍🏫 Учитель {} завершил работу", getLocalName());
    }
}