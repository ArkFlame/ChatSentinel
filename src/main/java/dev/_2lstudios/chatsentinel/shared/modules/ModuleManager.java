package dev._2lstudios.chatsentinel.shared.modules;

import dev._2lstudios.chatsentinel.shared.filter.FilterCompileReport;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompileStatus;
import dev._2lstudios.chatsentinel.shared.text.WarningDeliverySettings;

public abstract class ModuleManager {
	private CapitalizationModule capitalizationModule;
	private CooldownModerationModule cooldownModule;
	private FloodModerationModule floodModule;
	private MessagesModule messagesModule;
	private GeneralModule generalModule;
	private AllowedCharactersModule allowedCharactersModule;
	private BlacklistModerationModule blacklistModule;
	private SyntaxModerationModule syntaxModule;
	private WhitelistModule whitelistModule;
	private DiscordWebhookModule discordWebhookModule;
	private ChatSnapshotModule chatSnapshotModule;
	private ServerMuteModule serverMuteModule;
	private SpyModule spyModule;
	private SocialSpyModule socialSpyModule;
	private NoMoveChatModule noMoveChatModule;
	private CorrectionModule correctionModule;
	private SimilarityModerationModule similarityModule;
	private FilterCompileReport lastCompileReport;
	private boolean filterCompileNotifyAdmins = true;
	private String filterCompileNotifyPermission = "chatsentinel.admin";
	private WarningDeliverySettings warningDeliverySettings = new WarningDeliverySettings(true, true);
	private TextModerationSettings textModerationSettings = new TextModerationSettings(true, true, true, true);

	public ModuleManager() {
		this.capitalizationModule = new CapitalizationModule();
		this.cooldownModule = new CooldownModerationModule();
		this.floodModule = new FloodModerationModule();
		this.blacklistModule = new BlacklistModerationModule(this);
		this.syntaxModule = new SyntaxModerationModule();
		this.messagesModule = new MessagesModule();
		this.generalModule = new GeneralModule();
		this.allowedCharactersModule = new AllowedCharactersModule();
		this.whitelistModule = new WhitelistModule();
		this.discordWebhookModule = new DiscordWebhookModule();
		this.chatSnapshotModule = new ChatSnapshotModule();
		this.spyModule = new SpyModule();
		this.socialSpyModule = new SocialSpyModule();
		this.serverMuteModule = new ServerMuteModule();
		this.noMoveChatModule = new NoMoveChatModule();
		this.correctionModule = new CorrectionModule();
		this.similarityModule = new SimilarityModerationModule();
	}

	public CooldownModerationModule getCooldownModule() {
		return cooldownModule;
	}

	public CapitalizationModule getCapitalizationModule() {
		return capitalizationModule;
	}

	public FloodModerationModule getFloodModule() {
		return floodModule;
	}

	public BlacklistModerationModule getBlacklistModule() {
		return blacklistModule;
	}

	public SyntaxModerationModule getSyntaxModule() {
		return syntaxModule;
	}

	public MessagesModule getMessagesModule() {
		return messagesModule;
	}

	public GeneralModule getGeneralModule() {
		return generalModule;
	}

	public AllowedCharactersModule getAllowedCharactersModule() {
		return allowedCharactersModule;
	}

	public WhitelistModule getWhitelistModule() {
		return whitelistModule;
	}

	public DiscordWebhookModule getDiscordWebhookModule() {
		return discordWebhookModule;
	}

	public ChatSnapshotModule getChatSnapshotModule() {
		return chatSnapshotModule;
	}

	public ServerMuteModule getServerMuteModule() {
		return serverMuteModule;
	}

	public SpyModule getSpyModule() {
		return spyModule;
	}

	public SocialSpyModule getSocialSpyModule() {
		return socialSpyModule;
	}

	public NoMoveChatModule getNoMoveChatModule() {
		return noMoveChatModule;
	}

	public CorrectionModule getCorrectionModule() {
		return correctionModule;
	}

	public SimilarityModerationModule getSimilarityModule() {
		return similarityModule;
	}

	public void reloadData() {
		reloadData(null);
	}

	public abstract void reloadData(FilterCompileStatus status);

	public FilterCompileReport getLastCompileReport() {
		return lastCompileReport;
	}

	public boolean isFilterCompileNotifyAdmins() {
		return filterCompileNotifyAdmins;
	}

	public String getFilterCompileNotifyPermission() {
		return filterCompileNotifyPermission;
	}

	public WarningDeliverySettings getWarningDeliverySettings() {
		return warningDeliverySettings;
	}

	public TextModerationSettings getTextModerationSettings() {
		return textModerationSettings;
	}

	protected void setLastCompileReport(FilterCompileReport lastCompileReport) {
		this.lastCompileReport = lastCompileReport;
	}

	protected void loadFilterCompileNotification(boolean notifyAdmins, String notifyPermission) {
		this.filterCompileNotifyAdmins = notifyAdmins;
		this.filterCompileNotifyPermission = notifyPermission == null || notifyPermission.trim().isEmpty()
				? "chatsentinel.admin"
				: notifyPermission;
	}

	protected void loadWarningDelivery(boolean messageEnabled, boolean actionBarEnabled) {
		this.warningDeliverySettings = new WarningDeliverySettings(messageEnabled, actionBarEnabled);
	}

	protected void loadTextModeration(boolean signs, boolean anvils, boolean books, boolean packetEvents) {
		this.textModerationSettings = new TextModerationSettings(signs, anvils, books, packetEvents);
	}
}
