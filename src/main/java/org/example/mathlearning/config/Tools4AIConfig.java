package org.example.mathlearning.config;

import com.t4a.processor.LocalAIActionProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Tools4AIConfig {

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ollama.model:llama2}")
    private String modelName;

    @Bean
    public LocalAIActionProcessor localAIActionProcessor() {
        System.out.println("🔧 Инициализация Tools4AI версии 0.9.6...");
        System.out.println("📡 URL Ollama (для информации): " + ollamaUrl);
        System.out.println("🤖 Модель: " + modelName);

        // Просто создаем процессор - он сам должен найти Ollama
        LocalAIActionProcessor processor = new LocalAIActionProcessor();

        // В версии 0.9.6 нет специальных полей, Tools4AI использует
        // стандартные настройки окружения для подключения к Ollama

        return processor;
    }
}