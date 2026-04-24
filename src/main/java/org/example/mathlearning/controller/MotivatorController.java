package org.example.mathlearning.controller;

import jakarta.servlet.http.HttpSession;
import org.example.mathlearning.service.OllamaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/motivator")
public class MotivatorController {

    @Autowired
    private OllamaService ollamaService;

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, Object> request, HttpSession session) {
        String message = request.get("message") == null ? "" : request.get("message").toString();
        message = message.trim();
        if (message.isEmpty()) {
            return Map.of("reply", "Напиши сообщение — и я помогу с мотивацией.");
        }

        String page = request.get("page") == null ? null : request.get("page").toString();
        String task = request.get("task") == null ? null : request.get("task").toString();

        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) session.getAttribute("motivatorHistory");
        if (history == null) {
            history = new ArrayList<>();
        }

        history.add(Map.of("role", "user", "content", message));
        while (history.size() > 20) {
            history.remove(0);
        }

        String lastTask = (String) session.getAttribute("lastTask");
        String lastAnswer = (String) session.getAttribute("lastAnswer");
        String lastResult = (String) session.getAttribute("lastResult");
        Boolean lastIsCorrect = (Boolean) session.getAttribute("lastIsCorrect");

        StringBuilder prompt = new StringBuilder();
        prompt.append("Ты — мотивационный наставник по математике для школьника. ")
                .append("Отвечай на русском. ")
                .append("Тон: поддерживающий, без токсичности, но без пустых комплиментов. ")
                .append("Пиши коротко: 2–6 предложений. ")
                .append("Всегда давай 1 конкретный следующий шаг. ")
                .append("Если пользователь расстроен — сначала успокой, затем помоги. ")
                .append("Не придумывай факты о пользователе.\n\n");

        if (page != null) {
            prompt.append("Страница: ").append(page).append("\n");
        }

        if (task != null && !task.isBlank()) {
            prompt.append("Текущая задача: ").append(task).append("\n");
        }

        if (lastTask != null && lastResult != null) {
            prompt.append("Последняя проверка:\n");
            prompt.append("Задача: ").append(lastTask).append("\n");
            if (lastAnswer != null) {
                prompt.append("Ответ ученика: ").append(lastAnswer).append("\n");
            }
            prompt.append("Результат: ").append(lastResult).append("\n");
            if (lastIsCorrect != null) {
                prompt.append("Правильно: ").append(lastIsCorrect).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("Диалог:\n");
        for (Map<String, String> m : history) {
            String role = m.getOrDefault("role", "user");
            String content = m.getOrDefault("content", "");
            if ("assistant".equals(role)) {
                prompt.append("Мотиватор: ").append(content).append("\n");
            } else {
                prompt.append("Ученик: ").append(content).append("\n");
            }
        }
        prompt.append("Мотиватор:");

        String reply;
        try {
            reply = ollamaService.askModel(prompt.toString());
        } catch (Exception e) {
            reply = "Не получилось ответить сейчас. Попробуй ещё раз через пару секунд.";
        }

        if (reply == null) {
            reply = "Не получилось ответить сейчас. Попробуй ещё раз через пару секунд.";
        }

        reply = reply.trim();
        history.add(Map.of("role", "assistant", "content", reply));
        while (history.size() > 20) {
            history.remove(0);
        }
        session.setAttribute("motivatorHistory", history);

        Map<String, Object> response = new HashMap<>();
        response.put("reply", reply);
        return response;
    }
}
