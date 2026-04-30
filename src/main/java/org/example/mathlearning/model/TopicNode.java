package org.example.mathlearning.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "topic_registry")
public class TopicNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic_key", nullable = false, unique = true, length = 64)
    private String topicKey;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "discipline", length = 64)
    private String discipline;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "is_stub", nullable = false)
    private Boolean stub = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public TopicNode() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTopicKey() {
        return topicKey;
    }

    public void setTopicKey(String topicKey) {
        this.topicKey = topicKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDiscipline() {
        return discipline;
    }

    public void setDiscipline(String discipline) {
        this.discipline = discipline;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getStub() {
        return stub;
    }

    public void setStub(Boolean stub) {
        this.stub = stub;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
