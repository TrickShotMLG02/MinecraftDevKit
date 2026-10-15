package com.trickshotmlg.devkit.localization;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.trickshotmlg.devkit.text.TextCatalog;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.TreeSet;

/** Reads and writes the stable JSON interchange format used by localization tooling. */
public final class TextCatalogManifest {
    private static final JsonFactory JSON = new JsonFactory();
    private TextCatalogManifest() { }
    /** Writes a UTF-8 manifest to a file. */
    /**
     * @param catalog reference catalog
     * @param path destination
     * @throws IOException if the file cannot be written
     */
    public static void write(TextCatalog catalog, Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) { write(catalog, writer); }
    }
    /**
     * Writes a stable schema-versioned JSON manifest.
     * @param catalog reference catalog
     * @param writer destination writer
     * @throws IOException if JSON cannot be written
     */
    public static void write(TextCatalog catalog, Writer writer) throws IOException {
        try (JsonGenerator json = JSON.createGenerator(writer).useDefaultPrettyPrinter()) {
            json.writeStartObject(); json.writeNumberField("schemaVersion", 1);
            json.writeArrayFieldStart("references");
            for (TextCatalog.Entry entry : catalog.entries().values()) {
                json.writeStartObject(); json.writeStringField("key", entry.key());
                if (entry.fallback().isPresent()) json.writeStringField("fallback", entry.fallback().orElseThrow());
                json.writeArrayFieldStart("categories"); for (String value : entry.categories()) json.writeString(value); json.writeEndArray();
                json.writeArrayFieldStart("sources"); for (String value : entry.sources()) json.writeString(value); json.writeEndArray();
                json.writeEndObject();
            }
            json.writeEndArray(); json.writeArrayFieldStart("conflicts");
            for (TextCatalog.DeclarationConflict conflict : catalog.conflicts()) {
                json.writeStartObject(); json.writeStringField("key", conflict.key());
                conflict.firstFallback().ifPresent(value -> writeQuiet(json, "firstFallback", value));
                conflict.conflictingFallback().ifPresent(value -> writeQuiet(json, "conflictingFallback", value));
                json.writeStringField("source", conflict.source()); json.writeEndObject();
            }
            json.writeEndArray(); json.writeEndObject();
        }
    }
    /**
     * Reads a UTF-8 reference manifest.
     * @param path manifest file
     * @return immutable catalog
     * @throws IOException if the manifest is malformed or unavailable
     */
    public static TextCatalog read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { return read(reader); }
    }
    /**
     * Reads a reference manifest from JSON.
     * @param reader source reader
     * @return immutable catalog
     * @throws IOException if the document is malformed or has an unsupported schema
     */
    public static TextCatalog read(Reader reader) throws IOException {
        TextCatalog.Builder catalog = TextCatalog.builder();
        try (JsonParser parser = JSON.createParser(reader)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw new IOException("Manifest must be a JSON object");
            boolean version = false, refs = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName(); parser.nextToken();
                if ("schemaVersion".equals(field)) {
                    if (parser.getIntValue() != 1) throw new IOException("Unsupported translation manifest schema version");
                    version = true;
                } else if ("references".equals(field)) {
                    if (parser.currentToken() != JsonToken.START_ARRAY) throw new IOException("references must be an array");
                    refs = true;
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        if (parser.currentToken() != JsonToken.START_OBJECT) throw new IOException("reference must be an object");
                        String key = null, fallback = null; ArrayList<String> categories = new ArrayList<>(), sources = new ArrayList<>();
                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            String property = parser.currentName(); parser.nextToken();
                            if ("key".equals(property)) key = parser.getValueAsString();
                            else if ("fallback".equals(property)) fallback = parser.getValueAsString();
                            else if ("categories".equals(property)) readStrings(parser, categories);
                            else if ("sources".equals(property)) readStrings(parser, sources);
                            else parser.skipChildren();
                        }
                        if (key == null || key.isBlank()) throw new IOException("reference is missing key");
                        if (categories.isEmpty()) categories.add("manifest"); if (sources.isEmpty()) sources.add("manifest");
                        catalog.add(new com.trickshotmlg.devkit.text.TextRef.Translated(key, Optional.ofNullable(fallback)), categories.get(0), sources.get(0));
                        for (int i = 1; i < Math.max(categories.size(), sources.size()); i++)
                            catalog.add(new com.trickshotmlg.devkit.text.TextRef.Translated(key, Optional.ofNullable(fallback)),
                                    categories.get(Math.min(i, categories.size()-1)), sources.get(Math.min(i, sources.size()-1)));
                    }
                } else if ("conflicts".equals(field)) {
                    if (parser.currentToken() != JsonToken.START_ARRAY) throw new IOException("conflicts must be an array");
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        String key = null, first = null, second = null, source = "manifest";
                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            String property = parser.currentName(); parser.nextToken();
                            switch (property) {
                                case "key" -> key = parser.getValueAsString();
                                case "firstFallback" -> first = parser.getValueAsString();
                                case "conflictingFallback" -> second = parser.getValueAsString();
                                case "source" -> source = parser.getValueAsString();
                                default -> parser.skipChildren();
                            }
                        }
                        if (key == null) throw new IOException("conflict is missing key");
                        catalog.addConflict(new TextCatalog.DeclarationConflict(key, Optional.ofNullable(first),
                                Optional.ofNullable(second), source));
                    }
                } else parser.skipChildren();
            }
            if (!version || !refs) throw new IOException("Manifest requires schemaVersion and references");
            if (parser.nextToken() != null) throw new IOException("Unexpected trailing JSON data");
        } catch (RuntimeException e) { throw new IOException("Invalid translation reference manifest: " + e.getMessage(), e); }
        return catalog.build();
    }
    private static void readStrings(JsonParser parser, ArrayList<String> values) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) throw new IOException("metadata must be an array");
        while (parser.nextToken() != JsonToken.END_ARRAY) values.add(parser.getValueAsString());
    }
    private static void writeQuiet(JsonGenerator json, String field, String value) {
        try { json.writeStringField(field, value); } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
