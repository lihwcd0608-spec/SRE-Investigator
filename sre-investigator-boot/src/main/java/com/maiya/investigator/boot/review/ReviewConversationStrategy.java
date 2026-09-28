package com.maiya.investigator.boot.review;

import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.RepositoryConfig;
import com.maiya.investigator.domain.control.RuleUsage;
import com.maiya.investigator.graph.AgentThoughts;
import com.maiya.investigator.graph.InvestigationAgent;
import com.maiya.investigator.graph.conversation.AttachmentPrompt;
import com.maiya.investigator.graph.conversation.ConversationContext;
import com.maiya.investigator.graph.conversation.ConversationOutcome;
import com.maiya.investigator.graph.conversation.ConversationStrategy;
import com.maiya.investigator.graph.skill.SkillLoader;
import com.maiya.investigator.mcp.GitCompareService;
import com.maiya.investigator.mcp.GitCompareService.GitCompareResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReviewConversationStrategy implements ConversationStrategy {
    private static final Logger log = LoggerFactory.getLogger(ReviewConversationStrategy.class);
    static final String DEFAULT_ROUTE = "code-review";
    private final ControlPlaneStore controlPlane;
    private final GitCompareService gitCompare;
    private final SkillLoader skills;
    private final InvestigationAgent agent;

    public ReviewConversationStrategy(
            ControlPlaneStore controlPlane,
            GitCompareService gitCompare,
            SkillLoader skills,
            InvestigationAgent agent
    ) {
        this.controlPlane = controlPlane;
        this.gitCompare = gitCompare;
        this.skills = skills;
        this.agent = agent;
    }

    @Override
    public ConversationMode mode() {
        return ConversationMode.REVIEW;
    }

    @Override
    public ConversationOutcome execute(ConversationContext context) {
        if (context.repositoryId() == null) {
            throw new IllegalArgumentException("请选择要审查的代码仓");
        }
        if (context.baseRef() == null || context.baseRef().isBlank() || context.headRef() == null || context.headRef().isBlank()) {
            throw new IllegalArgumentException("请选择两个分支后再审查");
        }
        RepositoryConfig repository = controlPlane.repository(context.projectId(), context.repositoryId());
        String route = context.route() == null || context.route().isBlank() ? DEFAULT_ROUTE : context.route().trim();
        String skillSlug = context.skillSlug() == null ? "" : context.skillSlug().trim();
        String ruleSlug = context.ruleSlug() == null ? "" : context.ruleSlug().trim();
        log.info(
                "策略 REVIEW project={} repo={} base={} head={} route={} skill={} rule={}",
                context.projectId(),
                repository.name(),
                context.baseRef(),
                context.headRef(),
                route,
                skillSlug,
                ruleSlug
        );
        AgentThoughts.emit("phase", "正在拉取 " + context.baseRef() + " / " + context.headRef() + " 的最新提交");
        GitCompareResult diff = gitCompare.compare(Path.of(repository.rootPath()), context.baseRef(), context.headRef());
        AgentThoughts.emit(
                "phase",
                (diff.fetchedRemote() ? "已拉取 origin 最新，" : "使用本地分支，")
                        + (diff.commonAncestor() ? "按共同祖先对比 " : "未找到共同祖先，整树对比 ")
                        + diff.files().size()
                        + " 个文件"
        );
        List<String> skillContext = resolveSkills(context.projectId(), skillSlug);
        List<String> ruleContext = skills.withBuiltinReviewRules(
                context.projectId(),
                skills.mergePinnedRule(
                        skills.compactRules(context.projectId(), RuleUsage.REVIEW),
                        context.projectId(),
                        ruleSlug
                )
        );
        AgentThoughts.emit("phase", "已装载 " + skillContext.size() + " 条 Skill / " + ruleContext.size() + " 条 Rule");
        String question = context.question() == null ? "" : context.question();
        if (context.memoryPrompt() != null && !context.memoryPrompt().isBlank()) {
            question = context.memoryPrompt() + "## 当前审查焦点\n" + question;
        }
        InvestigationAgent.AgentResult result = agent.review(
                AttachmentPrompt.append(question, context.attachments()),
                context.environment().code(),
                context.provider(),
                context.model(),
                context.projectId() == null ? null : context.projectId().toString(),
                skillContext,
                ruleContext,
                GitCompareService.promptBlock(diff)
        );
        return new ConversationOutcome(result.content(), result.modelUsed(), route, Map.of(
                "modelUsed", result.modelUsed(),
                "availableTools", result.availableTools(),
                "usedToolCalls", result.usedToolCalls(),
                "budgetExhausted", result.budgetExhausted(),
                "maxToolCalls", result.maxToolCalls(),
                "repositoryId", repository.id().toString(),
                "baseRef", diff.baseRef(),
                "headRef", diff.headRef(),
                "changedFiles", diff.files().size()
        ));
    }

    private List<String> resolveSkills(UUID projectId, String skillSlug) {
        if (!skillSlug.isBlank()) {
            List<String> pinned = skills.compactBySlug(projectId, skillSlug);
            if (!pinned.isEmpty()) {
                AgentThoughts.emit("phase", "已指定 Skill " + skillSlug);
                return pinned;
            }
            AgentThoughts.emit("phase", "未找到指定 Skill " + skillSlug + "，已装载代码审查 Skill");
        }
        return skills.compactContext(projectId, RuleUsage.REVIEW);
    }
}
