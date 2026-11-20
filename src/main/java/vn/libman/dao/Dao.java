package vn.libman.dao;

import static vn.libman.model.Input.require;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class Dao {

  protected final Connection db;

  public Dao(Connection db) {
    this.db = db;
  }

  private PreparedStatement statement(
    String sql,
    boolean keys,
    Object... args
  ) throws SQLException {
    PreparedStatement s = db.prepareStatement(
      sql,
      keys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS
    );
    for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
    return s;
  }

  public List<Map<String, Object>> rows(
    String sql,
    Object... args
  ) throws SQLException {
    try (var st = statement(sql, false, args); var rs = st.executeQuery()) {
      var result = new ArrayList<Map<String, Object>>();
      var md = rs.getMetaData();
      while (rs.next()) {
        var row = new LinkedHashMap<String, Object>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
          Object v = rs.getObject(i);
          if (
            v instanceof java.sql.Date ||
            v instanceof Timestamp ||
            v instanceof java.time.temporal.TemporalAccessor
          ) v = v.toString();
          row.put(md.getColumnLabel(i), v);
        }
        result.add(row);
      }
      return result;
    }
  }

  public Map<String, Object> one(
    String sql,
    Object... args
  ) throws SQLException {
    var list = rows(sql, args);
    require(!list.isEmpty(), "Không tìm thấy bản ghi phù hợp.");
    return list.get(0);
  }

  public int insert(String sql, Object... args) throws SQLException {
    try (var st = statement(sql, true, args)) {
      st.executeUpdate();
      try (var rs = st.getGeneratedKeys()) {
        rs.next();
        return rs.getInt(1);
      }
    }
  }

  public int run(String sql, Object... args) throws SQLException {
    try (var st = statement(sql, false, args)) {
      return st.executeUpdate();
    }
  }

  public static int number(Map<String, Object> row, String field) {
    return ((Number) row.get(field)).intValue();
  }

  public static LocalDate today() {
    return LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
  }

  public static Timestamp now() {
    return Timestamp.valueOf(
      java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
    );
  }

  public static String like(String s) {
    return (
      "%" + s.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
    );
  }

  public static String code(String prefix) {
    return (
      prefix +
      "-" +
      UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT)
    );
  }
}
