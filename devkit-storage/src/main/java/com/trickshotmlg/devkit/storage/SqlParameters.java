package com.trickshotmlg.devkit.storage;

import java.util.ArrayList;
import java.util.List;

/** Immutable, eagerly validated parameter list. Mutable byte arrays are snapshotted on input. */
public final class SqlParameters {
    private static final SqlParameters EMPTY = new SqlParameters(List.of());
    private final List<SqlParameter> values;

    private SqlParameters(List<SqlParameter> values) { this.values = List.copyOf(values); }

    public static SqlParameters none() { return EMPTY; }

    public static SqlParameters of(Object... values) {
        if (values == null || values.length == 0) return EMPTY;
        List<SqlParameter> copy = new ArrayList<>(values.length);
        for (Object value : values) copy.add(SqlParameter.of(value));
        return new SqlParameters(copy);
    }

    public int size() { return values.size(); }
    void bind(java.sql.PreparedStatement statement) throws java.sql.SQLException {
        for (int i = 0; i < values.size(); i++) values.get(i).bind(statement, i + 1);
    }

    @Override public String toString() { return "SqlParameters[count=" + values.size() + "]"; }
}
