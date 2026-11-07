package io.github.nordavid.minekit.gametest;

import io.github.nordavid.minekit.core.screen.widget.UiText;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Prepares the gametest run: every text Minekit has to shorten on any screen is listed in {@code ui-audit.txt} in the
 * game folder ({@code build/run/clientGameTest}), so cut-off labels can be reviewed after a run.
 */
public final class GameTestSetup implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Path file = FabricLoader.getInstance().getGameDir().resolve("ui-audit.txt");
		UiText.auditTruncations(file);
	}
}
