package com.maiya.investigator.graph.route;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investigator.routes")
public class InvestigationRouteProperties {
    private String fallback = "http-5xx";
    private List<Item> items = new ArrayList<>();

    public String getFallback() {
        return fallback;
    }

    public void setFallback(String fallback) {
        this.fallback = fallback == null || fallback.isBlank() ? "http-5xx" : fallback.trim();
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public static class Item {
        private String id = "";
        private String label = "";
        private String description = "";
        private List<String> keywords = new ArrayList<>();
        private List<String> toolKinds = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id == null ? "" : id.trim();
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label == null ? "" : label.trim();
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description == null ? "" : description.trim();
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(List<String> keywords) {
            this.keywords = keywords == null ? new ArrayList<>() : keywords;
        }

        public List<String> getToolKinds() {
            return toolKinds;
        }

        public void setToolKinds(List<String> toolKinds) {
            this.toolKinds = toolKinds == null ? new ArrayList<>() : toolKinds;
        }
    }
}
