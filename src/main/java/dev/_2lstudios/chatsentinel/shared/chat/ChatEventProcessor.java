package dev._2lstudios.chatsentinel.shared.chat;

import dev._2lstudios.chatsentinel.shared.alerts.AlertBus;
import dev._2lstudios.chatsentinel.shared.alerts.AlertPayload;
import dev._2lstudios.chatsentinel.shared.filter.FilterMatch;
import dev._2lstudios.chatsentinel.shared.filter.FilterSource;
import dev._2lstudios.chatsentinel.shared.moderation.ModerationIdentity;
import dev._2lstudios.chatsentinel.shared.moderation.ModerationViolation;
import dev._2lstudios.chatsentinel.shared.modules.AllowedCharactersModule;
import dev._2lstudios.chatsentinel.shared.modules.CooldownModerationModule;
import dev._2lstudios.chatsentinel.shared.modules.DiscordWebhookModule;
import dev._2lstudios.chatsentinel.shared.modules.GeneralModule;
import dev._2lstudios.chatsentinel.shared.modules.MessagesModule;
import dev._2lstudios.chatsentinel.shared.modules.ModerationModule;
import dev._2lstudios.chatsentinel.shared.modules.ModuleManager;
import dev._2lstudios.chatsentinel.shared.modules.NoMoveChatModule;
import dev._2lstudios.chatsentinel.shared.modules.ServerMuteModule;
import dev._2lstudios.chatsentinel.shared.modules.SpyModule;
import dev._2lstudios.chatsentinel.shared.modules.SyntaxModerationModule;
import dev._2lstudios.chatsentinel.shared.modules.CorrectionModule;
import dev._2lstudios.chatsentinel.shared.modules.CorrectionResult;
import dev._2lstudios.chatsentinel.shared.platform.ChatPlatform;
import dev._2lstudios.chatsentinel.shared.platform.ChatUser;
import dev._2lstudios.chatsentinel.shared.utils.PlaceholderUtil;

import java.util.Optional;

public final class ChatEventProcessor {
    private final ModuleManager moduleManager;
    private final ChatPlayerManager chatPlayerManager;
    private final ChatNotificationManager chatNotificationManager;
    private final ChatPlatform platform;
    private final AlertBus alertBus;

    public ChatEventProcessor(final ModuleManager moduleManager, final ChatPlayerManager chatPlayerManager,
            final ChatNotificationManager chatNotificationManager, final ChatPlatform platform, final AlertBus alertBus) {
        this.moduleManager = moduleManager;
        this.chatPlayerManager = chatPlayerManager;
        this.chatNotificationManager = chatNotificationManager;
        this.platform = platform;
        this.alertBus = alertBus;
    }

    public ProcessedChatEvent process(final ChatUser user, final String originalMessage, final boolean enforce) {
        final ChatPlayer chatPlayer = chatPlayerManager.getPlayer(user);
        final MessagesModule messagesModule = moduleManager.getMessagesModule();
        final String playerName = user.getName();
        final String lang = chatPlayer.getLocale();
        final boolean isCommand = originalMessage.startsWith("/");
        final boolean isNormalCommand = moduleManager.getGeneralModule().isCommand(originalMessage);

        if (enforce && (!isCommand || isNormalCommand) && isServerMuted(user, chatPlayer, messagesModule, playerName, originalMessage, lang)) {
            return new ProcessedChatEvent(originalMessage, true, false);
        }

        if (enforce && !isCommand && isNoMoveBlocked(user, chatPlayer, originalMessage, lang)) {
            return new ProcessedChatEvent(originalMessage, true, false);
        }

        final String correctedMessage = applyCorrection(chatPlayer, user, originalMessage, enforce, isCommand, isNormalCommand, lang);
        final ChatEventResult finalResult = new ChatEventResult(correctedMessage, false, false);
        final ModerationModule[] moderationModulesToProcess = {
                moduleManager.getSyntaxModule(),
                moduleManager.getAllowedCharactersModule(),
                moduleManager.getCapitalizationModule(),
                moduleManager.getCooldownModule(),
                moduleManager.getSimilarityModule(),
                moduleManager.getFloodModule(),
                moduleManager.getBlacklistModule()
        };

        for (ModerationModule moderationModule : moderationModulesToProcess) {
            if (shouldSkipModule(moderationModule, user, isCommand, isNormalCommand)) {
                continue;
            }

            final String message = finalResult.getMessage();
            final ChatEventResult result = moderationModule.processEvent(chatPlayer, messagesModule, playerName, message, lang);
            if (result == null) {
                continue;
            }

            if (!enforce) {
                dispatchWarnOnly(user, chatPlayer, moderationModule, originalMessage, message, result);
                continue;
            }

            if (!result.isNotify()) {
                finalResult.setMessage(result.getMessage());
                if (result.isHide()) {
                    finalResult.setHide(true);
                }
                if (isTerminalCancellation(result)) {
                    handleViolation(user, chatPlayer, moderationModule, originalMessage, message, result);
                    if (result.isCancelled()) {
                        finalResult.setCancelled(true);
                    }
                    break;
                }
                continue;
            }

            if (moderationModule instanceof AllowedCharactersModule) {
                dispatchSpy(user, placeholders(user, chatPlayer, moderationModule, originalMessage,
                        moderationModule.getIdentityKey(), moderationModule.getCustomName(), moderationModule.getMaxWarns(),
                        "", moderationModule.getName(), "", result.getMessage()));
                finalResult.setMessage(result.getMessage());
                if (result.isHide()) {
                    finalResult.setHide(true);
                }
                if (isTerminalCancellation(result)) {
                    if (result.isCancelled()) {
                        user.sendMessage(messagesModule.getFiltered(lang));
                        finalResult.setCancelled(true);
                    }
                    break;
                }
                continue;
            }

            handleViolation(user, chatPlayer, moderationModule, originalMessage, message, result);
            finalResult.setMessage(result.getMessage());
            if (result.isHide()) {
                finalResult.setHide(true);
            }
            if (isTerminalCancellation(result)) {
                if (result.isCancelled()) {
                    finalResult.setCancelled(true);
                }
                break;
            }
        }

        return new ProcessedChatEvent(finalResult.getMessage(), finalResult.isCancelled(), finalResult.isHide());
    }

