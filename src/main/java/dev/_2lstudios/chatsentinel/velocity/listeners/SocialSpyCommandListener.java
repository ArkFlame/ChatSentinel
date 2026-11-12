package dev._2lstudios.chatsentinel.velocity.listeners;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import dev._2lstudios.chatsentinel.shared.chat.ChatModerationApplication;
import dev._2lstudios.chatsentinel.shared.chat.ChatModerationDecision;
import dev._2lstudios.chatsentinel.shared.modules.GeneralModule;
import dev._2lstudios.chatsentinel.shared.socialspy.SocialSpyService;
import dev._2lstudios.chatsentinel.velocity.ChatSentinel;
import dev._2lstudios.chatsentinel.velocity.platform.VelocityChatUser;

public final class SocialSpyCommandListener {
    private final ChatSentinel plugin;
    private final SocialSpyService socialSpyService;

    public SocialSpyCommandListener(final ChatSentinel plugin, final SocialSpyService socialSpyService) {
        this.plugin = plugin;
        this.socialSpyService = socialSpyService;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onCommandExecute(final CommandExecuteEvent event) {
        final CommandSource source = event.getCommandSource();
        if (!(source instanceof Player)) {
            return;
        }
        final Player player = (Player) source;
        final VelocityChatUser chatUser = new VelocityChatUser(player, plugin.getMessageSink());
        final String command = event.getCommand();
        final String message = command.startsWith("/") ? command : "/" + command;
        final GeneralModule generalModule = plugin.getModuleManager().getGeneralModule();

        if (generalModule.isCommand(message)) {
            final ChatModerationDecision decision = plugin.getChatEventProcessor().process(chatUser, message, true);
            if (decision.getAction().isTerminal()) {
                event.setResult(CommandExecuteEvent.CommandResult.denied());
                final ChatModerationApplication application = ChatModerationApplication.forCommand(decision);
                for (String feedback : application.getSenderMessages()) {
                    chatUser.sendMessage(feedback);
                }
                return;
            }
            if (decision.getAction().rewritesMessage()) {
                final String rewritten = withoutSlash(decision.getMessage());
                event.setResult(CommandExecuteEvent.CommandResult.command(rewritten));
                socialSpyService.publishCommand(chatUser, rewritten);
                return;
            }
        }
        socialSpyService.publishCommand(chatUser, command);
    }

    private String withoutSlash(final String value) {
        if (value == null) {
            return "";
        }
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
