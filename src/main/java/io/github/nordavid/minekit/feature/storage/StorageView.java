package io.github.nordavid.minekit.feature.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/** Filtering and ordering of the Storage Finder. Pure. */
public final class StorageView {
	private StorageView() {
	}

	/** Container type groups the finder can filter by. */
	public enum TypeFilter {
		ALL,
		CHESTS,
		BARRELS,
		SHULKER_BOXES,
		ENDER_CHEST,
		OTHER;

		public String translationKey() {
			return "minekit.storage.filter." + name().toLowerCase(Locale.ROOT);
		}

		public TypeFilter next() {
			TypeFilter[] all = values();
			return all[(ordinal() + 1) % all.length];
		}

		public boolean matches(String type) {
			return this == ALL || this == groupOf(type);
		}

		static TypeFilter groupOf(String type) {
			return switch (type) {
				case "minecraft:chest", "minecraft:trapped_chest", "minecraft:copper_chest" -> CHESTS;
				case "minecraft:barrel" -> BARRELS;
				case "minecraft:ender_chest" -> ENDER_CHEST;
				default -> type.endsWith("shulker_box") ? SHULKER_BOXES : OTHER;
			};
		}
	}

	/**
	 * Where the player is.
	 *
	 * @param dimension the player's dimension id
	 */
	public record Viewer(String dimension, double x, double y, double z) {
		/** Distance to a container in the viewer's dimension; {@code -1} for ender chests and other dimensions. */
		public double distanceTo(ContainerRecord record) {
			if (record.isEnderChest() || !record.dimension().equals(dimension)) {
				return -1;
			}
			return record.positions().stream().mapToDouble(pos -> pos.distanceTo(x, y, z)).min().orElse(-1);
		}
	}

	/**
	 * @param type          container types to show
	 * @param thisDimension only containers in the viewer's dimension (ender chests count as everywhere)
	 */
	public record Filter(TypeFilter type, boolean thisDimension) {
		public static final Filter ALL = new Filter(TypeFilter.ALL, false);

		boolean accepts(ContainerRecord record, Viewer viewer) {
			return type.matches(record.type()) && (!thisDimension || record.isEnderChest() || record.dimension().equals(viewer.dimension()));
		}
	}

	/** Item totals over the containers the filter accepts. */
	public static SortedMap<String, Long> itemTotals(StorageIndex index, Filter filter, Viewer viewer) {
		SortedMap<String, Long> totals = new TreeMap<>();
		for (ContainerRecord record : index.containers().values()) {
			if (filter.accepts(record, viewer)) {
				record.items().forEach((item, count) -> totals.merge(item, (long) count, Long::sum));
			}
		}
		return totals;
	}

	/**
	 * Accepted containers, holding {@code item} if one is given. Order: ender chest, then the viewer's dimension by
	 * distance, then other dimensions; with an item, containers holding more of it come first among equal distance.
	 */
	public static List<ContainerRecord> containers(StorageIndex index, @Nullable String item, Filter filter, Viewer viewer) {
		List<ContainerRecord> result = new ArrayList<>();
		for (ContainerRecord record : index.containers().values()) {
			if (filter.accepts(record, viewer) && (item == null || record.count(item) > 0)) {
				result.add(record);
			}
		}
		Comparator<ContainerRecord> order = Comparator
				.comparingInt((ContainerRecord record) -> record.isEnderChest() ? 0 : record.dimension().equals(viewer.dimension()) ? 1 : 2)
				.thenComparingDouble(viewer::distanceTo)
				.thenComparing(record -> item == null ? 0 : -record.count(item))
				.thenComparing(ContainerRecord::key);
		result.sort(order);
		return result;
	}

	/** Age as a translation key and its number: now, minutes, hours or days. */
	public record Age(String translationKey, long amount) {
		public static Age of(long ageMillis) {
			long minutes = Math.max(0, ageMillis) / 60_000;
			if (minutes < 1) {
				return new Age("minekit.storage.age.now", 0);
			}
			if (minutes < 60) {
				return new Age("minekit.storage.age.minutes", minutes);
			}
			long hours = minutes / 60;
			return hours < 48 ? new Age("minekit.storage.age.hours", hours) : new Age("minekit.storage.age.days", hours / 24);
		}
	}
}
