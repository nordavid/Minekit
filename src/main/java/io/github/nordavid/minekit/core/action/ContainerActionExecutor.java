package io.github.nordavid.minekit.core.action;

import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.MenuReader;
import io.github.nordavid.minekit.core.container.PickupSimulator;
import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs {@link ContainerJob}s: ordinary left clicks in an open menu, exactly as a player would make them, one at a
 * time and with state checks in between. Client thread only; {@link #tick} is called once per client tick.
 *
 * <p>Safety rules (PROJECT_PLAN.md §6.1, §6.8, §9):
 * <ul>
 *   <li>At most one click every {@link #CLICK_INTERVAL_TICKS} ticks.</li>
 *   <li>Before every click the job's preconditions are checked and the actual menu state is compared with the state
 *       predicted after the previous click. Any difference (a server correction, an item picked up, a slot changed by
 *       anything else) stops the job. Minecraft gives no positive confirmation of a successful click, only
 *       corrections, so the job also waits {@link #SETTLE_TICKS} ticks without corrections before it counts as done.</li>
 *   <li>The next click is always planned from the actual state, never from a stale list.</li>
 *   <li>If a job stops with an item on the cursor while the menu is still open, the item is put into a free slot.</li>
 *   <li>The item totals before and after are compared and reported, so a loss or duplication would never go
 *       unnoticed.</li>
 * </ul>
 * Only one job runs at a time.
 */
public final class ContainerActionExecutor {
	public static final int CLICK_INTERVAL_TICKS = 2;
	public static final int SETTLE_TICKS = 10;

	private static final Logger LOGGER = LoggerFactory.getLogger("MINEKIT");

	public enum Outcome {
		/** The goal was reached and no correction arrived during the settle time. */
		COMPLETED,
		/** The player cancelled. */
		CANCELLED,
		/** A precondition failed or the state deviated from the prediction. */
		STOPPED
	}

	/**
	 * @param reason              why the job ended early, if it did
	 * @param itemTotalsUnchanged whether the menu (all slots plus cursor) holds exactly the same items as at the start
	 */
	public record Result(Outcome outcome, int clicks, Optional<Component> reason, boolean itemTotalsUnchanged) {
	}

	private static final class Running {
		final ContainerJob job;
		final Map<ItemKey, Integer> initialTotals;
		ContainerState expected;
		int clicks;
		int cooldown;
		int settledTicks;

		Running(ContainerJob job, ContainerState initial) {
			this.job = job;
			this.initialTotals = initial.totals();
			this.expected = initial;
		}
	}

	private @Nullable Running running;
	private @Nullable Result lastResult;

	public boolean isRunning() {
		return running != null;
	}

	public Optional<ContainerJob> currentJob() {
		return Optional.ofNullable(running).map(run -> run.job);
	}

	/** Clicks the running job has made so far; 0 if none is running. */
	public int runningClicks() {
		return running == null ? 0 : running.clicks;
	}

	/** Result of the most recent job, for diagnostics and tests. */
	public Optional<Result> lastResult() {
		return Optional.ofNullable(lastResult);
	}

	/** Starts a job. Returns {@code false} if another job is running or the job's preconditions already fail. */
	public boolean start(ContainerJob job) {
		Objects.requireNonNull(job, "job");
		if (running != null) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Optional<Component> blocker = job.blocker(minecraft);
		if (blocker.isPresent()) {
			lastResult = new Result(Outcome.STOPPED, 0, blocker, true);
			job.onFinished(lastResult);
			return false;
		}
		running = new Running(job, MenuReader.read(job.menu(), job.keys()));
		LOGGER.info("Started '{}'", job.name().getString());
		return true;
	}

	public void cancel(Component reason) {
		if (running != null) {
			finish(Outcome.CANCELLED, Optional.of(reason));
		}
	}

	public void tick(Minecraft minecraft) {
		Running run = running;
		if (run == null) {
			return;
		}
		ContainerJob job = run.job;
		Optional<Component> blocker = job.blocker(minecraft);
		LocalPlayer player = minecraft.player;
		if (blocker.isEmpty() && (player == null || player.containerMenu != job.menu())) {
			blocker = Optional.of(Component.translatable("minekit.executor.menu_closed"));
		}
		if (blocker.isPresent()) {
			finish(Outcome.STOPPED, blocker);
			return;
		}

		ContainerState actual = MenuReader.read(job.menu(), job.keys());
		if (!actual.equals(run.expected)) {
			LOGGER.warn("'{}' stopped: menu differs from the prediction in slots {} (cursor expected {}, actual {})",
					job.name().getString(), actual.differingSlots(run.expected), run.expected.carried(), actual.carried());
			finish(Outcome.STOPPED, Optional.of(Component.translatable("minekit.executor.unexpected_change")));
			return;
		}
		if (run.cooldown > 0) {
			run.cooldown--;
			return;
		}

		OptionalInt next = job.planner().nextClick(actual);
		if (next.isEmpty()) {
			if (!job.planner().isComplete(actual)) {
				finish(Outcome.STOPPED, Optional.of(Component.translatable("minekit.executor.stuck")));
			} else if (++run.settledTicks >= SETTLE_TICKS) {
				finish(Outcome.COMPLETED, Optional.empty());
			}
			return;
		}
		run.settledTicks = 0;
		if (run.clicks >= job.maxClicks()) {
			finish(Outcome.STOPPED, Optional.of(Component.translatable("minekit.executor.too_many_clicks")));
			return;
		}

		int slot = next.getAsInt();
		click(minecraft, job.menu(), slot);
		run.clicks++;
		run.expected = PickupSimulator.leftClick(actual, slot, job.keys()::maxStackSize);
		run.cooldown = CLICK_INTERVAL_TICKS - 1;
	}

	private void finish(Outcome outcome, Optional<Component> reason) {
		Running run = Objects.requireNonNull(running);
		running = null;
		ContainerJob job = run.job;
		Minecraft minecraft = Minecraft.getInstance();
		boolean menuOpen = minecraft.player != null && minecraft.player.containerMenu == job.menu();
		if (outcome != Outcome.COMPLETED && menuOpen) {
			recoverCursor(minecraft, job);
		}
		boolean totalsUnchanged = MenuReader.read(job.menu(), job.keys()).totals().equals(run.initialTotals);
		lastResult = new Result(outcome, run.clicks, reason, totalsUnchanged);
		LOGGER.info("'{}' ended: {} after {} clicks{}{}", job.name().getString(), outcome, run.clicks,
				reason.map(r -> " (" + r.getString() + ")").orElse(""), totalsUnchanged ? "" : ", item totals changed");
		job.onFinished(lastResult);
	}

	private static void recoverCursor(Minecraft minecraft, ContainerJob job) {
		AbstractContainerMenu menu = job.menu();
		if (menu.getCarried().isEmpty()) {
			return;
		}
		for (int slot : job.recoverySlots()) {
			if (slot < menu.slots.size() && menu.slots.get(slot).getItem().isEmpty()) {
				click(minecraft, menu, slot);
				LOGGER.info("Put the item from the cursor back into slot {}", slot);
				return;
			}
		}
		LOGGER.warn("No free slot to put the cursor item back; it stays on the cursor");
	}

	private static void click(Minecraft minecraft, AbstractContainerMenu menu, int slot) {
		minecraft.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, minecraft.player);
	}
}