    private boolean isTerminalCancellation(final ChatEventResult result) {
        return result != null && (result.isCancelled() || result.isHide());
    }

    private boolean isServerMuted(final ChatUser user, final ChatPlayer chatPlayer, final MessagesModule messagesModule,
            final String playerName, final String originalMessage, final String lang) {
        final ServerMuteModule serverMuteModule = moduleManager.getServerMuteModule();
        if (hasModuleBypass(user, "server-mute", serverMuteModule.getBypassPermission())) {
            return false;
        }
        final ChatEventResult serverMuteResult = serverMuteModule.processEvent(chatPlayer, messagesModule, playerName, originalMessage, lang);
        if (serverMuteResult == null) {
            return false;
        }
        user.sendMessage(serverMuteResult.getMessage());
        return true;
    }

    private boolean isNoMoveBlocked(final ChatUser user, final ChatPlayer chatPlayer, final String originalMessage, final String lang) {
        final NoMoveChatModule noMoveChatModule = moduleManager.getNoMoveChatModule();
        if (!"Bukkit".equals(platform.getPlatformName())) {
            return false;
        }
        if (hasModuleBypass(user, "no-move-chat", noMoveChatModule.getBypassPermission())) {
            return false;
        }
        final ChatEventResult noMoveChatResult = noMoveChatModule.processEvent(chatPlayer, originalMessage);
        if (noMoveChatResult == null) {
            return false;
        }
        user.sendMessage(moduleManager.getMessagesModule().getNoMoveChatWarnMessage(new String[][] {
                { "%distance%" },
                { String.valueOf(noMoveChatModule.getMinDistanceBlocks()) }
        }, lang));
        return true;
    }

    private String applyCorrection(final ChatPlayer chatPlayer, final ChatUser user, final String originalMessage,
            final boolean enforce, final boolean isCommand, final boolean isNormalCommand, final String lang) {
        final CorrectionModule correctionModule = moduleManager.getCorrectionModule();
        if (!enforce || correctionModule == null || !correctionModule.isEnabled() || !correctionModule.hasReplacements()) {
            return originalMessage;
        }
        if (hasModuleBypass(user, "correction", correctionModule.getBypassPermission())) {
            return originalMessage;
        }
        if (!chatPlayer.isCorrectionEnabled()) {
            return originalMessage;
        }
        if (isCommand && (!isNormalCommand || !correctionModule.isApplyToNormalCommands())) {
            return originalMessage;
        }

        final CorrectionResult correctionResult = correctionModule.correct(originalMessage);
        if (!correctionResult.isCorrected()) {
            return originalMessage;
        }

        if (correctionModule.isNotifyPlayer()) {
            user.sendMessage(moduleManager.getMessagesModule().getCorrectionWarnMessage(new String[][] {
                    { "%corrections%", "%original_message%", "%corrected_message%" },
                    { String.valueOf(correctionResult.getCorrections()), originalMessage, correctionResult.getMessage() }
            }, lang));
        }
        return correctionResult.getMessage();
    }

