package io.github.nordavid.minekit.core.container;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;

/**
 * Coordinates Minekit's side panels in a container screen (Smart Deposit, loadouts). They share the space left of the
 * container, so at most one is open: opening one closes the others, and their opener buttons hide while it is open.
 */
public final class ContainerPanels {
	/** A side panel of one container screen. */
	public interface Panel {
		Screen screen();

		boolean isOpen();

		/** Closes the panel because another one opened. */
		void close();

		/** Rebuilds the panel's widgets, for example to hide or show its opener button. */
		void refresh();
	}

	private final List<Panel> panels = new ArrayList<>();

	/** Registers a panel of the current screen; panels of earlier screens are forgotten. */
	public void register(Panel panel) {
		panels.removeIf(existing -> existing == panel || existing.screen() != panel.screen());
		panels.add(panel);
	}

	/** Call when {@code panel} opened: closes the others of the same screen and lets the rest hide their openers. */
	public void opened(Panel panel) {
		for (Panel other : List.copyOf(panels)) {
			if (other != panel && other.screen() == panel.screen()) {
				if (other.isOpen()) {
					other.close();
				} else {
					other.refresh();
				}
			}
		}
	}

	/** Call when {@code panel} closed: the others show their openers again. */
	public void closed(Panel panel) {
		for (Panel other : List.copyOf(panels)) {
			if (other != panel && other.screen() == panel.screen()) {
				other.refresh();
			}
		}
	}

	/** Whether another panel of the same screen is open. */
	public boolean otherOpen(Panel panel) {
		return panels.stream().anyMatch(other -> other != panel && other.screen() == panel.screen() && other.isOpen());
	}
}
