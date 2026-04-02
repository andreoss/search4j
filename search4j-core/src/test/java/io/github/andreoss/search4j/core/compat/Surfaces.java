/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Catalogue of the surfaces the compatibility oracle covers, and of those
 * it does not.
 */
package io.github.andreoss.search4j.core.compat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Named surfaces and the reference-derived origin of their expectation.
 *
 * <p>A checked surface carries at least one expectation derived from the
 * reference sources or from a reference-produced fixture, and this suite
 * compares this project against it. An unchecked surface has no such
 * expectation, because nothing to derive it from is held here.</p>
 *
 * @since 0.1.0
 */
final class Surfaces {

    /**
     * Every declared surface by identifier.
     */
    private static final Map<String, Surface> ALL = new LinkedHashMap<>(0);

    /**
     * Identifiers whose checks ran.
     */
    private static final Set<String> EXERCISED = new LinkedHashSet<>(0);

    static {
        Surfaces.add(
            "vidrio.version.record", "vidrio",
            "reference version file of a 112-document database", true
        );
        Surfaces.add(
            "vidrio.table.postlist", "vidrio",
            "reference posting table, one all-document chunk", true
        );
        Surfaces.add(
            "vidrio.table.termlist", "vidrio",
            "reference term list table, one empty entry per document", true
        );
        Surfaces.add(
            "vidrio.table.docdata", "vidrio",
            "reference document data table, damaged in its last item", true
        );
        Surfaces.add(
            "vidrio.table.docdata2", "vidrio",
            "reference document data table with an oversized item", true
        );
        Surfaces.add(
            "vidrio.database.single", "vidrio",
            "reference single-file databases are not table files", true
        );
        Surfaces.add(
            "vidrio.database.read", "vidrio",
            "reference database read through the public API", true
        );
        Surfaces.add(
            "vidrio.positions", "vidrio",
            "reference-derived positions read through the public API", true
        );
        Surfaces.add(
            "vidrio.values", "vidrio",
            "reference-derived document values read through the public API",
            true
        );
        Surfaces.add(
            "vidrio.metadata", "vidrio",
            "reference-derived metadata keys and values through the public API",
            true
        );
        Surfaces.add(
            "vidrio.write.identity", "vidrio",
            "reference blocks rewritten through the public API", true
        );
        Surfaces.add(
            "ser.sortable.uints", "ser",
            "reference integer packing bands", true
        );
        Surfaces.add(
            "ser.sortable.strings", "ser",
            "reference string packing with escaped zero bytes", true
        );
        Surfaces.add(
            "ser.sortable.doubles", "ser",
            "reference variable-length sortable double vectors", true
        );
        Surfaces.add(
            "ser.varints", "ser",
            "reference variable-length integer encoding", true
        );
        Surfaces.add(
            "codec.prefix", "codec",
            "reference prefix-compressed key list", true
        );
        Surfaces.add(
            "query.description", "query",
            "reference description texts of every operator", true
        );
        Surfaces.add(
            "query.wire", "query",
            "reference vectors for match-all, conjunctions, unions and terms",
            true
        );
        Surfaces.add(
            "matcher.boolean", "matcher",
            "source-derived docid streams for deterministic child lists",
            true
        );
        Surfaces.add(
            "ser.zlib", "ser",
            "no reference output held, only a round trip of this project",
            false
        );
        Surfaces.add(
            "ser.bitstream", "ser",
            "reference least-significant-bit-first byte vector", true
        );
        Surfaces.add(
            "matcher.phrase", "matcher",
            "positional operators not implemented", false
        );
        Surfaces.add(
            "matcher.value", "matcher",
            "value based operators not implemented", false
        );
        Surfaces.add(
            "weight.schemes", "weight",
            "weighting schemes not implemented", false
        );
        Surfaces.add(
            "enquire", "api",
            "match set production not implemented", false
        );
        Surfaces.add(
            "queryparser", "queryparser",
            "query parser and term generator not implemented", false
        );
        Surfaces.add(
            "stemming", "stem",
            "stemming algorithms not implemented", false
        );
        Surfaces.add(
            "geospatial", "geo",
            "geospatial posting sources not implemented", false
        );
        Surfaces.add(
            "compaction", "backend",
            "compaction and database check not implemented", false
        );
        Surfaces.add(
            "multi", "backend",
            "multi-database dispatch not implemented", false
        );
        Surfaces.add(
            "remote", "net",
            "remote protocol and replication not implemented", false
        );
        Surfaces.add(
            "crosscompat.binary", "build",
            "no reference binary may be built or run here", false
        );
        Surfaces.add(
            "benchmarks", "build",
            "no reference timings held", false
        );
    }

