package com.maiya.investigator.domain.auth;

public final class Permissions {
    public static final String PROJECTS = "menu.projects";
    public static final String REPOSITORIES = "menu.repositories";
    public static final String BINDINGS = "menu.bindings";
    public static final String AGENTS = "menu.agents";
    public static final String AUTOMATIONS = "menu.automations";
    public static final String INVESTIGATE = "menu.investigate";
    public static final String REVIEW = "menu.review";
    public static final String MODEL = "menu.model";
    public static final String USERS = "menu.users";
    public static final String ROLES = "menu.roles";
    public static final String USAGE = "menu.usage";
    public static final String AUDIT = "menu.audit";

    public static final String PROJECTS_WRITE = "action.projects.write";
    public static final String PROJECTS_DELETE = "action.projects.delete";
    public static final String REPOSITORIES_WRITE = "action.repositories.write";
    public static final String REPOSITORIES_DELETE = "action.repositories.delete";
    public static final String BINDINGS_WRITE = "action.bindings.write";
    public static final String BINDINGS_DELETE = "action.bindings.delete";
    public static final String BINDINGS_MONITOR = "action.bindings.monitor";
    public static final String AGENTS_WRITE = "action.agents.write";
    public static final String AGENTS_DELETE = "action.agents.delete";
    public static final String AUTOMATIONS_WRITE = "action.automations.write";
    public static final String AUTOMATIONS_DELETE = "action.automations.delete";
    public static final String MODEL_WRITE = "action.model.write";
    public static final String USERS_WRITE = "action.users.write";
    public static final String ROLES_WRITE = "action.roles.write";
    public static final String FEISHU_WRITE = "action.feishu.write";

    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    private Permissions() {
    }
}
