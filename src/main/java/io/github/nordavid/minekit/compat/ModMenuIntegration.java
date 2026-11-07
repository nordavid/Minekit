package io.github.nordavid.minekit.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.nordavid.minekit.MinekitRuntime;
import io.github.nordavid.minekit.core.screen.DashboardScreen;

/**
 * Mod Menu's settings button for Minekit opens the dashboard; closing it returns to Mod Menu. Mod Menu calls this
 * entrypoint only when it is installed, so Minekit does not depend on it.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new DashboardScreen(MinekitRuntime.get(), parent, null);
	}
}
