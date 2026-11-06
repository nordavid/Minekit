package io.github.nordavid.minekit.feature.quickactions;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.core.action.QuickActionRegistry;
import io.github.nordavid.minekit.core.input.KeybindManager;
import io.github.nordavid.minekit.core.module.MinekitModule;
import io.github.nordavid.minekit.core.module.ModuleContext;
import io.github.nordavid.minekit.core.module.ModuleMetadata;
import io.github.nordavid.minekit.core.notification.NotificationService;
import io.github.nordavid.minekit.core.persistence.JsonFileStore;
import io.github.nordavid.minekit.core.screen.DashboardPages;
import java.util.Objects;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * D11 Quick Actions: a searchable popup, opened with a shortcut, that runs common MINEKIT actions. The actions
 * themselves live in the shared {@link QuickActionRegistry}, where every module can contribute. The player pins,
 * orders and hides actions ({@link QuickActionSettings}) in the popup and on the dashboard page.
 */
public final class QuickActionsModule implements MinekitModule {
	public static final ModuleMetadata METADATA = new ModuleMetadata("quick_actions",
			"minekit.module.quick_actions.name", "minekit.module.quick_actions.description", true);

	private final QuickActionRegistry registry;
	private final JsonFileStore<QuickActionSettings> settings;
	private final KeybindManager keybinds;
	private final NotificationService notifications;
	private final DashboardPages dashboardPages;
	private ModuleContext context;
	private KeyMapping key;

	public QuickActionsModule(QuickActionRegistry registry, JsonFileStore<QuickActionSettings> settings, KeybindManager keybinds,
			NotificationService notifications, DashboardPages dashboardPages) {
		this.registry = Objects.requireNonNull(registry, "registry");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.keybinds = Objects.requireNonNull(keybinds, "keybinds");
		this.notifications = Objects.requireNonNull(notifications, "notifications");
		this.dashboardPages = Objects.requireNonNull(dashboardPages, "dashboardPages");
	}

	@Override
	public ModuleMetadata metadata() {
		return METADATA;
	}

	@Override
	public void initialize(ModuleContext moduleContext) {
		this.context = moduleContext;
		key = keybinds.register("quick_actions", InputConstants.KEY_J, this::onShortcut);
		dashboardPages.register(new QuickActionsDashboardPage(this));
	}

	@Override
	public void onDisable() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof QuickActionsScreen) {
			minecraft.gui.setScreen(null);
		}
	}

	public boolean isEnabled() {
		return context != null && context.isEnabled();
	}

	public JsonFileStore<QuickActionSettings> settings() {
		return settings;
	}

	QuickActionRegistry registry() {
		return registry;
	}

	NotificationService notifications() {
		return notifications;
	}

	KeyMapping key() {
		return key;
	}

	/** Opens the popup. */
	public void open() {
		Minecraft.getInstance().gui.setScreen(new QuickActionsScreen(registry, settings, notifications));
	}

	private void onShortcut() {
		if (!context.isEnabled()) {
			notifications.info(Component.translatable("minekit.notification.quick_actions_disabled"));
			return;
		}
		open();
	}
}