    private boolean shouldSkipModule(final ModerationModule moderationModule, final ChatUser user,
            final boolean isCommand, final boolean isNormalCommand) {
        if (!(moderationModule instanceof SyntaxModerationModule)
                && !(moderationModule instanceof AllowedCharactersModule)
                && !(moderationModule instanceof CooldownModerationModule)
                && isCommand
                && !isNormalCommand) {
            return true;
        }
        return hasModuleBypass(user, moderationModule.getIdentityKey(), moderationModule.getBypassPermission());
    }

    private boolean hasModuleBypass(final ChatUser user, final String moduleId, final String moduleBypassPermission) {
        if (moduleBypassPermission != null && !moduleBypassPermission.trim().isEmpty()
                && user.hasPermission(moduleBypassPermission)) {
            return true;
        }

        final GeneralModule generalModule = moduleManager.getGeneralModule();
        final String globalBypassPermission = generalModule.getGlobalBypassPermission();
        if (globalBypassPermission == null || globalBypassPermission.trim().isEmpty()) {
            return false;
        }

        return user.hasPermission(globalBypassPermission) && !generalModule.isGlobalBypassExcluded(moduleId);
    }

    private void handleViolation(final ChatUser user, final ChatPlayer chatPlayer, final ModerationModule moderationModule,
            final String originalMessage, final String message, final ChatEventResult result) {
        final ViolationData data = resolveViolationData(moderationModule, result);
        chatPlayer.addWarn(data.identityKey);

        final String[][] placeholders = placeholders(user, chatPlayer, moderationModule, message, data.identityKey,
                data.customModuleName, data.maxWarns, data.sourceFile, data.sourceModule, data.matchedText, result.getMessage());
        final String warnMessage = moduleManager.getMessagesModule().getWarnMessage(placeholders, chatPlayer.getLocale(), moderationModule.getName());
        if (result.isCancelled() || result.isHide()) {
            final String playerMessage = result.getPlayerMessage().orElseGet(new java.util.function.Supplier<String>() {
                @Override
                public String get() {
                    return moduleManager.getMessagesModule().getBlockedMessage(placeholders, chatPlayer.getLocale());
                }
            });
            if (playerMessage != null && !playerMessage.isEmpty()) {
                user.sendMessage(playerMessage);
            }
        } else if (warnMessage != null && !warnMessage.isEmpty()) {
            user.sendWarning(warnMessage, moduleManager.getWarningDeliverySettings());
        }

        if (data.violation != null && chatPlayer.getWarns(data.identityKey) >= data.maxWarns && data.maxWarns > 0) {
            dispatchCommands(data.violation.getActionSettings().getCommands(), placeholders);
            chatPlayer.clearWarns(data.identityKey);
        } else if (data.violation == null && moderationModule.hasExceededWarns(chatPlayer)) {
            dispatchCommands(moderationModule.getCommands(placeholders), placeholders);
            chatPlayer.clearWarns();
        }

        final String notificationMessage = data.violation != null
                ? dispatchNotification(data.violation.getActionSettings().getWarnNotification(), placeholders)
                : dispatchNotification(moderationModule.getWarnNotification(placeholders));
        final String spyMessage = dispatchSpy(user, placeholders(user, chatPlayer, moderationModule, originalMessage, data.identityKey,
                data.customModuleName, data.maxWarns, data.sourceFile, data.sourceModule, data.matchedText, result.getMessage()));
        alertBus.publish(new AlertPayload(null, notificationMessage, spyMessage));

        final DiscordWebhookModule discordWebhookModule = moduleManager.getDiscordWebhookModule();
        if (data.violation == null || data.violation.getActionSettings().isWebhookEnabled()) {
            platform.runAsync(new Runnable() {
                @Override
                public void run() {
                    discordWebhookModule.dispatchWebhookNotification(moderationModule, placeholders);
                }
            });
        }
    }

    private void dispatchWarnOnly(final ChatUser user, final ChatPlayer chatPlayer, final ModerationModule moderationModule,
            final String originalMessage, final String message, final ChatEventResult result) {
        final ViolationData data = resolveViolationData(moderationModule, result);
        final String[][] placeholders = placeholders(user, chatPlayer, moderationModule, message, data.identityKey,
                data.customModuleName, data.maxWarns, data.sourceFile, data.sourceModule, data.matchedText, result.getMessage());
        final String notificationMessage = data.violation != null
                ? dispatchNotification(data.violation.getActionSettings().getWarnNotification(), placeholders)
                : dispatchNotification(moderationModule.getWarnNotification(placeholders));
        final String spyMessage = dispatchSpy(user, placeholders(user, chatPlayer, moderationModule, originalMessage, data.identityKey,
                data.customModuleName, data.maxWarns, data.sourceFile, data.sourceModule, data.matchedText, result.getMessage()));
        alertBus.publish(new AlertPayload(null, notificationMessage, spyMessage));

        final boolean webhookEnabled = data.violation != null
                ? data.violation.getActionSettings().isWebhookEnabled()
                : moderationModule.isWebhookEnabled();
        if (webhookEnabled) {
            platform.runAsync(new Runnable() {
                @Override
                public void run() {
                    moduleManager.getDiscordWebhookModule().dispatchWebhookNotification(moderationModule, placeholders);
                }
            });
        }
    }

