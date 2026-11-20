package vn.libman.dao;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import vn.libman.model.Passwords;

public final class Database {

  private Database() {}

  public static boolean demo() {
    return Boolean.getBoolean("libman.demo");
  }

  public static Connection open() throws SQLException {
    String url = System.getProperty("libman.test.url");
    if (url == null) url = demo()
      ? "jdbc:h2:file:./.runtime/libman;MODE=MySQL;DATABASE_TO_UPPER=FALSE"
      : System.getenv("LIBMAN_DB_URL");
    if (url == null || url.isBlank()) throw new SQLException(
      "Configure LIBMAN_DB_URL first."
    );
    boolean h2 = url.startsWith("jdbc:h2:");
    try {
      Class.forName(h2 ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
    } catch (ClassNotFoundException e) {
      throw new SQLException(e);
    }
    var c = DriverManager.getConnection(
      url,
      h2 ? "sa" : System.getenv("LIBMAN_DB_USER"),
      h2 ? "" : System.getenv("LIBMAN_DB_PASSWORD")
    );
    c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    c.setAutoCommit(false);
    return c;
  }

  public static void initialize() throws Exception {
    try (var c = open()) {
      if (
        !demo() && !"true".equalsIgnoreCase(System.getenv("LIBMAN_BOOTSTRAP"))
      ) return;
      try (
        var in = Database.class.getResourceAsStream("/schema.sql");
        var st = c.createStatement()
      ) {
        for (String sql : new String(
          in.readAllBytes(),
          StandardCharsets.UTF_8
        ).split(";"))
          if (!sql.isBlank()) st.execute(sql);
      }
      Dao d = new Dao(c);
      if (!d.rows("SELECT id FROM tblUser").isEmpty()) {
        c.commit();
        return;
      }
      var usedPasswords = new java.util.HashSet<String>();
      for (String[] a : new String[][] {
        { "bandoc", "Nguyễn An", "Member", "BD001" },
        { "thuthu", "Trần Minh Anh", "Librarian", "NV001" },
        { "quanly", "Lê Minh", "Manager", "QL001" },
      }) {
        String pass = demo()
          ? "ThuVien@2026"
          : System.getenv(
              "LIBMAN_" + a[2].toUpperCase(java.util.Locale.ROOT) + "_PASSWORD"
            );
        if (
          pass == null ||
          pass.length() < 10 ||
          (!demo() && !usedPasswords.add(pass))
        ) throw new IllegalArgumentException(
          "Configure three distinct initial role passwords (10+ characters)."
        );
        int id = d.insert(
          "INSERT INTO tblUser(username,passwordHash,fullName,active) VALUES(?,?,?,TRUE)",
          a[0],
          Passwords.hash(pass),
          a[1]
        );
        if (a[2].equals("Member")) d.run(
          "INSERT INTO tblMember(id,cardStatus) VALUES(?,'NONE')",
          id
        );
        else d.run(
          "INSERT INTO tbl" + a[2] + "(id,employeeCode) VALUES(?,?)",
          id,
          a[3]
        );
      }
      if (demo()) {
        String[][] books = {
          { "Cơ sở dữ liệu", "Nguyễn A", "Giáo trình", "A2" },
          { "Cơ sở dữ liệu nâng cao", "Trần B", "Sách", "A2" },
          {
            "Phân tích thiết kế hệ thống thông tin",
            "Lê Minh",
            "Giáo trình",
            "B1",
          },
          { "Cấu trúc dữ liệu và giải thuật", "Lê An", "Giáo trình", "B2" },
          { "Mạng máy tính", "Trần Hà", "Sách", "C1" },
          { "Tạp chí Khoa học và Công nghệ", "Ban biên tập", "Tạp chí", "D1" },
        };
        int i = 0;
        for (var b : books) {
          i++;
          d.run(
            "INSERT INTO tblDocument(documentCode,title,author,category,shelfLocation,publisher,publishYear,totalQuantity) VALUES(?,?,?,?,?,?,?,?)",
            String.format("TL%03d", i),
            b[0],
            b[1],
            b[2],
            b[3],
            "Giáo dục",
            2024,
            i == 1 ? 5 : 10 + i
          );
        }
        d.run(
          "INSERT INTO tblSupplier(supplierCode,supplierName,address) VALUES('NCC01','NXB Giáo dục','Hà Nội')"
        );
      }
      c.commit();
    }
  }
}
