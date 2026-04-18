package org.example.mathlearning.controller;

import org.example.mathlearning.model.User;
import org.example.mathlearning.repository.UserRepository;
import org.example.mathlearning.service.AnalyticsService;
import org.example.mathlearning.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Optional;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private AnalyticsService analyticsService;

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute User user,
                               BindingResult bindingResult,
                               Model model) {

        if (bindingResult.hasErrors()) {
            return "register";
        }

        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            model.addAttribute("error", "Имя пользователя уже занято");
            return "register";
        }

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            model.addAttribute("error", "Email уже используется");
            return "register";
        }

        user.setCurrentLevel(1);
        user.setTotalCorrect(0);
        user.setTotalAttempts(0);

        userRepository.save(user);
        return "redirect:/login?registered=true";
    }

    @GetMapping("/login")
    public String showLoginForm(@RequestParam(value = "registered", required = false) String registered,
                                Model model) {
        if (registered != null) {
            model.addAttribute("success", "Регистрация успешна! Теперь можете войти.");
        }
        return "login";
    }

    @PostMapping("/login")
    public String loginUser(@RequestParam String username,
                            @RequestParam String password,
                            HttpSession session,
                            Model model) {

        if (username == null || username.trim().isEmpty()) {
            model.addAttribute("error", "Введите имя пользователя");
            return "login";
        }

        if (password == null || password.trim().isEmpty()) {
            model.addAttribute("error", "Введите пароль");
            return "login";
        }

        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent() && userOpt.get().getPassword().equals(password)) {
            session.setAttribute("userId", userOpt.get().getId());
            session.setAttribute("username", userOpt.get().getUsername());
            session.setAttribute("userLevel", userOpt.get().getCurrentLevel());
            session.setAttribute("correctInRow", 0);
            session.setAttribute("wrongInRow", 0);
            return "redirect:/profile";
        } else {
            model.addAttribute("error", "Неверное имя пользователя или пароль");
            return "login";
        }
    }

    @GetMapping("/profile")
    public String showProfile(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        // Добавляем все необходимые атрибуты для профиля
        model.addAttribute("user", user);
        model.addAttribute("levelStats", userService.getLevelStats(userId));
        model.addAttribute("recentTasks", userService.getRecentTasks(userId, 10));
        model.addAttribute("successRate", userService.getSuccessRate(userId));

        // Добавляем аналитику
        model.addAttribute("analytics", analyticsService.getLevelStatistics(userId));
        model.addAttribute("weakTopic", analyticsService.identifyWeakTopics(userId));
        model.addAttribute("recommendedLevel", analyticsService.recommendNextLevel(userId));

        return "profile";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}