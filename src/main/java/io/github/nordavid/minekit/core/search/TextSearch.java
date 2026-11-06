package io.github.nordavid.minekit.core.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Filters and ranks items (Quick Actions, recipes, ...) by a free-text query. Pure logic, independent of Minecraft.
 *
 * <p>Matching ignores case and accents ("offnen" finds "Öffnen"). Every word of the query must occur in the
 * searchable text. Ranking: text starts with the query, then every query word starts a word of the text, then any
 * other match. Ties keep the original order.
 */
public final class TextSearch {
	private static final Pattern MARKS = Pattern.compile("\\p{M}+");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private TextSearch() {
	}

	public static <T> List<T> filter(List<T> items, Function<T, String> searchableText, String query) {
		String normalizedQuery = normalize(query);
		if (normalizedQuery.isEmpty()) {
			return List.copyOf(items);
		}
		String[] tokens = WHITESPACE.split(normalizedQuery);

		record Match<T>(T item, int rank, int index) {
		}
		List<Match<T>> matches = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			T item = items.get(i);
			int rank = rank(normalize(searchableText.apply(item)), normalizedQuery, tokens);
			if (rank >= 0) {
				matches.add(new Match<>(item, rank, i));
			}
		}
		matches.sort(Comparator.comparingInt((Match<T> match) -> match.rank()).thenComparingInt(Match::index));
		return matches.stream().map(Match::item).toList();
	}

	/** Lower-case, accent-free, single-spaced form used for matching. */
	static String normalize(String text) {
		String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
		return WHITESPACE.matcher(MARKS.matcher(decomposed).replaceAll("")).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
	}

	/** 0 = prefix of the whole text, 1 = every token starts a word, 2 = every token occurs, -1 = no match. */
	private static int rank(String text, String query, String[] tokens) {
		boolean allWordStarts = true;
		for (String token : tokens) {
			if (!text.contains(token)) {
				return -1;
			}
			if (!startsAWord(text, token)) {
				allWordStarts = false;
			}
		}
		if (text.startsWith(query)) {
			return 0;
		}
		return allWordStarts ? 1 : 2;
	}

	private static boolean startsAWord(String text, String token) {
		int from = 0;
		while (true) {
			int position = text.indexOf(token, from);
			if (position < 0) {
				return false;
			}
			if (position == 0 || !Character.isLetterOrDigit(text.charAt(position - 1))) {
				return true;
			}
			from = position + 1;
		}
	}
}
