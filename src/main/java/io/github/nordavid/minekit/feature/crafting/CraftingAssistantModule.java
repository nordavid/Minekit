package io.github.nordavid.minekit.feature.crafting;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.action.QuickActionRegistry;
import io.github.nordavid.minekit.core.goal.GoalBridge;
import io.github.nordavid.minekit.core.storage.StorageBridge;
import io.github.nordavid.minekit.core.input.KeybindManager;
import io.github.nordavid.minekit.core.item.ItemIds;
import io.github.nordavid.minekit.core.module.MinekitModule;
import io.github.nordavid.minekit.core.module.ModuleContext;
import io.github.nordavid.minekit.core.module.ModuleMetadata;
import io.github.nordavid.minekit.core.notification.NotificationService;
import io.github.nordavid.minekit.core.persistence.JsonFileStore;
import io.github.nordavid.minekit.core.screen.DashboardPages;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A2 Crafting Assistant: choose an item and a quantity, and see the raw materials, the crafting steps and what is
 * still missing from the inventory. Plans can be saved.
 *
 * <p>Recipes come from the server's recipe book plus the vanilla defaults in the game files (ADR 0004). The index is
 * built when the planner opens and rebuilt when the world or the unlocked recipes change.
 */
public final class CraftingAssistantModule implements MinekitModule {
	public static final ModuleMetadata METADATA = new ModuleMetadata("crafting_assistant",
			"minekit.module.crafting_assistant.name", "minekit.module.crafting_assistant.description", true);

	private final JsonFileStore<SavedPlans> savedPlans;
	private final KeybindManager keybinds;
	private final NotificationService notifications;
	private final QuickActionRegistry quickActions;
	private final DashboardPages dashboardPages;
	private final GoalBridge goals;
	private final StorageBridge storage;
	private ModuleContext context;

	private @Nullable RecipeIndex cachedIndex;
	private @Nullable ClientLevel cachedLevel;
	private int cachedBookSize = -1;

	public CraftingAssistantModule(JsonFileStore<SavedPlans> savedPlans, KeybindManager keybinds, NotificationService notifications,
			QuickActionRegistry quickActions, DashboardPages dashboardPages, GoalBridge goals,
			StorageBridge storage) {
		this.savedPlans = Objects.requireNonNull(savedPlans, "savedPlans");
		this.keybinds = Objects.requireNonNull(keybinds, "keybinds");
		this.notifications = Objects.requireNonNull(notifications, "notifications");
		this.quickActions = Objects.requireNonNull(quickActions, "quickActions");
		this.dashboardPages = Objects.requireNonNull(dashboardPages, "dashboardPages");
		this.goals = Objects.requireNonNull(goals, "goals");
		this.storage = Objects.requireNonNull(storage, "storage");
	}

	@Override
	public ModuleMetadata metadata() {
		return METADATA;
	}

	@Override
	public void initialize(ModuleContext moduleContext) {
		this.context = moduleContext;
		// No default key: the planner is reached through the dashboard and Quick Actions; players can bind one.
		keybinds.register("crafting_assistant", InputConstants.UNKNOWN.getValue(), () -> {
			if (isEnabled()) {
				openPlanner(null);
			}
		});
		quickActions.register(new QuickAction("open_crafting", Component.translatable("minekit.action.open_crafting"),
				"crafting recipe rezept herstellen material planer", this::unavailableReason, () -> openPlanner(null)));
		quickActions.register(new QuickAction("plan_held_item", Component.translatable("minekit.action.plan_held_item"),
				"held hand item craft plan recipe gehalten hand herstellen planen", this::heldItemUnavailableReason,
				() -> ItemIds.held().ifPresent(item -> openPlanner(PlanRequest.of(item, 1)))));
		dashboardPages.register(new CraftingDashboardPage(this));
	}

	private Optional<Component> heldItemUnavailableReason() {
		Optional<Component> reason = unavailableReason();
		if (reason.isPresent()) {
			return reason;
		}
		Optional<String> held = ItemIds.held();
		if (held.isEmpty()) {
			return Optional.of(Component.translatable("minekit.action.unavailable.no_held_item"));
		}
		if (recipeIndex().recipesFor(held.get()).isEmpty()) {
			return Optional.of(Component.translatable("minekit.crafting.unavailable.no_recipe", ItemIds.name(held.get())));
		}
		return Optional.empty();
	}

	@Override
	public void onDisable() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof CraftingScreen) {
			minecraft.gui.setScreen(null);
		}
	}

	public boolean isEnabled() {
		return context != null && context.isEnabled();
	}

	public JsonFileStore<SavedPlans> savedPlans() {
		return savedPlans;
	}

	GoalBridge goals() {
		return goals;
	}

	StorageBridge storage() {
		return storage;
	}

	NotificationService notifications() {
		return notifications;
	}

	public Optional<Component> unavailableReason() {
		if (!isEnabled()) {
			return Optional.of(Component.translatable("minekit.crafting.unavailable.module_off"));
		}
		if (Minecraft.getInstance().player == null) {
			return Optional.of(Component.translatable("minekit.crafting.unavailable.no_world"));
		}
		return Optional.empty();
	}

	/** Opens the planner, optionally with a plan. Explains why if that is not possible. */
	public void openPlanner(@Nullable PlanRequest request) {
		Optional<Component> reason = unavailableReason();
		if (reason.isPresent()) {
			notifications.warning(reason.get());
			return;
		}
		Minecraft.getInstance().gui.setScreen(new CraftingScreen(this, request));
	}

	/** The recipe index for the current world, rebuilt if the world or the unlocked recipes changed. */
	public RecipeIndex recipeIndex() {
		Minecraft minecraft = Minecraft.getInstance();
		int bookSize = minecraft.player == null ? 0
				: minecraft.player.getRecipeBook().getCollections().stream().mapToInt(collection -> collection.getRecipes().size()).sum();
		if (cachedIndex == null || cachedLevel != minecraft.level || cachedBookSize != bookSize) {
			cachedIndex = RecipeCatalog.load(minecraft);
			cachedLevel = minecraft.level;
			cachedBookSize = bookSize;
		}
		return cachedIndex;
	}

	void savePlan(PlanRequest request) {
		savedPlans.update(plans -> plans.withSaved(request, System.currentTimeMillis()));
		if (savedPlans.lastSaveFailed()) {
			notifications.error(Component.translatable("minekit.notification.save_failed"));
		} else {
			notifications.success(Component.translatable("minekit.crafting.saved", request.quantity(), ItemIds.name(request.target())));
		}
	}

	void deletePlan(String target) {
		savedPlans.update(plans -> plans.without(target));
	}
}