    private ViolationData resolveViolationData(final ModerationModule moderationModule, final ChatEventResult result) {
        final ViolationData data = new ViolationData();
        data.violation = result.getViolation().orElse(null);
        data.identityKey = moderationModule.getIdentityKey();
        data.customModuleName = moderationModule.getCustomName();
        data.maxWarns = moderationModule.getMaxWarns();
        data.sourceFile = "";
        data.sourceModule = moderationModule.getName();
        data.sourceModuleId = data.identityKey;
        data.matchedText = "";

        if (data.violation != null) {
            final ModerationIdentity identity = data.violation.getIdentity();
            data.identityKey = identity.getKey();
            data.customModuleName = identity.getDisplayName();
            data.sourceModule = identity.getModuleName();
            data.sourceModuleId = data.identityKey;
            data.maxWarns = data.violation.getActionSettings().getMaxWarns();
            final FilterMatch filterMatch = data.violation.getFilterMatch().orElse(null);
            if (filterMatch != null) {
                final FilterSource source = filterMatch.getSource();
                data.sourceFile = source.getRelativePath();
                data.matchedText = filterMatch.getMatchedText();
            }
        }
        return data;
    }

    private String dispatchNotification(final String notificationMessage) {
        if (notificationMessage == null || notificationMessage.isEmpty()) {
            return notificationMessage;
        }
        for (ChatPlayer notified : chatNotificationManager.getAllPlayers()) {
            if (moduleManager.getSpyModule().isEnabled() && notified.isSpy()) {
                continue;
            }
            final Optional<ChatUser> target = platform.findUser(notified.getUniqueId());
            if (target.isPresent()) {
                target.get().sendMessage(notificationMessage);
            }
        }
        platform.sendConsoleMessage(notificationMessage);
        return notificationMessage;
    }

    private String dispatchNotification(final String warnNotification, final String[][] placeholders) {
        if (warnNotification == null || warnNotification.isEmpty()) {
            return warnNotification;
        }
        return dispatchNotification(PlaceholderUtil.replacePlaceholders(warnNotification, placeholders));
    }

    private String dispatchSpy(final ChatUser sender, final String[][] placeholders) {
        final SpyModule spyModule = moduleManager.getSpyModule();
        if (!spyModule.isEnabled()) {
            return null;
        }

        final String spyMessage = spyModule.format(placeholders);
        if (spyMessage == null || spyMessage.isEmpty()) {
            return spyMessage;
        }
        for (ChatPlayer candidate : chatPlayerManager.getAllPlayers()) {
            if (!candidate.isSpy() || candidate.getUniqueId().equals(sender.getUniqueId())) {
                continue;
            }
            final Optional<ChatUser> target = platform.findUser(candidate.getUniqueId());
            if (target.isPresent() && target.get().hasPermission(spyModule.getPermission())) {
                target.get().sendMessage(spyMessage);
            }
        }
        return spyMessage;
    }

    private void dispatchCommands(final String[] commands, final String[][] placeholders) {
        for (String command : commands) {
            platform.dispatchConsoleCommand(PlaceholderUtil.replacePlaceholders(command, placeholders));
        }
    }

    private String[][] placeholders(final ChatUser user, final ChatPlayer chatPlayer, final ModerationModule moderationModule,
            final String message, final String identityKey, final String customModuleName, final int maxWarns,
            final String sourceFile, final String sourceModule, final String matchedText, final String resultMessage) {
        final float remainingTime = moduleManager.getCooldownModule().getRemainingTime(chatPlayer, message);
        final String safeMatchedText = matchedText == null ? "" : matchedText;
        return new String[][] {
                { "%player%", "%module%", "%message%", "%warns%", "%maxwarns%", "%cooldown%", "%server_name%", "%module_id%", "%source_file%", "%source_module%", "%matched_text%", "%word%", "%result_message%" },
                { user.getName(), customModuleName, message, String.valueOf(chatPlayer.getWarns(identityKey)), String.valueOf(maxWarns), String.valueOf(remainingTime), user.getServerName(), identityKey, sourceFile, sourceModule, safeMatchedText, safeMatchedText, resultMessage }
        };
    }

    private static final class ViolationData {
        private ModerationViolation violation;
        private String identityKey;
        private String customModuleName;
        private int maxWarns;
        private String sourceFile;
        private String sourceModule;
        private String sourceModuleId;
        private String matchedText;
    }
}
