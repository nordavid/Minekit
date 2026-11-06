package io.github.nordavid.minekit.feature.blueprint;

import java.util.Arrays;
import java.util.Objects;

/**
 * What is known about how far a placed blueprint is built: one status per block position (air positions of the
 * blueprint are not tracked) inside the layers being built, with running totals. Pure; the world is read elsewhere and
 * reported with {@link #set}. Positions start {@link Status#UNCHECKED} and only change when reported, so nothing is
 * ever claimed for a position that was not looked at.
 */
public final class BlueprintProgress {
	public enum Status {
		/** Not looked at yet, or its chunk is not loaded. */
		UNCHECKED,
		/** The right block, turned the right way. */
		CORRECT,
		/** Another block, or the right block turned another way. */
		WRONG,
		/** Nothing there yet (air, or something that would simply be replaced, like grass or water). */
		MISSING,
		/** The blueprint names a block this Minecraft version does not know. */
		UNKNOWN
	}

	private static final Status[] STATUSES = Status.values();

	private final Blueprint blueprint;
	private final BlueprintSettings.LayerRange range;
	private final byte[] status;
	private final long[] counts = new long[STATUSES.length];
	private final int[] openPerLayer;
	private final long[] neededPerPalette;
	private final long[] placedPerPalette;
	private final int[] tracked;
	private int cursor;

	public BlueprintProgress(Blueprint blueprint, BlueprintSettings.LayerRange range) {
		this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
		this.range = Objects.requireNonNull(range, "range");
		this.status = new byte[(int) blueprint.volume()];
		this.openPerLayer = new int[blueprint.sizeY()];
		this.neededPerPalette = new long[blueprint.palette().size()];
		this.placedPerPalette = new long[blueprint.palette().size()];
		int layer = blueprint.sizeX() * blueprint.sizeZ();
		int[] indexes = new int[(int) blueprint.volume()];
		int count = 0;
		for (int y = range.from(); y <= range.to(); y++) {
			for (int index = y * layer; index < (y + 1) * layer; index++) {
				int palette = blueprint.paletteIndexAt(index);
				if (!blueprint.palette().get(palette).isAir()) {
					indexes[count++] = index;
					neededPerPalette[palette]++;
				}
			}
		}
		this.tracked = Arrays.copyOf(indexes, count);
		counts[Status.UNCHECKED.ordinal()] = count;
	}

	public Blueprint blueprint() {
		return blueprint;
	}

	public BlueprintSettings.LayerRange range() {
		return range;
	}

	/** Block positions in the layers being built. */
	public long total() {
		return tracked.length;
	}

	public long count(Status which) {
		return counts[which.ordinal()];
	}

	/** Share of the tracked positions that are known to be correct, 0 to 1. */
	public double completion() {
		return tracked.length == 0 ? 1 : (double) count(Status.CORRECT) / tracked.length;
	}

	public Status status(int index) {
		return STATUSES[status[index]];
	}

	public boolean isTracked(int index) {
		int y = index / (blueprint.sizeX() * blueprint.sizeZ());
		return range.contains(y) && !blueprint.palette().get(blueprint.paletteIndexAt(index)).isAir();
	}

	/** Records what a position holds now. Ignored for positions that are not tracked. */
	public void set(int index, Status next) {
		if (!isTracked(index)) {
			return;
		}
		Status previous = STATUSES[status[index]];
		if (previous == next) {
			return;
		}
		status[index] = (byte) next.ordinal();
		counts[previous.ordinal()]--;
		counts[next.ordinal()]++;
		int layer = index / (blueprint.sizeX() * blueprint.sizeZ());
		openPerLayer[layer] += isOpen(next) - isOpen(previous);
		int palette = blueprint.paletteIndexAt(index);
		placedPerPalette[palette] += (next == Status.CORRECT ? 1 : 0) - (previous == Status.CORRECT ? 1 : 0);
	}

	private static int isOpen(Status status) {
		return status == Status.WRONG || status == Status.MISSING ? 1 : 0;
	}

	/** The lowest layer with a missing or wrong block, or -1. */
	public int lowestOpenLayer() {
		for (int y = range.from(); y <= range.to(); y++) {
			if (openPerLayer[y] > 0) {
				return y;
			}
		}
		return -1;
	}

	/** How many positions of a palette entry the layers being built need. */
	public long needed(int paletteIndex) {
		return neededPerPalette[paletteIndex];
	}

	/** How many of them are known to be correct. */
	public long placed(int paletteIndex) {
		return placedPerPalette[paletteIndex];
	}

	/**
	 * The next {@code budget} tracked positions to look at, going round and round, so a large structure is checked a
	 * slice at a time instead of all at once.
	 */
	public int[] nextToCheck(int budget) {
		int size = Math.min(budget, tracked.length);
		int[] result = new int[size];
		for (int i = 0; i < size; i++) {
			result[i] = tracked[cursor];
			cursor = (cursor + 1) % tracked.length;
		}
		return result;
	}
}
