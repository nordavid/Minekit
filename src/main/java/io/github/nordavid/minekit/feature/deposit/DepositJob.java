package io.github.nordavid.minekit.feature.deposit;

import io.github.nordavid.minekit.core.action.ContainerActionExecutor;
import io.github.nordavid.minekit.core.action.ContainerJob;
import io.github.nordavid.minekit.core.container.ClickPlanner;
import io.github.nordavid.minekit.core.container.ContainerTracker;
import io.github.nordavid.minekit.core.container.OpenedContainer;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Deposits into the open container while its screen stays open, so the player sees every move. */
final class DepositJob implements ContainerJob {
	private final LocalPlayer player;
	private final OpenedContainer container;
	private final ContainerTracker tracker;
	private final ItemKeyIndex keys;
	private final DepositPlanner.Plan plan;
	private final DepositClickPlanner planner;
	private final Consumer<ContainerActionExecutor.Result> onFinished;

	DepositJob(LocalPlayer player, OpenedContainer container, ContainerTracker tracker, ItemKeyIndex keys, DepositPlanner.Plan plan,
			DepositPlanner.Rules rules, Consumer<ContainerActionExecutor.Result> onFinished) {
		this.player = Objects.requireNonNull(player, "player");
		this.container = Objects.requireNonNull(container, "container");
		this.tracker = Objects.requireNonNull(tracker, "tracker");
		this.keys = Objects.requireNonNull(keys, "keys");
		this.plan = Objects.requireNonNull(plan, "plan");
		this.planner = plan.planner(rules);
		this.onFinished = Objects.requireNonNull(onFinished, "onFinished");
	}

	@Override
	public Component name() {
		return Component.translatable("minekit.action.smart_deposit");
	}

	@Override
	public AbstractContainerMenu menu() {
		return container.menu();
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
		return plan.maxClicks();
	}

	@Override
	public Optional<Component> blocker(Minecraft minecraft) {
		if (minecraft.player != player || !player.isAlive()) {
			return Optional.of(Component.translatable("minekit.sort.blocked.player"));
		}
		if (player.isSpectator()) {
			return Optional.of(Component.translatable("minekit.deposit.blocked.spectator"));
		}
		if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen) || screen.getMenu() != container.menu()
				|| tracker.current().map(OpenedContainer::menu).orElse(null) != container.menu()) {
			return Optional.of(Component.translatable("minekit.deposit.blocked.closed"));
		}
		return Optional.empty();
	}

	/** A cursor item goes back to the player first: the slot it came from, then any free inventory slot. */
	@Override
	public int[] recoverySlots() {
		return IntStream.concat(IntStream.of(plan.sources()),
				IntStream.concat(IntStream.of(plan.layout().main()), IntStream.of(plan.layout().hotbar()))).toArray();
	}

	@Override
	public void onFinished(ContainerActionExecutor.Result result) {
		onFinished.accept(result);
	}
}
