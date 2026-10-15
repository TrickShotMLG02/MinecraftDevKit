package com.trickshotmlg.devkit.localization;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;

/** Deterministic JSON serialization for localization reports. */
public final class LocalizationReportJson {
    private LocalizationReportJson() { }
    /**
     * Serializes a report with stable finding order and JSON escaping.
     * @param report report to serialize
     * @return formatted JSON document
     * @throws IOException if JSON serialization fails
     */
    public static String write(LocalizationReport report) throws IOException {
        StringWriter writer = new StringWriter();
        try (JsonGenerator json = new JsonFactory().createGenerator(writer).useDefaultPrettyPrinter()) {
            json.writeStartObject(); json.writeBooleanField("failsBuild", report.failsBuild());
            json.writeObjectFieldStart("counts"); for (var count : report.counts().entrySet()) json.writeNumberField(count.getKey().toLowerCase(java.util.Locale.ROOT), count.getValue());
            json.writeEndObject(); json.writeArrayFieldStart("findings");
            for (LocalizationFinding finding : report.findings()) {
                json.writeStartObject(); json.writeStringField("severity", finding.severity().name());
                json.writeStringField("category", finding.category().name());
                if (finding.locale().isPresent()) json.writeStringField("locale", finding.locale().orElseThrow());
                json.writeStringField("key", finding.key()); json.writeStringField("message", finding.message());
                json.writeBooleanField("fallbackAvailable", finding.fallbackAvailable()); json.writeEndObject();
            }
            json.writeEndArray(); json.writeEndObject();
        }
        return writer.toString();
    }
}
