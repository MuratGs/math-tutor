package org.example.mathlearning.agent;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StudentAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(StudentAgent.class);

    @Override
    protected void setup() {
        log.info("🧑‍🎓 Студент {} начал учиться", getLocalName());

        addBehaviour(new TickerBehaviour(this, 30000) {
            @Override
            protected void onTick() {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(new jade.core.AID("Teacher", jade.core.AID.ISLOCALNAME));
                msg.setContent("TASK:math");
                send(msg);
                log.info("Студент запросил задание");
            }
        });
    }

    @Override
    protected void takeDown() {
        log.info("🧑‍🎓 Студент {} завершил обучение", getLocalName());
    }
}