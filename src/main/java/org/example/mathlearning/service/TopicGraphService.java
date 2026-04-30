package org.example.mathlearning.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mathlearning.model.TaskHistory;
import org.example.mathlearning.model.TopicNode;
import org.example.mathlearning.model.TopicRelation;
import org.example.mathlearning.repository.TaskHistoryRepository;
import org.example.mathlearning.repository.TopicGraphRepository;
import org.example.mathlearning.repository.TopicNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TopicGraphService {

    private static final Logger log = LoggerFactory.getLogger(TopicGraphService.class);

    @Autowired
    private TopicNodeRepository topicNodeRepository;

    @Autowired
    private TopicGraphRepository topicGraphRepository;

    @Autowired
    private TaskHistoryRepository taskHistoryRepository;

    @Autowired
    private OllamaService ollamaService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> getRelatedTopics(String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String base = topic.trim();

        List<TopicRelation> relations = topicGraphRepository.findByTopicOrderByRelationStrengthDesc(topic);
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> relatedKeys = relations.stream()
                .map(TopicRelation::getRelatedTopic)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> !s.equalsIgnoreCase(base))
                .distinct()
                .collect(Collectors.toList());

        List<TopicNode> activeNodes = topicNodeRepository.findByStubFalseAndActiveTrueOrderByDisplayNameAsc();
        if (activeNodes == null || activeNodes.isEmpty()) {
            return relatedKeys;
        }

        Set<String> activeNonStub = activeNodes.stream()
                .map(TopicNode::getTopicKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return relatedKeys.stream()
                .filter(activeNonStub::contains)
                .collect(Collectors.toList());
    }

    public List<String> getActiveTopicsForDiscipline(String discipline) {
        if (discipline == null || discipline.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return topicNodeRepository.findByDisciplineAndStubFalseAndActiveTrueOrderByDisplayNameAsc(discipline).stream()
                .map(TopicNode::getTopicKey)
                .collect(Collectors.toList());
    }

    public List<String> getAllTopicsForDiscipline(String discipline) {
        if (discipline == null || discipline.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return topicNodeRepository.findByDisciplineOrderByDisplayNameAsc(discipline).stream()
                .map(TopicNode::getTopicKey)
                .collect(Collectors.toList());
    }

    public boolean shouldSwitchToRelatedTopics(Long userId, String currentTopic) {
        if (userId == null || currentTopic == null || currentTopic.trim().isEmpty()) {
            return false;
        }

        List<TaskHistory> recent = taskHistoryRepository.findRecentByUserIdAndTopic(userId, currentTopic, 5);
        if (recent == null || recent.isEmpty()) {
            return false;
        }

        int total = recent.size();
        int solved = 0;
        for (TaskHistory t : recent) {
            if (Boolean.TRUE.equals(t.getSolved())) {
                solved++;
            }
        }

        double successRate = total == 0 ? 1.0 : (double) solved / (double) total;
        return successRate < 0.4;
    }

    public boolean shouldSwitchToRelatedTopics(Long userId, String currentTopic, AnalyticsService analyticsService) {
        return shouldSwitchToRelatedTopics(userId, currentTopic);
    }

    @Transactional
    public List<TopicRelation> generateGraphForTopic(String topicKey, String displayName, String discipline) {
        if (topicKey == null || topicKey.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<TopicNode> nodes = topicNodeRepository.findAll();
        Map<String, String> keyToDisplay = new HashMap<>();
        for (TopicNode n : nodes) {
            keyToDisplay.put(n.getTopicKey(), n.getDisplayName());
        }

        List<String> existingTopics = nodes.stream()
                .map(n -> n.getTopicKey() + " (" + (n.getDisplayName() == null ? n.getTopicKey() : n.getDisplayName()) + ")")
                .collect(Collectors.toList());

        String json = ollamaService.generateTopicRelations(topicKey, displayName, existingTopics);
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<RelationDto> dtos;
        try {
            dtos = objectMapper.readValue(json, new TypeReference<List<RelationDto>>() {
            });
        } catch (Exception e) {
            log.error("Failed to parse topic relations JSON: {}", e.getMessage());
            return Collections.emptyList();
        }

        topicGraphRepository.deleteByTopic(topicKey);

        List<TopicRelation> saved = new ArrayList<>();
        for (RelationDto dto : dtos) {
            if (dto == null || dto.related_topic == null) {
                continue;
            }
            String related = dto.related_topic.trim();
            if (related.isEmpty()) {
                continue;
            }
            if (!keyToDisplay.containsKey(related)) {
                continue;
            }

            Float strength = dto.strength;
            if (strength == null) {
                continue;
            }
            if (strength <= 0.3f) {
                continue;
            }
            if (strength > 1.0f) {
                strength = 1.0f;
            }
            if (strength < 0.0f) {
                strength = 0.0f;
            }

            saved.add(topicGraphRepository.save(new TopicRelation(topicKey, related, strength)));
        }

        saved.sort(Comparator.comparing(TopicRelation::getRelationStrength).reversed());
        return saved;
    }

    private static class RelationDto {
        public String related_topic;
        public Float strength;
    }
}
