package org.example.mathlearning.config;

import org.example.mathlearning.agent.MotivatorAgent;
import org.example.mathlearning.agent.StudentAgent;
import org.example.mathlearning.agent.TeacherAgent;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.mathlearning.service.OllamaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JadeConfig {

    private static final Logger log = LoggerFactory.getLogger(JadeConfig.class);
    private AgentContainer container;

    @Autowired
    private OllamaService ollamaService;

    @PostConstruct
    public void startJade() {
        try {
            log.info("🚀 Запуск JADE...");

            Runtime rt = Runtime.instance();
            Profile p = new ProfileImpl();
            p.setParameter(Profile.MAIN_HOST, "localhost");
            p.setParameter(Profile.MAIN_PORT, "1099");
            p.setParameter(Profile.GUI, "false");

            container = rt.createMainContainer(p);

            container.createNewAgent("Teacher", TeacherAgent.class.getName(), null).start();
            container.createNewAgent("Student", StudentAgent.class.getName(), null).start();
            container.createNewAgent("Motivator", MotivatorAgent.class.getName(), new Object[]{ollamaService}).start();

            log.info("✅ JADE запущена (без GUI, агенты работают)");
        } catch (Exception e) {
            log.error("Ошибка JADE", e);
        }
    }

    @PreDestroy
    public void stopJade() {
        try {
            if (container != null) {
                container.kill();
                log.info("JADE остановлена");
            }
        } catch (Exception e) {
            log.error("Ошибка остановки JADE", e);
        }
    }
}