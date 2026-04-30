package org.example.mathlearning.controller;

import jakarta.servlet.http.HttpSession;
import org.example.mathlearning.model.TopicNode;
import org.example.mathlearning.model.TopicRelation;
import org.example.mathlearning.model.TaskHistory;
import org.example.mathlearning.model.User;
import org.example.mathlearning.repository.TaskReportRepository;
import org.example.mathlearning.repository.TaskHistoryRepository;
import org.example.mathlearning.repository.TopicGraphRepository;
import org.example.mathlearning.repository.TopicNodeRepository;
import org.example.mathlearning.repository.UserRepository;
import org.example.mathlearning.service.StandardTaskService;
import org.example.mathlearning.service.TaskReportService;
import org.example.mathlearning.service.TopicGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskHistoryRepository taskHistoryRepository;

    @Autowired
    private TaskReportRepository taskReportRepository;

    @Autowired
    private TaskReportService taskReportService;

    @Autowired
    private StandardTaskService standardTaskService;

    @Autowired
    private TopicNodeRepository topicNodeRepository;

    @Autowired
    private TopicGraphRepository topicGraphRepository;

    @Autowired
    private TopicGraphService topicGraphService;

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

        model.addAttribute("taskReports", taskReportRepository.findTop50ByOrderByCreatedAtDesc());
        model.addAttribute("newTaskReports", taskReportRepository.findTop50ByStatusOrderByCreatedAtDesc("NEW"));

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("usersByLevel", usersByLevel);
        model.addAttribute("overallSuccessRate", overallSuccessRate);
        model.addAttribute("newUsers7d", newUsers7d);
        model.addAttribute("recentUsers", recentUsers);
        model.addAttribute("topSolvedUsers", topSolvedUsers);
        model.addAttribute("recentSolved", recentSolved);

        return "admin";
    }

    @PostMapping("/admin/task-report/resolve")
    public String resolveTaskReport(@RequestParam("id") Long reportId,
                                    @RequestParam(value = "adminNote", required = false) String adminNote,
                                    HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getAdmin())) {
            return "redirect:/profile";
        }

        taskReportService.resolveReport(reportId, adminNote);
        return "redirect:/admin";
    }

    @PostMapping("/admin/standard-task/deactivate")
    public String deactivateStandardTask(@RequestParam("id") Long standardTaskId,
                                         HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getAdmin())) {
            return "redirect:/profile";
        }

        standardTaskService.deactivateStandardTask(standardTaskId);
        return "redirect:/admin";
    }

    @GetMapping("/admin/topics")
    public String adminTopics(@RequestParam(value = "discipline", required = false) String discipline,
                              @RequestParam(value = "topicKey", required = false) String topicKey,
                              HttpSession session,
                              Model model) {
        User adminUser = requireAdmin(session);
        if (adminUser == null) {
            return "redirect:/profile";
        }

        List<String> disciplines = topicNodeRepository.findDistinctDisciplines();
        if (disciplines == null) {
            disciplines = Collections.emptyList();
        }

        String selectedDiscipline = discipline;
        if ((selectedDiscipline == null || selectedDiscipline.trim().isEmpty()) && !disciplines.isEmpty()) {
            selectedDiscipline = disciplines.get(0);
        }

        List<TopicNode> topics;
        if (selectedDiscipline == null || selectedDiscipline.trim().isEmpty()) {
            topics = topicNodeRepository.findAll();
        } else {
            topics = topicNodeRepository.findByDisciplineOrderByDisplayNameAsc(selectedDiscipline);
        }
        if (topics == null) {
            topics = Collections.emptyList();
        }

        topics.sort(Comparator.comparing(t -> t.getDisplayName() == null ? t.getTopicKey() : t.getDisplayName(), String.CASE_INSENSITIVE_ORDER));

        String selectedTopicKey = topicKey;
        if ((selectedTopicKey == null || selectedTopicKey.trim().isEmpty()) && !topics.isEmpty()) {
            selectedTopicKey = topics.get(0).getTopicKey();
        }

        List<TopicRelation> relations = Collections.emptyList();
        if (selectedTopicKey != null && !selectedTopicKey.trim().isEmpty()) {
            relations = topicGraphRepository.findByTopicOrderByRelationStrengthDesc(selectedTopicKey);
            if (relations == null) {
                relations = Collections.emptyList();
            }
        }

        List<TopicNode> allNodes = topicNodeRepository.findAll();
        Map<String, String> keyToDisplay = new HashMap<>();
        if (allNodes != null) {
            for (TopicNode n : allNodes) {
                keyToDisplay.put(n.getTopicKey(), n.getDisplayName() == null ? n.getTopicKey() : n.getDisplayName());
            }
        }

        model.addAttribute("disciplines", disciplines);
        model.addAttribute("selectedDiscipline", selectedDiscipline);
        model.addAttribute("topics", topics);
        model.addAttribute("selectedTopicKey", selectedTopicKey);
        model.addAttribute("relations", relations);
        model.addAttribute("keyToDisplay", keyToDisplay);
        model.addAttribute("allNodes", allNodes == null ? Collections.emptyList() : allNodes);

        return "admin-topics";
    }

    @PostMapping("/admin/topics/topic/create")
    public String createTopic(@RequestParam("topicKey") String topicKey,
                              @RequestParam("displayName") String displayName,
                              @RequestParam("discipline") String discipline,
                              @RequestParam(value = "active", required = false) String active,
                              @RequestParam(value = "stub", required = false) String stub,
                              HttpSession session) {
        User adminUser = requireAdmin(session);
        if (adminUser == null) {
            return "redirect:/profile";
        }

        if (topicKey == null || topicKey.trim().isEmpty()) {
            return redirectTopics(discipline, null);
        }

        String key = topicKey.trim();
        TopicNode node = topicNodeRepository.findByTopicKey(key).orElse(null);
        if (node == null) {
            node = new TopicNode();
            node.setTopicKey(key);
        }

        node.setDisplayName(displayName == null ? null : displayName.trim());
        node.setDiscipline(discipline == null ? null : discipline.trim());
        node.setActive(active != null);
        node.setStub(stub != null);
        topicNodeRepository.save(node);

        return redirectTopics(node.getDiscipline(), node.getTopicKey());
    }

    @PostMapping("/admin/topics/topic/toggle-active")
    public String toggleTopicActive(@RequestParam("topicKey") String topicKey,
                                    @RequestParam(value = "discipline", required = false) String discipline,
                                    HttpSession session) {
        User adminUser = requireAdmin(session);
        if (adminUser == null) {
            return "redirect:/profile";
        }

        TopicNode node = topicNodeRepository.findByTopicKey(topicKey).orElse(null);
        if (node != null) {
            node.setActive(!Boolean.TRUE.equals(node.getActive()));
            topicNodeRepository.save(node);
            return redirectTopics(node.getDiscipline(), node.getTopicKey());
        }

        return redirectTopics(discipline, topicKey);
    }

    @PostMapping("/admin/topics/topic/toggle-stub")
    public String toggleTopicStub(@RequestParam("topicKey") String topicKey,
                                  @RequestParam(value = "discipline", required = false) String discipline,
                                  HttpSession session) {
        User adminUser = requireAdmin(session);
        if (adminUser == null) {
            return "redirect:/profile";
        }

        TopicNode node = topicNodeRepository.findByTopicKey(topicKey).orElse(null);
        if (node != null) {
            node.setStub(!Boolean.TRUE.equals(node.getStub()));
            topicNodeRepository.save(node);
            return redirectTopics(node.getDiscipline(), node.getTopicKey());
        }

        return redirectTopics(discipline, topicKey);
    }

    @PostMapping("/admin/topics/topic/regenerate")
    public String regenerateTopicGraph(@RequestParam("topicKey") String topicKey,
                                       @RequestParam(value = "discipline", required = false) String discipline,
                                       HttpSession session) {
        User adminUser = requireAdmin(session);
        if (adminUser == null) {
            return "redirect:/profile";
        }

        TopicNode node = topicNodeRepository.findByTopicKey(topicKey).orElse(null);
        if (node != null) {
            topicGraphService.generateGraphForTopic(node.getTopicKey(), node.getDisplayName(), node.getDiscipline());
            return redirectTopics(node.getDiscipline(), node.getTopicKey());
        }

        return redirectTopics(discipline, topicKey);
    }

    @PostMapping("/admin/topics/relation/upsert")
    public String upsertRelation(@RequestParam("topic") String topic,
                                 @RequestParam("relatedTopic") String relatedTopic,
                                 @RequestParam("strength") Float strength,
                                 @RequestParam(value = "discipline", required = false) String discipline,
                                 HttpSession session) {
        User adminUser = requireAdmin(session);
        if (adminUser == null) {
            return "redirect:/profile";
        }

        if (topic == null || topic.trim().isEmpty() || relatedTopic == null || relatedTopic.trim().isEmpty() || strength == null) {
            return redirectTopics(discipline, topic);
        }
        if (topic.trim().equalsIgnoreCase(relatedTopic.trim())) {
            return redirectTopics(discipline, topic);
        }

        TopicNode from = topicNodeRepository.findByTopicKey(topic.trim()).orElse(null);
        TopicNode to = topicNodeRepository.findByTopicKey(relatedTopic.trim()).orElse(null);
        if (from == null || to == null) {
            return redirectTopics(discipline, topic);
        }

        float s = Math.max(0.0f, Math.min(1.0f, strength));

        TopicRelation rel = topicGraphRepository.findByTopicAndRelatedTopic(from.getTopicKey(), to.getTopicKey())
                .orElseGet(() -> new TopicRelation(from.getTopicKey(), to.getTopicKey(), s));
        rel.setRelationStrength(s);
        topicGraphRepository.save(rel);

        return redirectTopics(from.getDiscipline(), from.getTopicKey());
    }

    @PostMapping("/admin/topics/relation/delete")
    public String deleteRelation(@RequestParam("id") Long id,
                                 @RequestParam(value = "discipline", required = false) String discipline,
                                 @RequestParam(value = "topicKey", required = false) String topicKey,
                                 HttpSession session) {
        User adminUser = requireAdmin(session);
        if (adminUser == null) {
            return "redirect:/profile";
        }

        if (id != null) {
            topicGraphRepository.deleteById(id);
        }

        return redirectTopics(discipline, topicKey);
    }

    private String redirectTopics(String discipline, String topicKey) {
        String d = discipline == null ? "" : discipline.trim();
        String t = topicKey == null ? "" : topicKey.trim();

        StringBuilder sb = new StringBuilder("redirect:/admin/topics");
        if (!d.isEmpty() || !t.isEmpty()) {
            sb.append("?");
            boolean first = true;
            if (!d.isEmpty()) {
                sb.append("discipline=").append(URLEncoder.encode(d, StandardCharsets.UTF_8));
                first = false;
            }
            if (!t.isEmpty()) {
                if (!first) {
                    sb.append("&");
                }
                sb.append("topicKey=").append(URLEncoder.encode(t, StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    private User requireAdmin(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return null;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }

        boolean isAdmin = Boolean.TRUE.equals(user.getAdmin());
        session.setAttribute("isAdmin", isAdmin);
        if (!isAdmin) {
            return null;
        }
        return user;
    }
}
