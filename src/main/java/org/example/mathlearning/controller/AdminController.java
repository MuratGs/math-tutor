package org.example.mathlearning.controller;

import jakarta.servlet.http.HttpSession;
import org.example.mathlearning.model.TaskHistory;
import org.example.mathlearning.model.User;
import org.example.mathlearning.repository.TaskHistoryRepository;
import org.example.mathlearning.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskHistoryRepository taskHistoryRepository;

    @GetMapping("/admin")
    public String admin(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        boolean isAdmin = Boolean.TRUE.equals(user.getAdmin());
        session.setAttribute("isAdmin", isAdmin);
        if (!isAdmin) {
            return "redirect:/profile";
        }

        long totalUsers = userRepository.count();

        List<Object[]> levelCounts = userRepository.countUsersByLevel();
        Map<Integer, Long> usersByLevel = new HashMap<>();
        for (Object[] row : levelCounts) {
            Integer level = (Integer) row[0];
            Long count = (Long) row[1];
            usersByLevel.put(level, count);
        }
        for (int lvl = 1; lvl <= 5; lvl++) {
            usersByLevel.putIfAbsent(lvl, 0L);
        }

        List<Object[]> totalsList = userRepository.getTotals();
        long totalCorrect = 0L;
        long totalAttempts = 0L;
        if (totalsList != null && !totalsList.isEmpty()) {
            Object[] totals = totalsList.get(0);
            if (totals.length > 0 && totals[0] != null) {
                totalCorrect = ((Number) totals[0]).longValue();
            }
            if (totals.length > 1 && totals[1] != null) {
                totalAttempts = ((Number) totals[1]).longValue();
            }
        }
        double overallSuccessRate = totalAttempts == 0 ? 0.0 : (double) totalCorrect * 100.0 / (double) totalAttempts;

        long newUsers7d = userRepository.countNewUsersSince(LocalDateTime.now().minusDays(7));

        List<User> recentUsers = userRepository.findTop10ByOrderByCreatedAtDesc();

        List<Object[]> topSolvedRows = taskHistoryRepository.findTopSolvedUsers(10);
        List<Map<String, Object>> topSolvedUsers = new ArrayList<>();
        for (Object[] row : topSolvedRows) {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", row[0]);
            m.put("username", row[1]);
            m.put("solved", row[2]);
            topSolvedUsers.add(m);
        }

        List<TaskHistory> recentSolved = taskHistoryRepository.findRecentSolved(10);

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("usersByLevel", usersByLevel);
        model.addAttribute("overallSuccessRate", overallSuccessRate);
        model.addAttribute("newUsers7d", newUsers7d);
        model.addAttribute("recentUsers", recentUsers);
        model.addAttribute("topSolvedUsers", topSolvedUsers);
        model.addAttribute("recentSolved", recentSolved);

        return "admin";
    }
}
