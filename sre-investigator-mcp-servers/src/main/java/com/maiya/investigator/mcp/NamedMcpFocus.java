package com.maiya.investigator.mcp;

import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户正文里「使用 xxx mcp」点名的 MCP，用于收窄本轮只读工具集。
 */
public record NamedMcpFocus(String profile, ToolKind toolKind) {
    private static final Pattern NAMED_MCP = Pattern.compile(
            "(?i)(?:使用|用)\\s*([A-Za-z][\\w-]*)\\s*mcp"
    );

    public String kindLabel() {
        return switch (toolKind) {
            case MYSQL -> "MySQL";
            case REDIS -> "Redis";
            case MONGODB -> "MongoDB";
            case ELASTICSEARCH -> "Elasticsearch";
            case LOGS -> "SLS";
            case MSE -> "MSE";
            case ARMS -> "ARMS 调用链";
            case LOAD_BALANCER -> "负载均衡";
            case CDN -> "CDN";
            case K8S -> "K8s";
            case STARROCKS -> "StarRocks";
            case KAFKA -> "Kafka";
            case ROCKETMQ -> "RocketMQ";
            default -> toolKind.name();
        };
    }

    public static Optional<String> parseRequestedProfile(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = NAMED_MCP.matcher(question);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    public static Optional<String> requestedProfile(String question, String explicitProfile) {
        return requestedProfile(question, explicitProfile, List.of());
    }

    public static Optional<String> requestedProfile(
            String question,
            String explicitProfile,
            List<String> priorUserTexts
    ) {
        if (explicitProfile != null && !explicitProfile.isBlank()) {
            return Optional.of(explicitProfile.trim());
        }
        Optional<String> fromQuestion = parseRequestedProfile(question);
        if (fromQuestion.isPresent()) {
            return fromQuestion;
        }
        if (priorUserTexts == null || priorUserTexts.isEmpty()) {
            return Optional.empty();
        }
        for (int index = priorUserTexts.size() - 1; index >= 0; index--) {
            Optional<String> fromPrior = parseRequestedProfile(priorUserTexts.get(index));
            if (fromPrior.isPresent()) {
                return fromPrior;
            }
        }
        return Optional.empty();
    }

    public static Optional<NamedMcpFocus> resolve(
            List<ToolBinding> bindings,
            String question,
            String explicitProfile,
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            List<String> logsProfiles,
            List<String> mseProfiles,
            List<String> k8sProfiles,
            List<String> starrocksProfiles,
            List<String> kafkaProfiles,
            List<String> rocketmqProfiles,
            List<String> mongodbProfiles,
            List<String> armsProfiles,
            List<String> loadBalancerProfiles,
            List<String> cdnProfiles
    ) {
        Optional<String> requested = requestedProfile(question, explicitProfile, List.of());
        if (requested.isEmpty()) {
            return Optional.empty();
        }
        String want = requested.get();
        if (bindings != null) {
            for (ToolBinding binding : bindings) {
                if (!binding.enabled()) {
                    continue;
                }
                if (profileMatches(binding.profile(), want)) {
                    return Optional.of(new NamedMcpFocus(binding.profile(), binding.toolKind()));
                }
            }
        }
        return resolveFromProfileLists(
                want,
                mysqlProfiles,
                redisProfiles,
                logsProfiles,
                mseProfiles,
                k8sProfiles,
                starrocksProfiles,
                kafkaProfiles,
                rocketmqProfiles,
                mongodbProfiles,
                armsProfiles,
                loadBalancerProfiles,
                cdnProfiles
        );
    }

    private static Optional<NamedMcpFocus> resolveFromProfileLists(
            String want,
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            List<String> logsProfiles,
            List<String> mseProfiles,
            List<String> k8sProfiles,
            List<String> starrocksProfiles,
            List<String> kafkaProfiles,
            List<String> rocketmqProfiles,
            List<String> mongodbProfiles,
            List<String> armsProfiles,
            List<String> loadBalancerProfiles,
            List<String> cdnProfiles
    ) {
        if (matchesProfileList(want, mysqlProfiles)) {
            return Optional.of(new NamedMcpFocus(ProfileNames.resolve(want, mysqlProfiles, "MySQL"), ToolKind.MYSQL));
        }
        if (matchesProfileList(want, redisProfiles)) {
            return Optional.of(new NamedMcpFocus(ProfileNames.resolve(want, redisProfiles, "Redis"), ToolKind.REDIS));
        }
        if (matchesProfileList(want, logsProfiles)) {
            return Optional.of(new NamedMcpFocus(ProfileNames.resolve(want, logsProfiles, "SLS"), ToolKind.LOGS));
        }
        if (matchesProfileList(want, mseProfiles)) {
            return Optional.of(new NamedMcpFocus(ProfileNames.resolve(want, mseProfiles, "MSE"), ToolKind.MSE));
        }
        if (matchesProfileList(want, k8sProfiles)) {
            return Optional.of(new NamedMcpFocus(ProfileNames.resolve(want, k8sProfiles, "K8s"), ToolKind.K8S));
        }
        if (matchesProfileList(want, starrocksProfiles)) {
            return Optional.of(new NamedMcpFocus(ProfileNames.resolve(want, starrocksProfiles, "StarRocks"), ToolKind.STARROCKS));
        }
        if (matchesProfileList(want, kafkaProfiles)) {
            return Optional.of(new NamedMcpFocus(ProfileNames.resolve(want, kafkaProfiles, "Kafka"), ToolKind.KAFKA));
        }
        if (matchesProfileList(want, rocketmqProfiles)) {
            return Optional.of(new NamedMcpFocus(ProfileNames.resolve(want, rocketmqProfiles, "RocketMQ"), ToolKind.ROCKETMQ));
        }
        if (matchesProfileList(want, mongodbProfiles)) {
            return Optional.of(new NamedMcpFocus(ProfileNames.resolve(want, mongodbProfiles, "MongoDB"), ToolKind.MONGODB));
        }
        if (matchesProfileList(want, armsProfiles)) {
            return Optional.of(new NamedMcpFocus(ProfileNames.resolve(want, armsProfiles, "ARMS"), ToolKind.ARMS));
        }
        if (matchesProfileList(want, loadBalancerProfiles)) {
            return Optional.of(new NamedMcpFocus(ProfileNames.resolve(want, loadBalancerProfiles, "负载均衡"), ToolKind.LOAD_BALANCER));
        }
        if (matchesProfileList(want, cdnProfiles)) {
            return Optional.of(new NamedMcpFocus(ProfileNames.resolve(want, cdnProfiles, "CDN"), ToolKind.CDN));
        }
        return Optional.empty();
    }

    public static AppliedMcpFocus apply(
            List<ToolBinding> bindings,
            String question,
            String explicitProfile,
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            List<String> logsProfiles,
            List<String> mseProfiles,
            List<String> k8sProfiles,
            List<String> starrocksProfiles,
            List<String> kafkaProfiles,
            List<String> rocketmqProfiles,
            List<String> mongodbProfiles,
            List<String> armsProfiles,
            List<String> loadBalancerProfiles,
            List<String> cdnProfiles
    ) {
        Optional<NamedMcpFocus> focus = resolve(
                bindings,
                question,
                explicitProfile,
                mysqlProfiles,
                redisProfiles,
                logsProfiles,
                mseProfiles,
                k8sProfiles,
                starrocksProfiles,
                kafkaProfiles,
                rocketmqProfiles,
                mongodbProfiles,
                armsProfiles,
                loadBalancerProfiles,
                cdnProfiles
        );
        if (focus.isEmpty()) {
            return new AppliedMcpFocus(
                    mysqlProfiles,
                    redisProfiles,
                    logsProfiles,
                    mseProfiles,
                    k8sProfiles,
                    starrocksProfiles,
                    kafkaProfiles,
                    rocketmqProfiles,
                    mongodbProfiles,
                    armsProfiles,
                    loadBalancerProfiles,
                    cdnProfiles,
                    Optional.empty()
            );
        }
        FocusedProfiles narrowed = focus.get().narrow(
                mysqlProfiles,
                redisProfiles,
                logsProfiles,
                mseProfiles,
                k8sProfiles,
                starrocksProfiles,
                kafkaProfiles,
                rocketmqProfiles,
                mongodbProfiles,
                armsProfiles,
                loadBalancerProfiles,
                cdnProfiles
        );
        return new AppliedMcpFocus(
                narrowed.mysqlProfiles(),
                narrowed.redisProfiles(),
                narrowed.logsProfiles(),
                narrowed.mseProfiles(),
                narrowed.k8sProfiles(),
                narrowed.starrocksProfiles(),
                narrowed.kafkaProfiles(),
                narrowed.rocketmqProfiles(),
                narrowed.mongodbProfiles(),
                narrowed.armsProfiles(),
                narrowed.loadBalancerProfiles(),
                narrowed.cdnProfiles(),
                focus
        );
    }

    public FocusedProfiles narrow(
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            List<String> logsProfiles,
            List<String> mseProfiles,
            List<String> k8sProfiles,
            List<String> starrocksProfiles,
            List<String> kafkaProfiles,
            List<String> rocketmqProfiles,
            List<String> mongodbProfiles,
            List<String> armsProfiles,
            List<String> loadBalancerProfiles,
            List<String> cdnProfiles
    ) {
        return new FocusedProfiles(
                pick(toolKind, ToolKind.MYSQL, profile, mysqlProfiles),
                pick(toolKind, ToolKind.REDIS, profile, redisProfiles),
                pick(toolKind, ToolKind.LOGS, profile, logsProfiles),
                pick(toolKind, ToolKind.MSE, profile, mseProfiles),
                pick(toolKind, ToolKind.K8S, profile, k8sProfiles),
                pick(toolKind, ToolKind.STARROCKS, profile, starrocksProfiles),
                pick(toolKind, ToolKind.KAFKA, profile, kafkaProfiles),
                pick(toolKind, ToolKind.ROCKETMQ, profile, rocketmqProfiles),
                pick(toolKind, ToolKind.MONGODB, profile, mongodbProfiles),
                pick(toolKind, ToolKind.ARMS, profile, armsProfiles),
                pick(toolKind, ToolKind.LOAD_BALANCER, profile, loadBalancerProfiles),
                pick(toolKind, ToolKind.CDN, profile, cdnProfiles)
        );
    }

    private static List<String> pick(ToolKind focusKind, ToolKind kind, String focusProfile, List<String> original) {
        if (focusKind != kind) {
            return new ArrayList<>();
        }
        List<String> narrowed = new ArrayList<>();
        narrowed.add(focusProfile);
        return narrowed;
    }

    private static boolean matchesProfileList(String requested, List<String> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return false;
        }
        for (String profile : profiles) {
            if (profileMatches(profile, requested)) {
                return true;
            }
        }
        return false;
    }

    private static boolean profileMatches(String bound, String requested) {
        if (bound == null || requested == null) {
            return false;
        }
        String want = requested.trim();
        if (bound.equalsIgnoreCase(want)) {
            return true;
        }
        return ProfileNames.normalize(bound).equals(ProfileNames.normalize(want));
    }

    public record AppliedMcpFocus(
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            List<String> logsProfiles,
            List<String> mseProfiles,
            List<String> k8sProfiles,
            List<String> starrocksProfiles,
            List<String> kafkaProfiles,
            List<String> rocketmqProfiles,
            List<String> mongodbProfiles,
            List<String> armsProfiles,
            List<String> loadBalancerProfiles,
            List<String> cdnProfiles,
            Optional<NamedMcpFocus> focus
    ) {
    }

    public record FocusedProfiles(
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            List<String> logsProfiles,
            List<String> mseProfiles,
            List<String> k8sProfiles,
            List<String> starrocksProfiles,
            List<String> kafkaProfiles,
            List<String> rocketmqProfiles,
            List<String> mongodbProfiles,
            List<String> armsProfiles,
            List<String> loadBalancerProfiles,
            List<String> cdnProfiles
    ) {
    }
}
