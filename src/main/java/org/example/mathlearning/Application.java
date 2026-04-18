package org.example.mathlearning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.out.println("\n" + "=".repeat(50));
        System.out.println("✅ ПРОЕКТ ЗАПУЩЕН!");
        System.out.println("🌐 Открой браузер: http://localhost:8080");
        System.out.println("🤖 JADE GUI: http://localhost:7778");
        System.out.println("=".repeat(50));
    }
}