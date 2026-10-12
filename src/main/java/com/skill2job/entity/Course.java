package com.skill2job.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "courses")
public class Course {

    @Id
    private String id;
    private String title;
    private String platform;
    private String url;
    /** Course difficulty 1 (beginner) to 5 (expert). */
    private int level;
    private boolean free;

    /** Skills this course teaches (referenced by id). */
    @Indexed
    private List<String> skillIds = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public boolean isFree() { return free; }
    public void setFree(boolean free) { this.free = free; }
    public List<String> getSkillIds() {
        if (skillIds == null) { skillIds = new ArrayList<>(); }
        return skillIds;
    }
    public void setSkillIds(List<String> skillIds) { this.skillIds = skillIds; }
}
