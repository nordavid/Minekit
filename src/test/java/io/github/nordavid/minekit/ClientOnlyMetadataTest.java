package io.github.nordavid.minekit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the client-only contract from PROJECT_PLAN.md section 3.3 against regressions. Reads the processed
 * {@code fabric.mod.json}, i.e. the file that ends up in the JAR.
 */
class ClientOnlyMetadataTest {
	private static JsonObject modJson;

	@BeforeAll
	static void loadModJson() throws IOException {
		// Fabric Loader and Fabric API also ship a fabric.mod.json on the test classpath, so select ours by id.
		List<JsonObject> matches = new ArrayList<>();
		Enumeration<URL> resources = ClientOnlyMetadataTest.class.getClassLoader().getResources("fabric.mod.json");
		while (resources.hasMoreElements()) {
			try (Reader reader = new InputStreamReader(resources.nextElement().openStream(), StandardCharsets.UTF_8)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (json.has("id") && MinekitClient.MOD_ID.equals(json.get("id").getAsString())) {
					matches.add(json);
				}
			}
		}
		assertEquals(1, matches.size(), "expected exactly one fabric.mod.json with id 'minekit' on the classpath");
		modJson = matches.getFirst();
	}

	@Test
	void declaresClientEnvironment() {
		assertEquals("client", modJson.get("environment").getAsString());
	}

	@Test
	void registersOnlyTheClientEntrypoint() {
		JsonObject entrypoints = modJson.getAsJsonObject("entrypoints");
		// "modmenu" is only called by Mod Menu itself, on the client; main and server entrypoints stay forbidden.
		assertEquals(Set.of("client", "modmenu"), entrypoints.keySet(), "no main or server entrypoint may be registered");

		JsonArray clientEntrypoints = entrypoints.getAsJsonArray("client");
		assertEquals(1, clientEntrypoints.size());
		assertEquals(MinekitClient.class.getName(), clientEntrypoints.get(0).getAsString());
	}

	@Test
	void dependsOnMinecraftFabricLoaderFabricApiAndJava() {
		JsonObject depends = modJson.getAsJsonObject("depends");
		assertEquals(Set.of("minecraft", "fabricloader", "fabric-api", "java"), depends.keySet());
	}

	@Test
	void buildPlaceholdersAreExpanded() {
		assertFalse(modJson.toString().contains("${"), "processResources left a placeholder unexpanded: " + modJson);
	}

	@Test
	void minecraftRangeMatchesBuildTarget() {
		String target = System.getProperty("minekit.minecraftVersion");
		assertNotNull(target, "the Gradle test task must pass minekit.minecraftVersion");
		assertEquals("~" + target, modJson.getAsJsonObject("depends").get("minecraft").getAsString());
	}
}
