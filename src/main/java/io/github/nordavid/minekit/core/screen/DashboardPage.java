package io.github.nordavid.minekit.core.screen;

import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One page of the dashboard. The core contributes Overview and Settings; feature modules add their own pages through
 * {@link DashboardPages}, so the dashboard never depends on feature code.
 */
public interface DashboardPage {
	/** What a page may do while it is being built. */
	interface Builder {
		/** The scrolling content area. Add headings and text here. */
		ContentPane content();

		/** Registers a widget with the screen and places it as the next, full-width row of the content area. */
		<T extends AbstractWidget> T widget(T widget);

		/** Registers widgets with the screen and places them right-aligned next to a label. */
		void labeled(Component label, AbstractWidget... widgets);

		/** Like {@link #labeled}, with an optional item icon and a status text before the widgets. */
		void line(@Nullable ItemStack icon, Component label, @Nullable Component status, int statusColor, AbstractWidget... widgets);

		/** Rebuilds the page after the current input event, e.g. when a change affects other rows. */
		void rebuildLater();

		/** The dashboard screen, for use as parent of sub-screens. */
		Screen screen();
	}

	/** Stable id, e.g. {@code overview}. */
	String id();

	Component title();

	/** Hidden pages are left out of the sidebar, e.g. the page of a module that is switched off. */
	default boolean isVisible() {
		return true;
	}

	void build(Builder builder);
}