    /**
     * Utility class, no instances.
     */
    private Surfaces() {
    }

    /**
     * Records that a checked surface was compared against the reference.
     *
     * @param id Identifier of a checked surface
     */
    static void covered(final String id) {
        if (!Surfaces.ALL.containsKey(id)) {
            throw new IllegalArgumentException(
                String.format("Undeclared surface: %s", id)
            );
        }
        if (!Surfaces.ALL.get(id).checked()) {
            throw new IllegalArgumentException(
                String.format("Unchecked surface: %s", id)
            );
        }
        Surfaces.EXERCISED.add(id);
    }

    /**
     * Identifiers of the checked surfaces.
     *
     * @return Identifiers in declaration order
     */
    static List<String> checked() {
        return Surfaces.ALL.values().stream()
            .filter(Surface::checked)
            .map(Surface::id)
            .toList();
    }

    /**
     * Summary of every surface and its state in this run.
     *
     * @return Lines of the report, one per surface then a total line
     */
    static List<String> report() {
        final List<String> lines = new ArrayList<>(0);
        long checked = 0L;
        long missed = 0L;
        long uncovered = 0L;
        lines.add("compatibility oracle");
        for (final Surface surface : Surfaces.ALL.values()) {
            final boolean exercised = Surfaces.EXERCISED.contains(surface.id());
            final String state = Surfaces.state(surface, exercised);
            if (surface.checked() && exercised) {
                checked += 1L;
            }
            if (surface.checked() && !exercised) {
                missed += 1L;
            }
            if (!surface.checked()) {
                uncovered += 1L;
            }
            lines.add(
                String.format(
                    "surface=%s area=%s state=%s origin=%s",
                    surface.id(), surface.area(), state, surface.origin()
                )
            );
        }
        lines.add(
            String.format(
                "surfaces covered=%d missed=%d uncovered=%d",
                checked, missed, uncovered
            )
        );
        return lines;
    }

    /**
     * Number of checked surfaces whose comparisons did not run.
     *
     * @return Count of missed surfaces
     */
    static long missed() {
        return Surfaces.checked().stream()
            .filter(id -> !Surfaces.EXERCISED.contains(id))
            .count();
    }

    private static void add(
        final String id, final String area, final String origin,
        final boolean checked
    ) {
        Surfaces.ALL.put(id, new Surface(id, area, origin, checked));
    }

    @SuppressWarnings("PMD.ConfusingTernary")
    private static String state(final Surface surface, final boolean exercised) {
        final String result;
        if (!surface.checked()) {
            result = "UNCOVERED";
        } else if (exercised) {
            result = "COVERED";
        } else {
            result = "MISSED";
        }
        return result;
    }

    /**
     * A surface of the reference semantics this project implements.
     *
     * @param id Stable identifier
     * @param area Code area the surface belongs to
     * @param origin Where the expectation comes from, or why none is held
     * @param checked Whether this suite compares this project against it
     */
    record Surface(String id, String area, String origin, boolean checked) {

        /**
         * Creates a surface.
         *
         * @param id Stable identifier
         * @param area Code area the surface belongs to
         * @param origin Where the expectation comes from
         * @param checked Whether this suite compares against it
         */
        Surface {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(area, "area");
            Objects.requireNonNull(origin, "origin");
        }
    }
}
