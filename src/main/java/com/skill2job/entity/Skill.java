package com.skill2job.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "skills")
public class Skill {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String category;

    /** Other spellings, e.g. "ReactJS", "React.js" for React. */
    private List<String> aliases = new ArrayList<>();

    public Skill() { }

    public Skill(String name, String category, List<String> aliases) {
        this.name = name;
        this.category = category;
        this.aliases = aliases == null ? new ArrayList<>() : new ArrayList<>(aliases);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getAliases() {
        if (aliases == null) { aliases = new ArrayList<>(); }
        return aliases;
    }
    public void setAliases(List<String> aliases) { this.aliases = aliases; }
}
