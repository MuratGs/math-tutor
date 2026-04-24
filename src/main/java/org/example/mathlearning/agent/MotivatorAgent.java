package org.example.mathlearning.agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import org.example.mathlearning.service.OllamaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MotivatorAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(MotivatorAgent.class);

    private OllamaService ollamaService;

    @Override
    protected void setup() {
        log.info("💬 Мотиватор {} начал работу", getLocalName());

        Object[] args = getArguments();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof OllamaService) {
                    this.ollamaService = (OllamaService) arg;
                }
            }
        }

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) {
                    block();
                    return;
                }

                String content = msg.getContent();
                ACLMessage reply = msg.createReply();

                if (content == null) {
                    reply.setContent("ERROR:EMPTY_MESSAGE");
                    send(reply);
                    return;
                }

                if (content.startsWith("PING")) {
                    reply.setContent("PONG");
                    send(reply);
                    return;
                }

                if (ollamaService == null) {
                    reply.setContent("ERROR:NO_MODEL");
                    send(reply);
                    return;
                }

                String userMessage = content;
                if (content.startsWith("MOTIVATE:")) {
                    userMessage = content.substring("MOTIVATE:".length()).trim();
                }

                String prompt = "Ты — мотивационный наставник по математике для школьника. " +
                        "Отвечай на русском. " +
                        "Тон: поддерживающий, без токсичности, но без пустых комплиментов. " +
                        "Пиши коротко: 2–6 предложений. " +
                        "Всегда давай 1 конкретный следующий шаг. " +
                        "Если ученик расстроен — сначала успокой, затем помоги. " +
                        "Не давай готовое решение задачи, если ученик его прямо не попросил.\n\n" +
                        "Сообщение ученика: " + userMessage + "\n" +
                        "Мотиватор:";

                try {
                    String result = ollamaService.askModel(prompt);
                    reply.setContent("MOTIVATION:" + (result == null ? "" : result.trim()));
                } catch (Exception e) {
                    log.error("Ошибка мотивации", e);
                    reply.setContent("ERROR:MODEL_CALL_FAILED");
                }

                send(reply);
            }
        });
    }

    @Override
    protected void takeDown() {
        log.info("💬 Мотиватор {} завершил работу", getLocalName());
    }
}
