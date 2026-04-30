package org.example.mathlearning.agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.mathlearning.service.OllamaService;
import org.example.mathlearning.service.UserService;
import org.example.mathlearning.service.AnalyticsService;
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

    private Long currentUserId;
    private int currentLevel = 1;
    private int correctInRow = 0;
    private int wrongInRow = 0;
    private String currentTask;
    private String currentTopic = "algebra";

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
        if (!"balanced".equals(weakTopic)) {
            this.currentTopic = weakTopic;
            log.info("Рекомендована тема {} для пользователя {}", weakTopic, userId);
        }

        reply.setContent("LOGIN_OK:" + currentLevel + ":" + currentTopic);
    }

    private void handleGetTask(ACLMessage reply) {
        try {
            // Генерируем задачу с учётом текущего уровня
            currentTask = ollamaService.generateTask(currentTopic, currentLevel, getLocalName(), currentUserId);
            reply.setContent("TASK:" + currentTask);
        } catch (Exception e) {
            log.error("Ошибка генерации задачи: {}", e.getMessage());
            reply.setContent("ERROR:Не удалось сгенерировать задачу");
        }
    }

    private void handleCheck(String task, String answer, ACLMessage reply) {
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