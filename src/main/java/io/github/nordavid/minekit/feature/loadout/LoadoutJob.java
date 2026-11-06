package io.github.nordavid.minekit.feature.loadout;

import io.github.nordavid.minekit.core.action.ContainerActionExecutor;
import io.github.nordavid.minekit.core.action.ContainerJob;
import io.github.nordavid.minekit.core.container.ClickPlanner;
import io.github.nordavid.minekit.core.container.ContainerTracker;
import io.github.nordavid.minekit.core.container.OpenedContainer;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jspecify.annotations.Nullable;

/**
 * Applies a loadout with the clicks its plan simulated, either in the player's own inventory screen or in a confirmed
 * container's screen (taking items from the container).
 */
final class LoadoutJob implements ContainerJob {
	private final LocalPlayer player;
	private final AbstractContainerMenu menu;
	private final @Nullable OpenedContainer container;
	private final ContainerTracker tracker;
	private final ItemKeyIndex keys;
	private final LoadoutPlanner.Plan plan;
	private final ClickPlanner planner;
	private final Consumer<ContainerActionExecutor.Result> onFinished;

	LoadoutJob(LocalPlayer player, AbstractContainerMenu menu, @Nullable OpenedContainer container, ContainerTracker tracker, ItemKeyIndex keys,
			LoadoutPlanner.Plan plan, Consumer<ContainerActionExecutor.Result> onFinished) {
		this.player = Objects.requireNonNull(player, "player");
		this.menu = Objects.requireNonNull(menu, "menu");
		this.container = container;
		this.tracker = Objects.requireNonNull(tracker, "tracker");
		this.keys = Objects.requireNonNull(keys, "keys");
		this.plan = Objects.requireNonNull(plan, "plan");
		this.planner = plan.clickPlanner(keys::maxStackSize);
		this.onFinished = Objects.requireNonNull(onFinished, "onFinished");
	}

	@Override
	public Component name() {
		return Component.translatable("minekit.action.apply_loadout");
	}

	@Override
	public AbstractContainerMenu menu() {
		return menu;
	}

	@Override
	public ItemKeyIndex keys() {
		return keys;
	}

	@Override
	public ClickPlanner planner() {
		return planner;
	}

	@Override
	public int maxClicks() {
		return plan.clicks().size() + 4;
	}

	@Override
	public Optional<Component> blocker(Minecraft minecraft) {
		if (minecraft.player != player || !player.isAlive()) {
			return Optional.of(Component.translatable("minekit.sort.blocked.player"));
		}
		if (player.isSpectator() || (container == null && player.isCreative())) {
			return Optional.of(Component.translatable("minekit.sort.blocked.game_mode"));
		}
		if (container == null) {
			if (!(minecraft.gui.screen() instanceof InventoryScreen)) {
				return Optional.of(Component.translatable("minekit.sort.blocked.inventory_closed"));
			}
		} else if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen) || screen.getMenu() != menu
				|| tracker.current().map(OpenedContainer::menu).orElse(null) != menu) {
			return Optional.of(Component.translatable("minekit.deposit.blocked.closed"));
		}
		return Optional.empty();
	}

	@Override
	public int[] recoverySlots() {
		return plan.recovery();
	}

	@Override
	public void onFinished(ContainerActionExecutor.Result result) {
		onFinished.accept(result);
	}
}
