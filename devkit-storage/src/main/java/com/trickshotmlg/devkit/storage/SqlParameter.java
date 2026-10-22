package com.trickshotmlg.devkit.storage;

import java.sql.JDBCType;
import java.util.Objects;

/** Explicitly typed SQL NULL, or a validated and snapshotted JDBC parameter value. */
public final class SqlParameter {
    private final Object value;
    private final Integer sqlType;

    private SqlParameter(Object value, Integer sqlType) {
        this.value = snapshot(value);
        this.sqlType = sqlType;
    }

    /** Creates a typed SQL NULL. */
    public static SqlParameter nullOf(JDBCType type) {
        return new SqlParameter(null, Objects.requireNonNull(type, "type").getVendorTypeNumber());
    }

    static SqlParameter of(Object value) {
        if (value instanceof SqlParameter parameter) return parameter.copy();
        return new SqlParameter(value, null);
    }

    private SqlParameter copy() { return new SqlParameter(value, sqlType); }

    private static Object snapshot(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Byte
                || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof java.math.BigDecimal)
            return value;
        if (value instanceof byte[] bytes) return bytes.clone();
        if (value instanceof java.util.UUID uuid) return uuid.toString();
        if (value instanceof java.time.Instant instant) return instant.toString();
        throw new IllegalArgumentException("Unsupported SQL parameter type: " + value.getClass().getName());
    }

    void bind(java.sql.PreparedStatement statement, int index) throws java.sql.SQLException {
        if (sqlType != null) statement.setNull(index, sqlType);
        else if (value == null) statement.setNull(index, java.sql.Types.NULL);
        else if (value instanceof byte[] bytes) statement.setBytes(index, bytes.clone());
        else statement.setObject(index, value);
    }

    @Override public String toString() { return "SqlParameter[<redacted>]"; }
}
