package com.charlie.ikibaho.issue.internal.rank;

import java.util.ArrayList;
import java.util.List;

/**
 * Ranks that sort lexicographically, so reordering one issue is one UPDATE.
 * <p>
 * The alternative -- an integer position column -- makes every drag rewrite the
 * position of every row after it. That is fine for ten issues and unusable for a
 * backlog of two thousand, which is exactly when reordering matters.
 * <p>
 * A rank is a base-36 string read as digits after an implied decimal point, so
 * "b" sits between "a" and "c", and "ai" sits between "a" and "b". There is
 * always room to insert: when two neighbours are adjacent digits the rank simply
 * grows a character. Ranks lengthen slowly under repeated insertion at the same
 * point, which is what a periodic rebalance is for -- see {@link #isDegenerate}.
 *
 * <p>Immutable, dependency-free, and deliberately not a Spring bean.
 *
 * <p><b>On its location:</b> ARCHITECTURE.md sketches this under {@code board}.
 * It lives here because ranks are stored on the issue row and only the issue
 * module may write those; putting the algorithm in board would have board
 * writing issue tables, or issue depending on board and closing a cycle. Board
 * consumes the resulting order rather than producing it.
 */
public final class LexoRank {

    private static final String DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int BASE = DIGITS.length();

    /**
     * Guards against a corrupt rank forcing an unbounded loop. Nothing this class
     * generates comes close; ranks grow one character per collision.
     */
    private static final int MAX_LENGTH = 64;

    private LexoRank() {
    }

    /**
     * The rank for the first issue in an empty list: mid-range, room on both sides.
     */
    public static String initial() {
        return between(null, null);
    }

    /**
     * A rank that sorts after everything currently ranked.
     */
    public static String after(String prev) {
        return between(prev, null);
    }

    /**
     * A rank that sorts before everything currently ranked.
     */
    public static String before(String next) {
        return between(null, next);
    }

    /**
     * A rank strictly between two neighbours. Either bound may be null, meaning
     * "no neighbour on that side".
     *
     * @throws IllegalArgumentException if the bounds are out of order, equal, or
     *                                  leave no room (see {@link #before}).
     */
    public static String between(String prev, String next) {
        validate(prev, "prev");
        validate(next, "next");

        if (prev != null && next != null && prev.compareTo(next) >= 0) {
            throw new IllegalArgumentException(
                    "Ranks are out of order: '" + prev + "' is not before '" + next + "'");
        }
        // No string sorts below one made entirely of the lowest digit, so there is
        // genuinely nothing to return. This class never emits such a rank -- every
        // rank it produces ends in a digit above the minimum -- so reaching here
        // means a rank was written by something else.
        if (next != null && isAllMinimum(next)) {
            throw new IllegalArgumentException("No rank exists before '" + next + "'");
        }

        StringBuilder rank = new StringBuilder();
        for (int i = 0; i < MAX_LENGTH; i++) {
            int low = i < length(prev) ? digitAt(prev, i) : 0;
            int high = i < length(next) ? digitAt(next, i) : BASE;

            if (high - low > 1) {
                // Room to split here. The midpoint is strictly above low and strictly
                // below high, which is what makes the result land between the bounds.
                rank.append(DIGITS.charAt(low + (high - low) / 2));
                return rank.toString();
            }

            // Neighbours are adjacent or identical at this position: keep the lower
            // bound's digit and look for room one position deeper.
            rank.append(DIGITS.charAt(low));
        }
        throw new IllegalStateException("Rank exceeded " + MAX_LENGTH + " characters; rebalance");
    }

    /**
     * Evenly spaced ranks for seeding an existing, already-ordered list.
     * <p>
     * Chaining {@link #after} {@code count} times would work but degenerates:
     * each rank lands halfway to the top, so they crowd together and lengthen.
     * Spreading them across the whole space up front leaves even gaps for later
     * insertion.
     */
    public static List<String> evenlySpaced(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        if (count == 0) {
            return List.of();
        }

        // Shortest width whose value space comfortably exceeds the number of slots.
        int width = 1;
        long space = BASE;
        while (space <= (long) count + 1) {
            width++;
            space *= BASE;
        }

        long step = space / (count + 1);
        List<String> ranks = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            ranks.add(encode(i * step, width));
        }
        return ranks;
    }

    /**
     * True when ranks have grown long enough to be worth rebalancing.
     * <p>
     * Repeatedly dropping an issue into the same gap adds a character each time.
     * Nothing breaks -- comparison still works -- but the column grows and the
     * next insert grows it further, so a background rebalance should reseed with
     * {@link #evenlySpaced}.
     */
    public static boolean isDegenerate(String rank) {
        return rank != null && rank.length() > 8;
    }

    private static String encode(long value, int width) {
        StringBuilder out = new StringBuilder();
        long remaining = value;
        for (int i = 0; i < width; i++) {
            out.insert(0, DIGITS.charAt((int) (remaining % BASE)));
            remaining /= BASE;
        }
        return out.toString();
    }

    private static void validate(String rank, String name) {
        if (rank == null) {
            return;
        }
        if (rank.isEmpty()) {
            throw new IllegalArgumentException(name + " rank must not be empty");
        }
        for (int i = 0; i < rank.length(); i++) {
            if (DIGITS.indexOf(rank.charAt(i)) < 0) {
                throw new IllegalArgumentException(
                        name + " rank has a character outside base 36: '" + rank + "'");
            }
        }
    }

    private static boolean isAllMinimum(String rank) {
        for (int i = 0; i < rank.length(); i++) {
            if (rank.charAt(i) != DIGITS.charAt(0)) {
                return false;
            }
        }
        return true;
    }

    private static int length(String rank) {
        return rank == null ? 0 : rank.length();
    }

    private static int digitAt(String rank, int index) {
        return DIGITS.indexOf(rank.charAt(index));
    }
}
