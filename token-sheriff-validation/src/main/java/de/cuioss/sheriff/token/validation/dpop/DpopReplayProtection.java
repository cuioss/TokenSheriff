/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.sheriff.token.validation.dpop;

import de.cuioss.tools.logging.CuiLogger;

import java.io.Closeable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe jti replay protection for DPoP proofs per RFC 9449 Section 11.1.
 * <p>
 * Tracks previously seen {@code jti} values to prevent replay attacks. Each jti
 * is stored with its insertion timestamp and evicted after the configured TTL.
 * </p>
 * <p>
 * This instance is shared across all issuers since jti values must be globally
 * unique per RFC 9449.
 * </p>
 * <h2>Soft capacity</h2>
 * <p>
 * The configured {@code maxSize} is a <em>soft cap</em>, not a hard maximum. A jti that is
 * still inside its freshness window is never evicted, because forgetting it early would
 * permit a replay. The cache can therefore exceed {@code maxSize} by at most the number of
 * jtis that arrive within one TTL window (arrival rate x TTL). Those entries are reclaimed
 * once their window has closed, either by the periodic expiry sweep (every 60 seconds) or
 * by a later over-cap insert.
 * </p>
 * <p>
 * An over-cap insert evicts oldest-first from an insertion-ordered queue kept next to the
 * map, and stops at the first entry still inside its window. When nothing is evictable it
 * therefore examines a constant number of entries rather than scanning the whole cache.
 * </p>
 * <p>
 * A daemon thread periodically evicts expired entries to prevent unbounded memory growth.
 * Call {@link #close()} to shut down the eviction scheduler.
 * </p>
 *
 * @since 1.0
 */
public class DpopReplayProtection implements Closeable {

    private static final CuiLogger LOGGER = new CuiLogger(DpopReplayProtection.class);
    private static final long EVICTION_INTERVAL_SECONDS = 60;

    /**
     * A stored jti value. The insertion order makes every entry unique, so a
     * {@code remove(key, value)} never removes a newer entry stored under the same jti.
     */
    private record Entry(long insertionOrder, long timestampMillis) {
    }

    /** Queue node referencing the map entry it was created for; stale once the map holds another value. */
    private record Node(String jti, Entry entry) {
    }

    /** Source of truth for replay decisions. */
    private final ConcurrentHashMap<String, Entry> seenJti = new ConcurrentHashMap<>();
    /** Insertion-ordered references to map entries; nodes whose entry was replaced or removed are skipped. */
    private final ConcurrentLinkedQueue<Node> insertionQueue = new ConcurrentLinkedQueue<>();
    /** Guards queue consumption (head inspection and removal) in the eviction paths. */
    private final Object evictionLock = new Object();
    private final AtomicLong insertionCounter = new AtomicLong(0);
    private final long ttlMillis;
    private final int maxSize;
    private final ScheduledExecutorService scheduler;
    private volatile int lastOverCapExamined;

    /**
     * Creates a new DpopReplayProtection with the given configuration.
     *
     * @param ttlSeconds maximum time in seconds to retain jti entries
     * @param maxSize    soft cap on the number of jti entries; in-window entries are never
     *                   evicted, so the cache may exceed it by the jtis arriving within one TTL
     */
    public DpopReplayProtection(long ttlSeconds, int maxSize) {
        this.ttlMillis = ttlSeconds * 1000;
        this.maxSize = maxSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dpop-replay-eviction");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::evictExpired,
                EVICTION_INTERVAL_SECONDS, EVICTION_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Checks if the given jti has been seen before and stores it if new.
     *
     * @param jti the unique identifier from the DPoP proof
     * @return {@code true} if the jti is new (not a replay), {@code false} if already seen
     */
    public boolean checkAndStore(String jti) {
        long now = System.currentTimeMillis();

        Entry newEntry = new Entry(insertionCounter.getAndIncrement(), now);

        // Atomically store only if absent, preventing race conditions
        Entry existing = seenJti.putIfAbsent(jti, newEntry);

        if (existing != null) {
            // Entry exists - accept only if it has expired and the atomic replacement wins
            if ((now - existing.timestampMillis()) >= ttlMillis && seenJti.replace(jti, existing, newEntry)) {
                insertionQueue.offer(new Node(jti, newEntry));
                return true;
            }
            return false;
        }

        insertionQueue.offer(new Node(jti, newEntry));

        // Successfully stored new jti - evict oldest expired entries if over the soft cap
        if (seenJti.size() > maxSize) {
            evictOldest();
        }

        return true; // New jti, not a replay
    }

    /**
     * Number of queue entries the most recent over-cap insert examined. Package-private probe
     * that lets tests pin the eviction cost without a wall-clock assertion.
     *
     * @return entries examined by the last over-cap eviction
     */
    int lastOverCapExamined() {
        return lastOverCapExamined;
    }

    /**
     * Periodic sweep over the whole queue. Unlike {@link #evictOldest()} it does not stop at the first
     * in-window entry: entry timestamps come from the wall clock, so after the clock steps backwards an
     * entry inserted later carries an earlier timestamp and can expire while an entry ahead of it is
     * still in its window. Stopping at that entry would keep every expired entry behind it until it
     * expired too, which breaks the soft-cap promise that expired entries are reclaimed by this sweep.
     */
    private void evictExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        synchronized (evictionLock) {
            var iterator = insertionQueue.iterator();
            while (iterator.hasNext()) {
                Node node = iterator.next();
                if (isStale(node)) {
                    iterator.remove();
                } else if (isExpired(node.entry(), now)) {
                    iterator.remove();
                    if (seenJti.remove(node.jti(), node.entry())) {
                        removed++;
                    }
                }
            }
        }
        if (removed > 0) {
            LOGGER.debug("DPoP replay cache eviction: removed %s expired entries, %s remaining", removed, seenJti.size());
        }
    }

    private void evictOldest() {
        long now = System.currentTimeMillis();
        int examined = 0;
        int evicted = 0;
        int overflow;
        synchronized (evictionLock) {
            overflow = seenJti.size() - maxSize;
            int remaining = overflow;
            // Pop from the head only while the head entry is stale or expired. Evicting a jti still
            // inside its window would forget it early and permit a replay, so the first in-window
            // entry ends the pass: every later entry was inserted after it. An empty queue ends it too.
            Node head = insertionQueue.peek();
            while (remaining > 0 && head != null) {
                examined++;
                if (isStale(head)) {
                    insertionQueue.poll();
                } else if (isExpired(head.entry(), now)) {
                    insertionQueue.poll();
                    if (seenJti.remove(head.jti(), head.entry())) {
                        evicted++;
                        remaining--;
                    }
                } else {
                    break;
                }
                head = insertionQueue.peek();
            }
        }
        lastOverCapExamined = examined;
        if (evicted < overflow) {
            LOGGER.debug("DPoP replay cache over soft capacity: retained %s in-window jtis to preserve replay protection (overflow=%s, evicted=%s)",
                    seenJti.size(), overflow, evicted);
        }
    }

    /** A node is stale once the map no longer holds its entry (replaced after expiry, or removed). */
    private boolean isStale(Node node) {
        return !node.entry().equals(seenJti.get(node.jti()));
    }

    private boolean isExpired(Entry entry, long now) {
        return (now - entry.timestampMillis()) >= ttlMillis;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        synchronized (evictionLock) {
            seenJti.clear();
            insertionQueue.clear();
        }
    }
}
