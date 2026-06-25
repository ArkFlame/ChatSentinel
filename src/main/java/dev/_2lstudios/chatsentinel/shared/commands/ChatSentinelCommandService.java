package dev._2lstudios.chatsentinel.shared.commands;

import dev._2lstudios.chatsentinel.shared.chat.ChatNotificationManager;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayerManager;
import dev._2lstudios.chatsentinel.shared.config.MutableModuleConfigStore;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompileError;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompileReport;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompileStatus;
import dev._2lstudios.chatsentinel.shared.filter.UserRegexAddService;
import dev._2lstudios.chatsentinel.shared.modules.MessagesModule;
import dev._2lstudios.chatsentinel.shared.modules.ModerationModule;
import dev._2lstudios.chatsentinel.shared.modules.ModuleManager;
import dev._2lstudios.chatsentinel.shared.platform.ChatPlatform;
import dev._2lstudios.chatsentinel.shared.platform.ChatUser;
import dev._2lstudios.chatsentinel.shared.platform.CommandActor;
import dev._2lstudios.chatsentinel.shared.socialspy.SocialSpyModuleId;
import dev._2lstudios.chatsentinel.shared.socialspy.SocialSpyService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ChatSentinelCommandService {
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "reload", "status", "selftest", "clear", "notify", "spy",
            "module", "regex", "mutechat", "servermute", "muteall", "muteserver", "autocorrect", "correction", "socialspy", "sspy");
    private static final List<String> ON_OFF_TOGGLE_MODES = Arrays.asList("on", "off", "toggle");
    private static final List<String> SOCIALSPY_ARGUMENTS = Arrays.asList("status", "messages", "signs", "books", "anvils", "commands");

    private final ModuleManager moduleManager;
    private final ChatPlayerManager chatPlayerManager;
    private final ChatNotificationManager chatNotificationManager;
    private final ChatPlatform platform;
    private final UserRegexAddService regexAddService;
    private final MutableModuleConfigStore configStore;
    private final SocialSpyService socialSpyService;

    public ChatSentinelCommandService(final ModuleManager moduleManager, final ChatPlayerManager chatPlayerManager,
            final ChatNotificationManager chatNotificationManager, final ChatPlatform platform,
            final UserRegexAddService regexAddService, final MutableModuleConfigStore configStore,
            final SocialSpyService socialSpyService) {
        this.moduleManager = moduleManager;
        this.chatPlayerManager = chatPlayerManager;
        this.chatNotificationManager = chatNotificationManager;
        this.platform = platform;
        this.regexAddService = regexAddService;
        this.configStore = configStore;
        this.socialSpyService = socialSpyService;
    }

    public CommandResult execute(final CommandActor actor, final String label, final String[] args) {
        final String normalizedLabel = normalizeModuleId(label);
        if ("autocorrect".equals(normalizedLabel) || "correction".equals(normalizedLabel)) {
            handleAutocorrect(actor, args, actor.getLocale(), 0);
            return CommandResult.handled();
        }
        if (isServerMuteAlias(normalizedLabel)) {
            handleServerMute(actor, args, actor.getLocale(), 0, "/" + normalizedLabel);
            return CommandResult.handled();
        }
        if ("socialspy".equals(normalizedLabel) || "sspy".equals(normalizedLabel)) {
            handleSocialSpy(actor, args, actor.getLocale(), 0);
            return CommandResult.handled();
        }
        return execute(actor, args);
    }

    public CommandResult execute(final CommandActor actor, final String[] args) {
        final String lang = actor.getLocale();
        final MessagesModule messagesModule = moduleManager.getMessagesModule();
        final String subcommand = args.length == 0 ? "help" : args[0].toLowerCase();

        if ("help".equals(subcommand)) {
            handleHelp(actor, lang);
        } else if ("status".equals(subcommand)) {
            handleStatus(actor, lang);
        } else if ("selftest".equals(subcommand)) {
            handleSelfTest(actor, lang);
        } else if ("reload".equals(subcommand)) {
            handleReload(actor, lang);
        } else if ("clear".equals(subcommand)) {
            handleClear(actor, args, lang);
        } else if ("notify".equals(subcommand)) {
            handleNotify(actor, lang);
        } else if ("spy".equals(subcommand)) {
            handleSpy(actor, lang);
        } else if ("module".equals(subcommand)) {
            handleModule(actor, args, lang);
        } else if ("regex".equals(subcommand)) {
            handleRegex(actor, args, lang);
        } else if ("mutechat".equals(subcommand) || isServerMuteAlias(subcommand)) {
            handleServerMute(actor, args, lang, 1, "/chatsentinel " + subcommand);
        } else if ("autocorrect".equals(subcommand) || "correction".equals(subcommand)) {
            handleAutocorrect(actor, args, lang, 1);
        } else if ("socialspy".equals(subcommand) || "sspy".equals(subcommand)) {
            handleSocialSpy(actor, args, lang, 1);
        } else {
            actor.sendMessage(messagesModule.getUnknownCommand(lang));
        }
        return CommandResult.handled();
    }

    public List<String> suggest(final CommandActor actor, final String[] args) {
        return suggest(actor, "chatsentinel", args);
    }

    public List<String> suggest(final CommandActor actor, final String label, final String[] args) {
        final String normalizedLabel = normalizeModuleId(label);
        if ("autocorrect".equals(normalizedLabel) || "correction".equals(normalizedLabel)) {
            return suggestModes(args, 0, ON_OFF_TOGGLE_MODES);
        }
        if (isServerMuteAlias(normalizedLabel)) {
            return suggestModes(args, 0, ON_OFF_TOGGLE_MODES);
        }
        if ("socialspy".equals(normalizedLabel) || "sspy".equals(normalizedLabel)) {
            return suggestModes(args, 0, SOCIALSPY_ARGUMENTS);
        }

        if (args.length == 2 && ("socialspy".equalsIgnoreCase(args[0]) || "sspy".equalsIgnoreCase(args[0]))) {
            return suggestModes(args, 1, SOCIALSPY_ARGUMENTS);
        }

        if (args.length == 2 && ("mutechat".equalsIgnoreCase(args[0]) || isServerMuteAlias(args[0]))) {
            return suggestModes(args, 1, ON_OFF_TOGGLE_MODES);
        }

        final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        final List<String> result = new ArrayList<String>();
        for (String subcommand : SUBCOMMANDS) {
            if (subcommand.startsWith(prefix) && hasPermission(actor, permissionFor(subcommand))) {
                result.add(subcommand);
            }
        }
        return result;
    }

    private List<String> suggestModes(final String[] args, final int modeIndex, final List<String> modes) {
        final String prefix = args.length <= modeIndex ? "" : args[modeIndex].toLowerCase(Locale.ROOT);
        final List<String> result = new ArrayList<String>();
        for (String mode : modes) {
            if (mode.startsWith(prefix)) {
                result.add(mode);
            }
        }
        return result;
    }

    private void sendIfPermitted(final CommandActor actor, final String permission, final String message) {
        if (!hasPermission(actor, permission)) {
            actor.sendMessage(moduleManager.getMessagesModule().getNoPermission(actor.getLocale()));
            return;
        }
        actor.sendMessage(message);
    }

    private void handleHelp(final CommandActor actor, final String lang) {
        if (!hasPermission(actor, CommandPermission.HELP)) {
            actor.sendMessage(moduleManager.getMessagesModule().getNoPermission(lang));
            return;
        }
        actor.sendMessage("&aChatSentinel commands:");
        sendHelpLine(actor, CommandPermission.HELP, "&e/chatsentinel help &7- &bShows this help message.");
        sendHelpLine(actor, CommandPermission.RELOAD, "&e/chatsentinel reload &7- &bReloads config, messages, filters, and online-player cache.");
        sendHelpLine(actor, CommandPermission.HELP, "&e/chatsentinel status &7- &bShows module and filter status.");
        sendHelpLine(actor, CommandPermission.SELFTEST, "&e/chatsentinel selftest &7- &bTests command, player, and warning message delivery.");
        sendHelpLine(actor, CommandPermission.CLEAR, "&e/chatsentinel clear [reason] &7- &bClears visible chat for non-bypass players.");
        sendHelpLine(actor, CommandPermission.NOTIFY, "&e/chatsentinel notify &7- &bToggles moderation notifications.");
        sendHelpLine(actor, moduleManager.getSpyModule().getPermission(), "&e/chatsentinel spy &7- &bToggles spy alerts.");
        sendHelpLine(actor, CommandPermission.MODULE, "&e/chatsentinel module list|enable|disable|toggle <moduleId> &7- &bManages modules live.");
        sendHelpLine(actor, CommandPermission.REGEX_ADD, "&e/chatsentinel regex add <moduleId> common|raw <text|regex> &7- &bAdds user blacklist regex.");
        sendHelpLine(actor, CommandPermission.MUTE, "&e/servermute [on|off|toggle] [reason] &7- &bMutes or unmutes server chat.");
        sendHelpLine(actor, CommandPermission.MUTE, "&e/chatsentinel mutechat [on|off|toggle] [reason] &7- &bSame as /servermute.");
        sendHelpLine(actor, CommandPermission.SOCIALSPY, "&e/socialspy [module|status] &7- &bToggles SocialSpy modules.");
        sendHelpLine(actor, CommandPermission.HELP, "&e/autocorrect [on|off|toggle] &7- &bToggles personal correction.");
    }

    private void sendHelpLine(final CommandActor actor, final String permission, final String line) {
        if (hasPermission(actor, permission)) {
            actor.sendMessage(line);
        }
    }

    private void handleStatus(final CommandActor actor, final String lang) {
        if (!hasPermission(actor, CommandPermission.HELP)) {
            actor.sendMessage(moduleManager.getMessagesModule().getNoPermission(lang));
            return;
        }
        actor.sendMessage("&aChatSentinel status:");
        actor.sendMessage("&7Platform: &f" + platform.getPlatformName());
        actor.sendMessage("&7Filters: &f" + filterSummary());
        actor.sendMessage(moduleList());
    }

    private void handleSelfTest(final CommandActor actor, final String lang) {
        if (!hasPermission(actor, CommandPermission.SELFTEST)) {
            actor.sendMessage(moduleManager.getMessagesModule().getNoPermission(lang));
            return;
        }
        actor.sendMessage("&a&lCS: &7Command message delivery works. &eIf you can see this, command output is fixed.");
        final ChatUser user = actor.asUserOrNull();
        if (user != null) {
            user.sendMessage("&b&lCS: &7Player message delivery works. &eIf you can see this, player sendMessage is fixed.");
            user.sendWarning("&6&lCS: &eWarning delivery works. &7This may also appear in actionbar depending config.", moduleManager.getWarningDeliverySettings());
        }
    }

    private String filterSummary() {
        if (moduleManager.getLastCompileReport() == null) {
            return "not compiled";
        }
        FilterCompileReport report = moduleManager.getLastCompileReport();
        return report.getFilesCompiled() + " files, "
                + report.getExpressionsTotal() + " expressions, "
                + report.getErrors().size() + " errors";
    }

    private void handleReload(final CommandActor actor, final String lang) {
        if (!hasPermission(actor, CommandPermission.RELOAD)) {
            actor.sendMessage(moduleManager.getMessagesModule().getNoPermission(lang));
            return;
        }
        moduleManager.reloadData(createStatus(actor));
        platform.refreshOnlinePlayers(chatPlayerManager, chatNotificationManager, moduleManager.getGeneralModule());
        sendCompileErrors(actor, moduleManager.getLastCompileReport());
        actor.sendMessage(moduleManager.getMessagesModule().getReload(lang));
    }

    private void handleClear(final CommandActor actor, final String[] args, final String lang) {
        if (!hasPermission(actor, CommandPermission.CLEAR)) {
            actor.sendMessage(moduleManager.getMessagesModule().getNoPermission(lang));
            return;
        }
        final String reason = args.length > 1 ? joinArgs(args, 1) : "No reason specified";
        final String[][] placeholders = placeholders(actor.getName(), reason, "0", "0", "");
        final String clearedMessage = moduleManager.getMessagesModule().getCleared(placeholders, lang);
        final String payload = moduleManager.getChatSnapshotModule().buildClearPayload(clearedMessage);
        int cleared = 0;
        int bypassed = 0;
        for (ChatUser user : platform.getOnlineUsers()) {
            if (user.hasPermission(CommandPermission.CLEAR_BYPASS)) {
                user.sendMessage(moduleManager.getMessagesModule().getClearBypassNotice(placeholders, user.getLocale()));
                bypassed++;
            } else {
                user.sendMessage(payload);
                cleared++;
            }
        }
        actor.sendMessage(moduleManager.getMessagesModule().getClearSenderSummary(
                placeholders(actor.getName(), reason, String.valueOf(cleared), String.valueOf(bypassed), ""), lang));
    }

    private void handleNotify(final CommandActor actor, final String lang) {
        if (!hasPermission(actor, CommandPermission.NOTIFY)) {
            actor.sendMessage(moduleManager.getMessagesModule().getNoPermission(lang));
            return;
        }
        final ChatUser user = actor.asUserOrNull();
        if (user == null) {
            actor.sendMessage(moduleManager.getMessagesModule().getUnknownCommand(lang));
            return;
        }
        final ChatPlayer chatPlayer = chatPlayerManager.getPlayer(user);
        if (chatNotificationManager.containsPlayer(chatPlayer)) {
            chatNotificationManager.removePlayer(chatPlayer);
            actor.sendMessage(moduleManager.getMessagesModule().getNotifyDisabled(lang));
        } else {
            chatNotificationManager.addPlayer(chatPlayer);
            actor.sendMessage(moduleManager.getMessagesModule().getNotifyEnabled(lang));
        }
    }

    private void handleSpy(final CommandActor actor, final String lang) {
        if (!hasPermission(actor, moduleManager.getSpyModule().getPermission())) {
            actor.sendMessage(moduleManager.getMessagesModule().getNoPermission(lang));
            return;
        }
        final ChatUser user = actor.asUserOrNull();
        if (user == null) {
            actor.sendMessage("Only players can use spy.");
            return;
        }
        final ChatPlayer chatPlayer = chatPlayerManager.getPlayer(user);
        chatPlayer.setSpy(!chatPlayer.isSpy());
        actor.sendMessage("Spy " + (chatPlayer.isSpy() ? "enabled" : "disabled") + ".");
    }

    private void handleModule(final CommandActor actor, final String[] args, final String lang) {
        if (!hasPermission(actor, CommandPermission.MODULE)) {
            actor.sendMessage(moduleManager.getMessagesModule().getNoPermission(lang));
            return;
        }
        if (args.length == 2 && "list".equalsIgnoreCase(args[1])) {
            actor.sendMessage(moduleList());
            return;
        }
        if (args.length == 3 && "toggle".equalsIgnoreCase(args[1])) {
            final String path = modulePath(args[2]);
            if (path == null) {
                actor.sendMessage("Unknown module: " + args[2]);
                return;
            }
            final boolean currentlyEnabled = isModuleEnabled(args[2]);
            final boolean newEnabled = !currentlyEnabled;
            try {
                configStore.setBoolean(path, newEnabled);
                moduleManager.reloadData(createStatus(actor));
                platform.refreshOnlinePlayers(chatPlayerManager, chatNotificationManager, moduleManager.getGeneralModule());
                actor.sendMessage("Module " + args[2].toLowerCase() + " " + (newEnabled ? "enabled" : "disabled") + ".");
            } catch (IOException exception) {
                actor.sendMessage("Module save failed: " + exception.getMessage());
            }
            return;
        }
        if (args.length == 3 && ("enable".equalsIgnoreCase(args[1]) || "disable".equalsIgnoreCase(args[1]))) {
            final boolean enabled = "enable".equalsIgnoreCase(args[1]);
            final String path = modulePath(args[2]);
            if (path == null) {
                actor.sendMessage("Unknown module: " + args[2]);
                return;
            }
            try {
                configStore.setBoolean(path, enabled);
                moduleManager.reloadData(createStatus(actor));
                actor.sendMessage("Module " + args[2].toLowerCase() + " " + (enabled ? "enabled" : "disabled") + ".");
            } catch (IOException exception) {
                actor.sendMessage("Module save failed: " + exception.getMessage());
            }
            return;
        }
        actor.sendMessage("Usage: /chatsentinel module list|enable|disable|toggle <moduleId>");
    }

    private boolean isModuleEnabled(final String moduleId) {
        final String id = normalizeModuleId(moduleId);
        if ("blacklist".equals(id)) return moduleManager.getBlacklistModule().isEnabled();
        if ("capitalization".equals(id) || "caps".equals(id)) return moduleManager.getCapitalizationModule().isEnabled();
        if ("cooldown".equals(id)) return moduleManager.getCooldownModule().isEnabled();
        if ("flood".equals(id)) return moduleManager.getFloodModule().isEnabled();
        if ("syntax".equals(id)) return moduleManager.getSyntaxModule().isEnabled();
        if ("whitelist".equals(id)) return moduleManager.getWhitelistModule().isEnabled();
        if ("server-mute".equals(id)) return moduleManager.getServerMuteModule().isEnabled();
        if ("correction".equals(id) || "autocorrect".equals(id)) return moduleManager.getCorrectionModule().isEnabled();
        if ("similarity".equals(id)) return moduleManager.getSimilarityModule().isEnabled();
        if (id.startsWith("blacklist/")) {
            String submoduleId = id.substring("blacklist/".length());
            if (moduleManager.getBlacklistModule().getSettingsRegistry().moduleIds().contains(submoduleId)) {
                return moduleManager.getBlacklistModule().getSettingsRegistry().resolve(submoduleId).isEnabled();
            }
        }
        return false;
    }

    private void handleRegex(final CommandActor actor, final String[] args, final String lang) {
        if (!hasPermission(actor, CommandPermission.REGEX_ADD)) {
            actor.sendMessage(moduleManager.getMessagesModule().getNoPermission(lang));
            return;
        }
        if (args.length < 5 || !"add".equalsIgnoreCase(args[1])) {
            actor.sendMessage("Usage: /chatsentinel regex add <moduleId> common <plain...>|raw <regex...>");
            return;
        }
        try {
            final String expression = "common".equalsIgnoreCase(args[3])
                    ? regexAddService.addCommon(args[2], joinArgs(args, 4))
                    : "raw".equalsIgnoreCase(args[3]) ? regexAddService.addRaw(args[2], joinArgs(args, 4)) : null;
            if (expression == null) {
                actor.sendMessage("Usage: /chatsentinel regex add <moduleId> common <plain...>|raw <regex...>");
                return;
            }
            moduleManager.reloadData(createStatus(actor));
            sendCompileErrors(actor, moduleManager.getLastCompileReport());
            actor.sendMessage("Added regex to module " + args[2] + ": " + expression);
        } catch (IllegalArgumentException | IOException exception) {
            actor.sendMessage("Regex add failed: " + exception.getMessage());
        }
    }

    private void handleServerMute(final CommandActor actor, final String[] args, final String lang,
            final int modeIndex, final String usageCommand) {
        if (!hasPermission(actor, CommandPermission.MUTE)) {
            actor.sendMessage(moduleManager.getMessagesModule().getNoPermission(lang));
            return;
        }

        final String mode = args.length <= modeIndex ? "toggle" : args[modeIndex].toLowerCase(Locale.ROOT);
        final boolean muted;
        if ("on".equals(mode)) {
            muted = true;
        } else if ("off".equals(mode)) {
            muted = false;
        } else if ("toggle".equals(mode)) {
            muted = !moduleManager.getServerMuteModule().isMuted();
        } else {
            actor.sendMessage("Usage: " + usageCommand + " [on|off|toggle] [reason...]");
            return;
        }

        final String reason = args.length > modeIndex + 1 ? joinArgs(args, modeIndex + 1) : "No reason specified";
        try {
            configStore.setBoolean("server-mute.muted", muted);
            moduleManager.getServerMuteModule().setMuted(muted);
            final String[][] placeholders = new String[][] {
                    { "%player%", "%reason%" },
                    { actor.getName(), reason }
            };
            final String message = muted
                    ? moduleManager.getMessagesModule().getServerMuteEnabled(placeholders, lang)
                    : moduleManager.getMessagesModule().getServerMuteDisabled(placeholders, lang);
            broadcastServerMuteChange(actor, message);
        } catch (IOException exception) {
            actor.sendMessage("Server mute save failed: " + exception.getMessage());
        }
    }

    private void broadcastServerMuteChange(final CommandActor actor, final String message) {
        boolean actorReceived = false;
        final ChatUser actorUser = actor.asUserOrNull();
        for (ChatUser user : platform.getOnlineUsers()) {
            user.sendMessage(message);
            if (actorUser != null && actorUser.getUniqueId().equals(user.getUniqueId())) {
                actorReceived = true;
            }
        }
        if (!actorReceived) {
            actor.sendMessage(message);
        }
        platform.sendConsoleMessage(message);
    }

    private boolean hasPermission(final CommandActor actor, final String permission) {
        return actor.hasPermission(permission) || actor.hasPermission(CommandPermission.ADMIN);
    }

    private String permissionFor(final String subcommand) {
        if ("reload".equals(subcommand)) return CommandPermission.RELOAD;
        if ("selftest".equals(subcommand)) return CommandPermission.SELFTEST;
        if ("clear".equals(subcommand)) return CommandPermission.CLEAR;
        if ("notify".equals(subcommand)) return CommandPermission.NOTIFY;
        if ("module".equals(subcommand)) return CommandPermission.MODULE;
        if ("regex".equals(subcommand)) return CommandPermission.REGEX_ADD;
        if ("mutechat".equals(subcommand) || isServerMuteAlias(subcommand)) return CommandPermission.MUTE;
        if ("spy".equals(subcommand)) return moduleManager.getSpyModule().getPermission();
        if ("socialspy".equals(subcommand) || "sspy".equals(subcommand)) return CommandPermission.SOCIALSPY;
        if ("autocorrect".equals(subcommand) || "correction".equals(subcommand)) return CommandPermission.HELP;
        return CommandPermission.HELP;
    }

    private boolean isServerMuteAlias(final String labelOrSubcommand) {
        final String id = normalizeModuleId(labelOrSubcommand);
        return "servermute".equals(id) || "server-mute".equals(id) || "muteall".equals(id)
                || "mute-all".equals(id) || "muteserver".equals(id) || "mute-server".equals(id);
    }

    private String modulePath(final String moduleId) {
        final String id = normalizeModuleId(moduleId);
        if ("blacklist".equals(id)) return "blacklist.enabled";
        if ("capitalization".equals(id) || "caps".equals(id)) return "capitalization.enabled";
        if ("cooldown".equals(id)) return "cooldown.enabled";
        if ("flood".equals(id)) return "flood.enabled";
        if ("syntax".equals(id)) return "syntax.enabled";
        if ("whitelist".equals(id)) return "whitelist.enabled";
        if ("server-mute".equals(id)) return "server-mute.enabled";
        if ("allowed-characters".equals(id)) return "allowed-characters.enabled";
        if ("no-move-chat".equals(id)) return "no-move-chat.enabled";
        if ("correction".equals(id) || "autocorrect".equals(id)) return "correction.enabled";
        if ("similarity".equals(id)) return "similarity.enabled";
        if (id.startsWith("blacklist/")) return "blacklist.modules." + id.substring("blacklist/".length()) + ".enabled";
        if (moduleManager.getBlacklistModule().getSettingsRegistry().moduleIds().contains(id)) return "blacklist.modules." + id + ".enabled";
        return null;
    }

    private String normalizeModuleId(final String moduleId) {
        return moduleId == null ? "" : moduleId.trim().toLowerCase().replace('_', '-');
    }

    private String moduleList() {
        final StringBuilder builder = new StringBuilder("Modules:");
        appendModule(builder, "blacklist", moduleManager.getBlacklistModule().isEnabled());
        appendModule(builder, "capitalization", moduleManager.getCapitalizationModule().isEnabled());
        appendModule(builder, "cooldown", moduleManager.getCooldownModule().isEnabled());
        appendModule(builder, "flood", moduleManager.getFloodModule().isEnabled());
        appendModule(builder, "syntax", moduleManager.getSyntaxModule().isEnabled());
        appendModule(builder, "whitelist", moduleManager.getWhitelistModule().isEnabled());
        appendModule(builder, "correction", moduleManager.getCorrectionModule().isEnabled());
        builder.append("\n- server-mute: ")
                .append(moduleManager.getServerMuteModule().isEnabled() ? "enabled" : "disabled")
                .append(", state=")
                .append(moduleManager.getServerMuteModule().isMuted() ? "muted" : "open");
        final Set<String> moduleIds = moduleManager.getBlacklistModule().getSettingsRegistry().moduleIds();
        final List<String> sortedIds = new ArrayList<String>(moduleIds);
        Collections.sort(sortedIds);
        for (String moduleId : sortedIds) {
            if (!"default".equals(moduleId)) {
                appendModule(builder, "blacklist/" + moduleId, moduleManager.getBlacklistModule().getSettingsRegistry().resolve(moduleId).isEnabled());
            }
        }
        return builder.toString();
    }

    private void appendModule(final StringBuilder builder, final String moduleId, final boolean enabled) {
        builder.append("\n- ").append(moduleId).append(": ").append(enabled ? "enabled" : "disabled");
    }

    private String[][] placeholders(final String player, final String reason, final String cleared, final String bypassed, final String id) {
        return new String[][] {
                { "%player%", "%reason%", "%cleared%", "%bypassed%", "%id%" },
                { player, reason, cleared, bypassed, id }
        };
    }

    private String joinArgs(final String[] args, final int startIndex) {
        final StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private void handleSocialSpy(final CommandActor actor, final String[] args, final String lang, final int moduleIndex) {
        final ChatUser user = actor.asUserOrNull();
        if (user == null) {
            actor.sendMessage("Only players can use SocialSpy.");
            return;
        }
        if (!socialSpyService.hasAnyPermission(user)) {
            actor.sendMessage(moduleManager.getSocialSpyModule().getNoPermission());
            return;
        }
        if (args.length <= moduleIndex) {
            final boolean enabled = socialSpyService.toggleAllPermitted(user);
            actor.sendMessage(enabled
                    ? moduleManager.getSocialSpyModule().getEnabledAllMessage()
                    : moduleManager.getSocialSpyModule().getDisabledAllMessage());
            return;
        }
        final String requested = args[moduleIndex];
        if ("status".equalsIgnoreCase(requested)) {
            sendLines(actor, socialSpyService.status(user));
            return;
        }
        if (!SocialSpyModuleId.isValid(requested)) {
            actor.sendMessage(moduleManager.getSocialSpyModule().getInvalidModuleMessage(String.join(", ", SocialSpyModuleId.ids())));
            return;
        }
        final String moduleId = SocialSpyModuleId.normalize(requested);
        if (!socialSpyService.hasModulePermission(user, moduleId)) {
            actor.sendMessage(moduleManager.getSocialSpyModule().getNoPermission());
            return;
        }
        final boolean enabled = socialSpyService.toggle(user, moduleId);
        actor.sendMessage(enabled
                ? moduleManager.getSocialSpyModule().getEnabledMessage(moduleId)
                : moduleManager.getSocialSpyModule().getDisabledMessage(moduleId));
    }

    private void sendLines(final CommandActor actor, final String message) {
        final String[] lines = message.split("\\n");
        for (String line : lines) {
            actor.sendMessage(line);
        }
    }

    private FilterCompileStatus createStatus(final CommandActor actor) {
        return new FilterCompileStatus(new FilterCompileStatus.MessageSink() {
            @Override
            public void send(final String message) {
                actor.sendMessage(message);
                platform.sendConsoleMessage(message);
            }
        });
    }

    private void sendCompileErrors(final CommandActor actor, final FilterCompileReport report) {
        if (report == null || !report.hasErrors()) {
            return;
        }
        int sent = 0;
        for (FilterCompileError error : report.getErrors()) {
            final String message = FilterCompileStatus.formatError(error);
            platform.sendConsoleMessage(message);
            if (sent < 5) {
                actor.sendMessage(message);
                sent++;
            }
        }
    }

    private void handleAutocorrect(final CommandActor actor, final String[] args, final String lang, final int modeIndex) {
        final ChatUser user = actor.asUserOrNull();
        if (user == null) {
            actor.sendMessage(moduleManager.getMessagesModule().getCorrectionConsoleOnly(lang));
            return;
        }

        final ChatPlayer chatPlayer = chatPlayerManager.getPlayer(user);
        final String mode = args.length <= modeIndex ? "toggle" : args[modeIndex].toLowerCase();

        final boolean enabled;
        if ("on".equals(mode) || "enable".equals(mode) || "enabled".equals(mode)) {
            enabled = true;
        } else if ("off".equals(mode) || "disable".equals(mode) || "disabled".equals(mode)) {
            enabled = false;
        } else if ("toggle".equals(mode)) {
            enabled = chatPlayer.toggleCorrectionEnabled();
            actor.sendMessage(enabled
                    ? moduleManager.getMessagesModule().getCorrectionEnabled(lang)
                    : moduleManager.getMessagesModule().getCorrectionDisabled(lang));
            return;
        } else {
            actor.sendMessage(moduleManager.getMessagesModule().getCorrectionUsage(lang));
            return;
        }

        chatPlayer.setCorrectionEnabled(enabled);
        actor.sendMessage(enabled
                ? moduleManager.getMessagesModule().getCorrectionEnabled(lang)
                : moduleManager.getMessagesModule().getCorrectionDisabled(lang));
    }
}
