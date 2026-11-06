package io.github.nordavid.minekit.core.action;

import io.github.nordavid.minekit.core.container.ClickPlanner;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** A sequence of ordinary inventory clicks run by {@link ContainerActionExecutor}. */
public interface ContainerJob {
	/** Short name for messages, e.g. "Sort inventory". */
	Component name();

	/** The menu the job clicks in. The job stops if the player's open menu is no longer this instance. */
	AbstractContainerMenu menu();

	/** Key index used for every snapshot of this job, so keys stay consistent. */
	ItemKeyIndex keys();

	ClickPlanner planner();

	/** Upper bound on clicks; reaching it stops the job. */
	int maxClicks();

	/**
	 * Checked before every step. A present value is the reason the job cannot continue, e.g. the screen was closed or
	 * the game mode changed.
	 */
	Optional<Component> blocker(Minecraft minecraft);

	/** Slots, in order of preference, where an item left on the cursor may be put down if the job stops early. */
	int[] recoverySlots();

	/** Called once, on the client thread, when the job ends for any reason. */
	void onFinished(ContainerActionExecutor.Result result);
}
