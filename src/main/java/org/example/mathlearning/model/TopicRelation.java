package org.example.mathlearning.model;

import jakarta.persistence.*;

@Entity
@Table(name = "topic_graph", uniqueConstraints = {
        @UniqueConstraint(name = "uq_topic_graph_edge", columnNames = {"topic", "related_topic"})
})
public class TopicRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false, length = 64)
    private String topic;

    @Column(name = "related_topic", nullable = false, length = 64)
    private String relatedTopic;

    @Column(name = "relation_strength", nullable = false)
    private Float relationStrength;

    public TopicRelation() {
    }

    public TopicRelation(String topic, String relatedTopic, Float relationStrength) {
        this.topic = topic;
        this.relatedTopic = relatedTopic;
        this.relationStrength = relationStrength;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getRelatedTopic() {
        return relatedTopic;
    }

    public void setRelatedTopic(String relatedTopic) {
        this.relatedTopic = relatedTopic;
    }

    public Float getRelationStrength() {
        return relationStrength;
    }

    public void setRelationStrength(Float relationStrength) {
        this.relationStrength = relationStrength;
    }
}
