package vn.libman.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class Input {

  private Input() {}

  public static void require(boolean ok, String message) {
    if (!ok) throw new IllegalArgumentException(message);
  }

  public static String optional(JsonNode data, String name, int limit) {
    String s = data.path(name).asText("").strip();
    require(
      s.length() <= limit,
      "Trường " + name + " vượt quá độ dài cho phép."
    );
    return s.isEmpty() ? null : s;
  }

  public static String text(JsonNode data, String name, int limit) {
    String s = optional(data, name, limit);
    require(s != null, "Vui lòng nhập đủ các trường bắt buộc.");
    return s;
  }

  public static int positive(JsonNode data, String name) {
    try {
      int v = Integer.parseInt(data.path(name).asText());
      require(v > 0, "Số lượng hoặc mã chọn phải lớn hơn 0.");
      return v;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Cần nhập số nguyên dương hợp lệ.");
    }
  }

  public static LocalDate date(String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException | NullPointerException e) {
      throw new IllegalArgumentException("Ngày không hợp lệ.");
    }
  }

  public static BigDecimal price(JsonNode line) {
    try {
      BigDecimal p = new BigDecimal(line.path("importPrice").asText());
      require(
        p.signum() > 0 &&
          p.scale() <= 2 &&
          p.compareTo(new BigDecimal("9999999999999.99")) <= 0,
        "Đơn giá phải dương, tối đa hai chữ số thập phân."
      );
      return p;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Đơn giá không hợp lệ.");
    }
  }
}
