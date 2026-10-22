package com.trickshotmlg.devkit.storage;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/** Borrowed view over one ResultSet row. It becomes invalid as soon as the mapper returns. */
public final class Row {
    private final ResultSet resultSet;
    private boolean active = true;

    Row(ResultSet resultSet) { this.resultSet = resultSet; }

    public String getString(String column) { return read(() -> resultSet.getString(column), column); }
    public String getString(int column) { return read(() -> resultSet.getString(column), column); }
    public int getInt(String column) { return read(() -> resultSet.getInt(column), column); }
    public int getInt(int column) { return read(() -> resultSet.getInt(column), column); }
    public Integer getInteger(String column) { return read(() -> { int value = resultSet.getInt(column); return resultSet.wasNull() ? null : value; }, column); }
    public long getLong(String column) { return read(() -> resultSet.getLong(column), column); }
    public Long getLongObject(String column) { return read(() -> { long value = resultSet.getLong(column); return resultSet.wasNull() ? null : value; }, column); }
    public boolean getBoolean(String column) { return read(() -> resultSet.getBoolean(column), column); }
    public Boolean getBooleanObject(String column) { return read(() -> { boolean value = resultSet.getBoolean(column); return resultSet.wasNull() ? null : value; }, column); }
    public double getDouble(String column) { return read(() -> resultSet.getDouble(column), column); }
    public Double getDoubleObject(String column) { return read(() -> { double value = resultSet.getDouble(column); return resultSet.wasNull() ? null : value; }, column); }
    public BigDecimal getBigDecimal(String column) { return read(() -> resultSet.getBigDecimal(column), column); }
    public byte[] getBytes(String column) {
        byte[] bytes = read(() -> resultSet.getBytes(column), column);
        return bytes == null ? null : bytes.clone();
    }
    public UUID getUuid(String column) {
        String value = getString(column);
        return value == null ? null : UUID.fromString(value);
    }
    public Instant getInstant(String column) {
        String value = getString(column);
        return value == null ? null : Instant.parse(value);
    }
    public boolean wasNull() { return read(resultSet::wasNull, "<last column>"); }
    public Object getObject(String column) { return read(() -> resultSet.getObject(column), column); }

    private <T> T read(SqlSupplier<T> supplier, Object column) {
        if (!active) throw new IllegalStateException("Row is only available during its mapper callback");
        try { return supplier.get(); }
        catch (SQLException failure) { throw new StorageException("Could not read SQL result column " + column, failure); }
    }

    void invalidate() { active = false; }
    @FunctionalInterface private interface SqlSupplier<T> { T get() throws SQLException; }
}
