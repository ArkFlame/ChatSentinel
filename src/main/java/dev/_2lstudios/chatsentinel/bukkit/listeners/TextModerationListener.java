package dev._2lstudios.chatsentinel.bukkit.listeners;

import dev._2lstudios.chatsentinel.bukkit.ChatSentinel;
import dev._2lstudios.chatsentinel.bukkit.platform.BukkitChatUser;
import dev._2lstudios.chatsentinel.shared.chat.ChatModerationApplication;
import dev._2lstudios.chatsentinel.shared.chat.ChatModerationDecision;
import dev._2lstudios.chatsentinel.shared.modules.TextModerationSettings;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class TextModerationListener implements Listener {
    private final ChatSentinel plugin;

    public TextModerationListener(final ChatSentinel plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignChange(final SignChangeEvent event) {
        final TextModerationSettings settings = plugin.getModuleManager().getTextModerationSettings();
        if (!settings.isSignsEnabled()) {
            return;
        }
        final StringBuilder text = new StringBuilder();
        for (String line : event.getLines()) {
            if (line == null) {
                continue;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(line);
        }
        if (text.toString().trim().isEmpty()) {
            return;
        }
        if (moderate(event.getPlayer(), text.toString())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        final TextModerationSettings settings = plugin.getModuleManager().getTextModerationSettings();
        if (!settings.isAnvilsEnabled()) {
            return;
        }
        if (event.getRawSlot() != 2) {
            return;
        }
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) {
            return;
        }
        if (!(event.getView().getTopInventory() instanceof AnvilInventory)) {
            return;
        }
        final ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) {
            return;
        }
        final AnvilInventory anvil = (AnvilInventory) event.getView().getTopInventory();
        final String renameText = anvil.getRenameText();
        if (renameText == null || renameText.trim().isEmpty()) {
            return;
        }
        if (renameText.equals(displayNameOrType(anvil.getItem(0)))) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (moderate((Player) event.getWhoClicked(), renameText)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerEditBook(final PlayerEditBookEvent event) {
        final TextModerationSettings settings = plugin.getModuleManager().getTextModerationSettings();
        if (!settings.isBooksEnabled() || !event.isSigning()) {
            return;
        }
        final BookMeta meta = event.getNewBookMeta();
        final StringBuilder text = new StringBuilder();
        final String title = meta.getTitle();
        if (title != null) {
            text.append(title);
        }
        final List<String> pages = meta.getPages();
        for (String page : pages) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(page == null ? "" : page.replace('\n', ' '));
        }
        if (text.toString().trim().isEmpty()) {
            return;
        }
        if (moderate(event.getPlayer(), text.toString())) {
            event.setCancelled(true);
        }
    }

    private boolean moderate(final Player player, final String text) {
        final BukkitChatUser chatUser = new BukkitChatUser(plugin, player, plugin.getMessageSink());
        final ChatModerationDecision decision = plugin.getChatEventProcessor().processText(chatUser, text, true);
        if (!decision.getAction().isTerminal()) {
            return false;
        }
        final ChatModerationApplication application = ChatModerationApplication.forCommand(decision);
        chatUser.sendRequiredMessages(decision.getAction(), decision.getReasonId().orElse(""),
                application.getSenderMessages());
        return true;
    }

    private String displayNameOrType(final ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "";
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return item.getType().name().toLowerCase().replace("_", " ");
    }
}
