package io.github.nordavid.minekit.feature.inventory;

import io.github.nordavid.minekit.core.action.ContainerActionExecutor;
import io.github.nordavid.minekit.core.action.ContainerJob;
import io.github.nordavid.minekit.core.container.ClickPlanner;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Sorts the player inventory while the vanilla inventory screen is open, so the player sees every move. */
final class SortJob implements ContainerJob {
	private final LocalPlayer player;
	private final ItemKeyIndex keys;
	private final SortPlanner.Planned plan;
	private final SortClickPlanner planner;
	private final Consumer<ContainerActionExecutor.Result> onFinished;

	SortJob(LocalPlayer player, ItemKeyIndex keys, SortPlanner.Planned plan, Consumer<ContainerActionExecutor.Result> onFinished) {
		this.player = Objects.requireNonNull(player, "player");
		this.keys = Objects.requireNonNull(keys, "keys");
		this.plan = Objects.requireNonNull(plan, "plan");
		this.planner = new SortClickPlanner(plan);
		this.onFinished = Objects.requireNonNull(onFinished, "onFinished");
	}

	@Override
	public Component name() {
		return Component.translatable("minekit.action.sort_inventory");
	}

	@Override
	public AbstractContainerMenu menu() {
		return player.inventoryMenu;
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
		return SortJobLimits.maxClicks(plan.region().length);
	}

	@Override
	public Optional<Component> blocker(Minecraft minecraft) {
		if (minecraft.player != player || !player.isAlive()) {
			return Optional.of(Component.translatable("minekit.sort.blocked.player"));
		}
		if (player.isCreative() || player.isSpectator()) {
			return Optional.of(Component.translatable("minekit.sort.blocked.game_mode"));
		}
		if (!(minecraft.gui.screen() instanceof InventoryScreen)) {
			return Optional.of(Component.translatable("minekit.sort.blocked.inventory_closed"));
		}
		return Optional.empty();
	}

	@Override
	public int[] recoverySlots() {
		return plan.region();
	}

	@Override
	public void onFinished(ContainerActionExecutor.Result result) {
		onFinished.accept(result);
	}
}
