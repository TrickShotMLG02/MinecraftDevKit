package com.trickshotmlg.devkit.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable catalog of translation references declared by an application. */
public final class TextCatalog {
    private final Map<String, Entry> entries;
    private final List<DeclarationConflict> conflicts;

    private TextCatalog(Map<String, Entry> entries, List<DeclarationConflict> conflicts) {
        this.entries = Collections.unmodifiableMap(new TreeMap<>(entries));
        this.conflicts = List.copyOf(conflicts);
    }

    /** Returns a new mutable builder. */
    /**
     * Creates a collector for translated references.
     * @return an empty builder
     */
    public static Builder builder() { return new Builder(); }

    /** Returns a stable key-sorted map of translated references. */
    /** @return references in translation-key order */
    public Map<String, Entry> entries() { return entries; }

    /** Returns conflicting fallback declarations for duplicate keys. */
    /** @return conflicting fallback declarations in declaration order */
    public List<DeclarationConflict> conflicts() { return conflicts; }

    /** One key's optional fallback and all known declaration sources. */
    public record Entry(String key, Optional<String> fallback, Set<String> categories, Set<String> sources) {
        /**
         * Validates and snapshots reference metadata.
         * @param key translation key
         * @param fallback optional display fallback
         * @param categories declaration categories
         * @param sources declaration source labels
         */
        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(fallback, "fallback");
            categories = Collections.unmodifiableSet(new TreeSet<>(categories));
            sources = Collections.unmodifiableSet(new TreeSet<>(sources));
        }
    }

    /** A duplicate key was declared with conflicting fallback metadata. */
    public record DeclarationConflict(String key, Optional<String> firstFallback,
                                      Optional<String> conflictingFallback, String source) {
        /**
         * Describes two declarations that disagree about fallback text.
         * @param key translation key
         * @param firstFallback first declared fallback
         * @param conflictingFallback later conflicting fallback
         * @param source later declaration source
         */
        public DeclarationConflict {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(firstFallback, "firstFallback");
            Objects.requireNonNull(conflictingFallback, "conflictingFallback");
            Objects.requireNonNull(source, "source");
        }
    }

    /** Builder for collecting references from commands and application-owned text features. */
    public static final class Builder {
        private final Map<String, MutableEntry> entries = new TreeMap<>();
        private final List<DeclarationConflict> conflicts = new ArrayList<>();

        /** Adds a reference. Literal text is ignored because it has no translation key. */
        /**
         * Adds one reference; literal references are intentionally ignored.
         * @param reference text reference
         * @param category feature category such as {@code command-description}
         * @param source human-readable declaring feature
         * @return this builder
         */
        public Builder add(TextRef reference, String category, String source) {
            Objects.requireNonNull(reference, "reference");
            String cleanCategory = requireLabel(category, "category");
            String cleanSource = requireLabel(source, "source");
            if (!(reference instanceof TextRef.Translated translated)) return this;
            String key = translated.key();
            MutableEntry existing = entries.get(key);
            if (existing == null) {
                existing = new MutableEntry(key, translated.fallback());
                entries.put(key, existing);
            } else if (!existing.fallback.equals(translated.fallback())) {
                conflicts.add(new DeclarationConflict(key, existing.fallback, translated.fallback(), cleanSource));
            }
            existing.categories.add(cleanCategory);
            existing.sources.add(cleanSource);
            return this;
        }

        /** Adds all references under one source category and source label. */
        /**
         * Adds references with shared metadata.
         * @param references references to add
         * @param category feature category
         * @param source declaring feature
         * @return this builder
         */
        public Builder addAll(Iterable<? extends TextRef> references, String category, String source) {
            Objects.requireNonNull(references, "references");
            for (TextRef reference : references) add(reference, category, source);
            return this;
        }

        /** Retains a conflict imported from a generated reference manifest. */
        /**
         * Adds a previously recorded conflict.
         * @param conflict conflict to retain
         * @return this builder
         */
        public Builder addConflict(DeclarationConflict conflict) {
            conflicts.add(Objects.requireNonNull(conflict, "conflict"));
            return this;
        }

        /** Merges another immutable reference catalog, including its source labels and conflicts. */
        /**
         * Merges another immutable catalog.
         * @param catalog catalog to merge
         * @return this builder
         */
        public Builder addCatalog(TextCatalog catalog) {
            Objects.requireNonNull(catalog, "catalog");
            for (Entry entry : catalog.entries.values()) {
                String category = entry.categories().stream().findFirst().orElse("application");
                String source = entry.sources().stream().findFirst().orElse("application");
                add(new TextRef.Translated(entry.key(), entry.fallback()), category, source);
                for (String extraCategory : entry.categories().stream().skip(1).toList())
                    add(new TextRef.Translated(entry.key(), entry.fallback()), extraCategory, source);
                for (String extraSource : entry.sources().stream().skip(1).toList())
                    add(new TextRef.Translated(entry.key(), entry.fallback()), category, extraSource);
            }
            catalog.conflicts.forEach(this::addConflict);
            return this;
        }

        /** Builds an immutable catalog snapshot. */
        /** @return an immutable reference snapshot */
        public TextCatalog build() {
            Map<String, Entry> immutable = new TreeMap<>();
            entries.forEach((key, mutable) -> immutable.put(key, mutable.toEntry()));
            return new TextCatalog(immutable, conflicts);
        }

        private static String requireLabel(String value, String name) {
            Objects.requireNonNull(value, name);
            String trimmed = value.trim();
            if (trimmed.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
            return trimmed;
        }

        private static final class MutableEntry {
            private final String key;
            private final Optional<String> fallback;
            private final Set<String> categories = new LinkedHashSet<>();
            private final Set<String> sources = new LinkedHashSet<>();
            private MutableEntry(String key, Optional<String> fallback) { this.key = key; this.fallback = fallback; }
            private Entry toEntry() { return new Entry(key, fallback, categories, sources); }
        }
    }
}
