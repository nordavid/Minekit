package io.github.nordavid.minekit.feature.blueprint;

import com.mojang.serialization.Dynamic;
import io.github.nordavid.minekit.feature.blueprint.BlueprintProgress.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

/**
 * A blueprint with its palette turned into this Minecraft version's block states. Files saved by an older version are
 * first updated with Minecraft's own data fixer (renamed blocks and properties); names this version does not know stay
 * unknown and are reported, never guessed.
 */
public final class ResolvedBlueprint {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Minekit");
	/** Properties that decide how a block is turned or which part it is; the rest (water, power, ...) is ignored. */
	static final Set<String> ORIENTATION = Set.of("facing", "axis", "half", "type", "rotation", "face", "orientation",
			"vertical_direction", "part", "hinge");

	private final String file;
	private final Blueprint blueprint;
	private final @Nullable BlockState[] states;
	private final Item[] items;
	private final List<String> unknown;
	private @Nullable BlockState[] transformed;
	private int transformedRotation = -1;
	private boolean transformedMirrored;

	private ResolvedBlueprint(String file, Blueprint blueprint, @Nullable BlockState[] states, Item[] items, List<String> unknown) {
		this.file = file;
		this.blueprint = blueprint;
		this.states = states;
		this.items = items;
		this.unknown = List.copyOf(unknown);
	}

	public static ResolvedBlueprint resolve(String file, Blueprint blueprint) {
		int current = SharedConstants.getCurrentVersion().dataVersion().version();
		int saved = blueprint.dataVersion();
		BlockState[] states = new BlockState[blueprint.palette().size()];
		Item[] items = new Item[states.length];
		List<String> unknown = new ArrayList<>();
		for (int i = 0; i < states.length; i++) {
			BlockSpec spec = blueprint.palette().get(i);
			if (saved > 0 && saved < current) {
				spec = updated(spec, saved, current);
			}
			BlockState state = resolve(spec);
			states[i] = state;
			items[i] = state == null ? Items.AIR : state.getBlock().asItem();
			if (state == null && !spec.isAir()) {
				unknown.add(blueprint.palette().get(i).id());
			}
		}
		return new ResolvedBlueprint(file, blueprint, states, items, unknown.stream().distinct().toList());
	}

	/** Data version from which stored block states use {@code id} and {@code properties} instead of {@code Name} and {@code Properties}. */
	private static final int FIELD_RENAME_VERSION = 5006;

	/** Runs a block state saved by an older version through Minecraft's data fixer. */
	private static BlockSpec updated(BlockSpec spec, int from, int to) {
		try {
			boolean newNames = from >= FIELD_RENAME_VERSION;
			CompoundTag tag = new CompoundTag();
			tag.putString(newNames ? "id" : "Name", spec.id());
			if (!spec.properties().isEmpty()) {
				CompoundTag properties = new CompoundTag();
				spec.properties().forEach(properties::putString);
				tag.put(newNames ? "properties" : "Properties", properties);
			}
			Tag fixed = DataFixers.getDataFixer().update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, tag), from, to).getValue();
			if (fixed instanceof CompoundTag result) {
				return BlueprintReader.blockFromTag(result);
			}
		} catch (RuntimeException | BlueprintException e) {
			// Keep the original name; resolving decides whether it is known.
			LOGGER.warn("Could not update block state {} from data version {} to {}", spec, from, to, e);
		}
		return spec;
	}

	private static @Nullable BlockState resolve(BlockSpec spec) {
		if (spec.isAir()) {
			return null;
		}
		Identifier id = Identifier.tryParse(spec.id());
		Optional<Block> block = id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
		if (block.isEmpty()) {
			return null;
		}
		BlockState state = block.get().defaultBlockState();
		for (Map.Entry<String, String> entry : spec.properties().entrySet()) {
			Property<?> property = block.get().getStateDefinition().getProperty(entry.getKey());
			if (property != null) {
				state = withValue(state, property, entry.getValue());
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
	}

	public String file() {
		return file;
	}

	public Blueprint blueprint() {
		return blueprint;
	}

	/** Block ids this version does not know. */
	public List<String> unknownBlocks() {
		return unknown;
	}

	/** The item to place for a palette entry; air if the block has none (fire, water, ...). */
	public Item item(int paletteIndex) {
		return items[paletteIndex];
	}

	public @Nullable BlockState state(int paletteIndex) {
		return states[paletteIndex];
	}

	/** The block state wanted at a palette entry once the blueprint is mirrored and turned like {@code placement}. */
	public @Nullable BlockState expected(int paletteIndex, Placement placement) {
		if (transformed == null || transformedRotation != placement.rotation() || transformedMirrored != placement.mirrored()) {
			BlockState[] next = new BlockState[states.length];
			Rotation rotation = Rotation.values()[placement.rotation()];
			for (int i = 0; i < states.length; i++) {
				BlockState state = states[i];
				if (state != null) {
					next[i] = (placement.mirrored() ? state.mirror(Mirror.FRONT_BACK) : state).rotate(rotation);
				}
			}
			transformed = next;
			transformedRotation = placement.rotation();
			transformedMirrored = placement.mirrored();
		}
		return transformed[paletteIndex];
	}

	/** World position of a raw index (see {@link Blueprint#index}). */
	public BlockPos worldPos(int index, Placement placement) {
		int layer = blueprint.sizeX() * blueprint.sizeZ();
		int y = index / layer;
		int rest = index % layer;
		int z = rest / blueprint.sizeX();
		int x = rest % blueprint.sizeX();
		return new BlockPos(placement.worldX(blueprint, x, z), placement.worldY(y), placement.worldZ(blueprint, x, z));
	}

	/** Compares one position with the client's copy of the world. Unloaded chunks are never read. */
	public Status check(Level level, int index, Placement placement) {
		int palette = blueprint.paletteIndexAt(index);
		BlockState expected = expected(palette, placement);
		if (expected == null) {
			return Status.UNKNOWN;
		}
		BlockPos pos = worldPos(index, placement);
		if (level.isOutsideBuildHeight(pos)
				|| level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) == null) {
			return Status.UNCHECKED;
		}
		BlockState actual = level.getBlockState(pos);
		if (actual.getBlock() == expected.getBlock()) {
			for (Property<?> property : expected.getProperties()) {
				if (ORIENTATION.contains(property.getName()) && !Objects.equals(expected.getValue(property), actual.getValue(property))) {
					return Status.WRONG;
				}
			}
			return Status.CORRECT;
		}
		return actual.isAir() || actual.canBeReplaced() ? Status.MISSING : Status.WRONG;
	}
}
