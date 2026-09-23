package com.enchantcalc.plan;

import com.enchantcalc.core.AnvilMath;
import com.enchantcalc.core.Leaf;
import com.enchantcalc.core.Mode;
import com.enchantcalc.core.Optimizer;
import com.enchantcalc.data.Books;
import com.enchantcalc.data.EnchantInfo;
import com.enchantcalc.data.Target;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Turns a selection into books to combine, and solves the order off the render thread. */
public final class Planner {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "EnchantCalc planner");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static final AtomicLong LATEST = new AtomicLong();

    private Planner() {
    }

    /**
     * Everything a plan depends on.
     *
     * @param wanted   enchantments to add, at their final level
     * @param owned    enchanted books the player has
     * @param creative players with infinite materials have no "Too Expensive!" limit
     */
    public record Request(Target target, Map<EnchantInfo, Integer> wanted, List<Books.Owned> owned, Mode mode, boolean creative) {
    }

    /** One book the plan applies: one the player owns, or a fresh single-enchantment book they still need. */
    public record LeafBook(Map<EnchantInfo, Integer> enchantments, Books.Owned owned) {
        public int value() {
            int value = 0;
            for (Map.Entry<EnchantInfo, Integer> entry : enchantments.entrySet()) {
                value += entry.getKey().bookValue(entry.getValue());
            }
            return value;
        }
    }

    /**
     * Solves on a background thread; the returned future completes on the client thread. A request
     * that is still queued when a newer one arrives is skipped (its future fails with a
     * {@link CancellationException}), so fast clicking never builds up a backlog of big searches.
     */
    public static CompletableFuture<PlanView> plan(Request request) {
        List<LeafBook> books = chooseBooks(request.wanted(), request.owned());
        List<Leaf> leaves = new ArrayList<>(books.size());
        for (int i = 0; i < books.size(); i++) {
            LeafBook book = books.get(i);
            leaves.add(new Leaf(i, book.value(), book.owned() == null ? 0 : book.owned().repairCost()));
        }
        int limit = request.creative() ? AnvilMath.NO_LIMIT : AnvilMath.MAX_SURVIVAL_COST;
        long generation = LATEST.incrementAndGet();
        Minecraft minecraft = Minecraft.getInstance();
        return CompletableFuture
            .supplyAsync(() -> {
                if (generation != LATEST.get()) {
                    throw new CancellationException("superseded by a newer plan request");
                }
                return Optimizer.solve(request.target().repairCost(), leaves, request.mode(), limit);
            }, WORKER)
            .thenApplyAsync(plan -> PlanView.create(request, books, plan), minecraft::execute);
    }

    /**
     * Uses owned books where they hold exactly wanted enchantments at exactly the wanted levels,
     * preferring books that cover more enchantments, then books with less prior work. Everything
     * else becomes a fresh book with one enchantment.
     */
    static List<LeafBook> chooseBooks(Map<EnchantInfo, Integer> wanted, List<Books.Owned> owned) {
        List<Books.Owned> usable = new ArrayList<>();
        for (Books.Owned book : owned) {
            boolean exact = book.enchantments().entrySet().stream()
                .allMatch(entry -> Objects.equals(wanted.get(entry.getKey()), entry.getValue()));
            if (exact) {
                usable.add(book);
            }
        }
        usable.sort(Comparator.<Books.Owned>comparingInt(book -> -book.enchantments().size())
            .thenComparingInt(Books.Owned::repairCost)
            .thenComparingInt(Books.Owned::slot));
        Set<EnchantInfo> covered = new HashSet<>();
        List<LeafBook> books = new ArrayList<>();
        for (Books.Owned book : usable) {
            if (Collections.disjoint(book.enchantments().keySet(), covered)) {
                covered.addAll(book.enchantments().keySet());
                books.add(new LeafBook(book.enchantments(), book));
            }
        }
        for (Map.Entry<EnchantInfo, Integer> entry : wanted.entrySet()) {
            if (!covered.contains(entry.getKey())) {
                books.add(new LeafBook(Map.of(entry.getKey(), entry.getValue()), null));
            }
        }
        return books;
    }
}
