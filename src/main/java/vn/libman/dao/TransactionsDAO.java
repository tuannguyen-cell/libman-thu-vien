package vn.libman.dao;

import static vn.libman.model.Input.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class TransactionsDAO extends Dao {

  public TransactionsDAO(Connection db) {
    super(db);
  }

  private record Line(int id, int qty, BigDecimal price) {}

  private Collection<Line> lines(JsonNode p, boolean priced) {
    JsonNode items = p.path("lines");
    require(
      items.isArray() && !items.isEmpty() && items.size() <= 500,
      "Phiếu cần từ 1 đến 500 dòng."
    );
    var sorted = new TreeMap<Integer, Line>();
    for (var item : items) {
      int id = positive(item, "id"),
        qty = positive(item, "quantity");
      BigDecimal cost = priced ? price(item) : BigDecimal.ZERO;
      var old = sorted.get(id);
      if (old != null) {
        require(
          old.price.compareTo(cost) == 0,
          "Một mã tài liệu có hai đơn giá khác nhau."
        );
        require((long) old.qty + qty <= Integer.MAX_VALUE, "Số lượng quá lớn.");
        qty += old.qty;
      }
      sorted.put(id, new Line(id, qty, cost));
    }
    return sorted.values();
  }

  public int createImport(int staff, JsonNode p) throws SQLException {
    String token = text(p, "token", 100);
    require(
      token.matches("[a-zA-Z0-9_-]{16,100}"),
      "Mã phiên lập phiếu không hợp lệ."
    );
    one("SELECT id FROM tblLibrarian WHERE id=? FOR UPDATE", staff);
    var old = rows(
      "SELECT id,librarianId FROM tblImportOrder WHERE submissionToken=?",
      token
    );
    if (!old.isEmpty()) {
      require(
        number(old.get(0), "librarianId") == staff,
        "Mã phiên đã được sử dụng."
      );
      return number(old.get(0), "id");
    }
    int supplier = positive(p, "supplierId");
    one(
      "SELECT id FROM tblSupplier WHERE id=? AND active=TRUE FOR UPDATE",
      supplier
    );
    var details = lines(p, true);
    int id = insert(
      "INSERT INTO tblImportOrder(importCode,importDate,supplierId,librarianId,paymentStatus,submissionToken) VALUES(?,?,?,?,'UNPAID',?)",
      code("PN"),
      now(),
      supplier,
      staff,
      token
    );
    for (var l : details) {
      var doc = one(
        "SELECT totalQuantity FROM tblDocument WHERE id=? AND active=TRUE FOR UPDATE",
        l.id
      );
      require(
        (long) number(doc, "totalQuantity") + l.qty <= Integer.MAX_VALUE,
        "Tổng kho vượt giới hạn."
      );
      run(
        "INSERT INTO tblImportOrderDetail(importOrderId,documentId,quantity,importPrice) VALUES(?,?,?,?)",
        id,
        l.id,
        l.qty,
        l.price
      );
      run(
        "UPDATE tblDocument SET totalQuantity=totalQuantity+? WHERE id=?",
        l.qty,
        l.id
      );
    }
    return id;
  }

  private static final String IMPORT =
    "SELECT h.id,h.importCode,h.importDate,h.paymentStatus,h.paidAt,s.supplierName,s.supplierCode,u.fullName AS librarianName,(SELECT SUM(i.quantity*i.importPrice) FROM tblImportOrderDetail i WHERE i.importOrderId=h.id) AS totalAmount FROM tblImportOrder h JOIN tblSupplier s ON s.id=h.supplierId JOIN tblUser u ON u.id=h.librarianId";

  public List<Map<String, Object>> imports() throws SQLException {
    return rows(IMPORT + " ORDER BY h.id DESC");
  }

  public Map<String, Object> invoice(int id) throws SQLException {
    var head = one(IMPORT + " WHERE h.id=?", id);
    head.put(
      "lines",
      rows(
        "SELECT d.documentCode,d.title,i.quantity,i.importPrice,i.quantity*i.importPrice AS amount FROM tblImportOrderDetail i JOIN tblDocument d ON d.id=i.documentId WHERE i.importOrderId=? ORDER BY i.id",
        id
      )
    );
    return head;
  }

  public void pay(int id) throws SQLException {
    one("SELECT id FROM tblImportOrder WHERE id=? FOR UPDATE", id);
    run(
      "UPDATE tblImportOrder SET paymentStatus='PAID',paidAt=? WHERE id=? AND paymentStatus='UNPAID'",
      now(),
      id
    );
  }

  public int borrow(int staff, JsonNode p) throws SQLException {
    int member = positive(p, "memberId");
    LocalDate due = date(text(p, "dueDate", 10));
    require(!due.isBefore(today()), "Hạn trả không thể trước ngày mượn.");
    one(
      "SELECT m.id FROM tblMember m JOIN tblUser u ON u.id=m.id WHERE m.id=? AND u.active=TRUE AND m.cardStatus='ACTIVE' AND m.cardExpiryDate>=? FOR UPDATE",
      member,
      today()
    );
    int id = insert(
      "INSERT INTO tblBorrowOrder(orderCode,borrowDate,dueDate,memberId,librarianId) VALUES(?,?,?,?,?)",
      code("PM"),
      now(),
      due,
      member,
      staff
    );
    for (var l : lines(p, false)) {
      one(
        "SELECT id FROM tblDocument WHERE id=? AND active=TRUE FOR UPDATE",
        l.id
      );
      var doc = new CatalogDAO(db).detail(l.id);
      require(
        number(doc, "availableQuantity") >= l.qty,
        "Không đủ số bản có sẵn của " + doc.get("title") + "."
      );
      run(
        "INSERT INTO tblBorrowOrderDetail(borrowOrderId,documentId,quantity) VALUES(?,?,?)",
        id,
        l.id,
        l.qty
      );
    }
    return id;
  }

  private static final String REMAIN =
    "b.quantity-COALESCE((SELECT SUM(r.returnedQuantity) FROM tblReturnOrderDetail r WHERE r.borrowOrderDetailId=b.id),0)";

  public List<Map<String, Object>> loans() throws SQLException {
    return rows(
      "SELECT h.id,h.orderCode,h.borrowDate,h.dueDate,u.fullName,m.memberCode,(SELECT SUM(" +
        REMAIN +
        ") FROM tblBorrowOrderDetail b WHERE b.borrowOrderId=h.id) AS remaining FROM tblBorrowOrder h JOIN tblMember m ON m.id=h.memberId JOIN tblUser u ON u.id=m.id ORDER BY h.id DESC"
    );
  }

  public List<Map<String, Object>> loanLines(int id) throws SQLException {
    return rows(
      "SELECT b.id,d.documentCode,d.title,b.quantity," +
        REMAIN +
        " AS remaining FROM tblBorrowOrderDetail b JOIN tblDocument d ON d.id=b.documentId WHERE b.borrowOrderId=? ORDER BY b.id",
      id
    );
  }

  public int receive(int staff, JsonNode p) throws SQLException {
    int loan = positive(p, "loanId");
    one("SELECT id FROM tblBorrowOrder WHERE id=? FOR UPDATE", loan);
    int id = insert(
      "INSERT INTO tblReturnOrder(returnCode,returnDate,borrowOrderId,librarianId) VALUES(?,?,?,?)",
      code("PT"),
      now(),
      loan,
      staff
    );
    for (var l : lines(p, false)) {
      var line = one(
        "SELECT " +
          REMAIN +
          " AS remaining FROM tblBorrowOrderDetail b WHERE b.id=? AND b.borrowOrderId=?",
        l.id,
        loan
      );
      require(
        l.qty <= number(line, "remaining"),
        "Số lượng trả vượt quá số còn mượn."
      );
      run(
        "INSERT INTO tblReturnOrderDetail(returnOrderId,borrowOrderDetailId,returnedQuantity) VALUES(?,?,?)",
        id,
        l.id,
        l.qty
      );
    }
    return id;
  }

  public List<Map<String, Object>> stats(
    String type,
    String from,
    String to
  ) throws SQLException {
    LocalDate start = date(from),
      end = date(to);
    require(!end.isBefore(start), "Ngày kết thúc phải từ ngày bắt đầu trở đi.");
    String sql = switch (type) {
      case "documents" -> "SELECT d.id,d.documentCode AS code,d.title AS label,COUNT(*) AS valueCount FROM tblBorrowOrder h JOIN tblBorrowOrderDetail l ON l.borrowOrderId=h.id JOIN tblDocument d ON d.id=l.documentId WHERE h.borrowDate>=? AND h.borrowDate<? GROUP BY d.id,d.documentCode,d.title";
      case "members" -> "SELECT m.id,m.memberCode AS code,u.fullName AS label,COUNT(*) AS valueCount FROM tblBorrowOrder h JOIN tblMember m ON m.id=h.memberId JOIN tblUser u ON u.id=m.id WHERE h.borrowDate>=? AND h.borrowDate<? GROUP BY m.id,m.memberCode,u.fullName";
      case "suppliers" -> "SELECT s.id,s.supplierCode AS code,s.supplierName AS label,SUM(l.quantity) AS valueCount FROM tblImportOrder h JOIN tblImportOrderDetail l ON l.importOrderId=h.id JOIN tblSupplier s ON s.id=h.supplierId WHERE h.importDate>=? AND h.importDate<? GROUP BY s.id,s.supplierCode,s.supplierName";
      default -> throw new IllegalArgumentException(
        "Loại thống kê chưa hợp lệ."
      );
    };
    return rows(
      sql + " ORDER BY valueCount DESC,code ASC",
      start,
      end.plusDays(1)
    );
  }

  public List<Map<String, Object>> reportDetail(
    int id,
    String from,
    String to
  ) throws SQLException {
    LocalDate start = date(from),
      end = date(to);
    require(!end.isBefore(start), "Khoảng ngày không hợp lệ.");
    return rows(
      "SELECT h.orderCode,h.borrowDate,u.fullName,l.quantity FROM tblBorrowOrderDetail l JOIN tblBorrowOrder h ON h.id=l.borrowOrderId JOIN tblUser u ON u.id=h.memberId WHERE l.documentId=? AND h.borrowDate>=? AND h.borrowDate<? ORDER BY h.borrowDate,h.id",
      id,
      start,
      end.plusDays(1)
    );
  }
}
