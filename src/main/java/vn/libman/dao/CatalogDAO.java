package vn.libman.dao;

import static vn.libman.model.Input.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.util.*;

public class CatalogDAO extends Dao {

  public CatalogDAO(Connection db) {
    super(db);
  }

  public static final String AVAILABLE =
    "d.totalQuantity-COALESCE((SELECT SUM(b.quantity) FROM tblBorrowOrderDetail b WHERE b.documentId=d.id),0)+COALESCE((SELECT SUM(r.returnedQuantity) FROM tblReturnOrderDetail r JOIN tblBorrowOrderDetail b ON b.id=r.borrowOrderDetailId WHERE b.documentId=d.id),0)";
  private static final String DOC =
    "SELECT d.*," + AVAILABLE + " AS availableQuantity FROM tblDocument d";

  public List<Map<String, Object>> documents(
    String keyword,
    boolean all
  ) throws SQLException {
    return rows(
      DOC +
        " WHERE " +
        (all ? "1=1" : "d.active=TRUE") +
        " AND LOWER(d.title) LIKE LOWER(?) ESCAPE '!' ORDER BY d.documentCode",
      like(keyword)
    );
  }

  public Map<String, Object> detail(int id) throws SQLException {
    return one(DOC + " WHERE d.active=TRUE AND d.id=?", id);
  }

  public List<Map<String, Object>> suppliers(String q) throws SQLException {
    return rows(
      "SELECT * FROM tblSupplier WHERE LOWER(supplierName) LIKE LOWER(?) ESCAPE '!' ORDER BY supplierName",
      like(q)
    );
  }

  public void saveDocument(JsonNode p) throws SQLException {
    String code = text(p, "documentCode", 100),
      title = text(p, "title", 255),
      cat = text(p, "category", 100);
    String year = optional(p, "publishYear", 4);
    Integer y = null;
    if (year != null) {
      require(year.matches("[0-9]{4}"), "Năm xuất bản cần 4 chữ số.");
      y = Integer.valueOf(year);
    }
    String author = optional(p, "author", 100),
      publisher = optional(p, "publisher", 100),
      shelf = optional(p, "shelfLocation", 100);
    int id = p.path("id").asInt();
    if (id == 0) run(
      "INSERT INTO tblDocument(documentCode,title,category,author,publisher,shelfLocation,publishYear,totalQuantity) VALUES(?,?,?,?,?,?,?,0)",
      code,
      title,
      cat,
      author,
      publisher,
      shelf,
      y
    );
    else {
      one("SELECT id FROM tblDocument WHERE id=? FOR UPDATE", id);
      run(
        "UPDATE tblDocument SET documentCode=?,title=?,category=?,author=?,publisher=?,shelfLocation=?,publishYear=? WHERE id=?",
        code,
        title,
        cat,
        author,
        publisher,
        shelf,
        y,
        id
      );
    }
  }

  public void saveSupplier(JsonNode p) throws SQLException {
    String code = text(p, "supplierCode", 100),
      name = text(p, "supplierName", 100),
      addr = optional(p, "address", 255),
      phone = optional(p, "phone", 100),
      email = optional(p, "email", 100);
    int id = p.path("id").asInt();
    if (id == 0) run(
      "INSERT INTO tblSupplier(supplierCode,supplierName,address,phone,email) VALUES(?,?,?,?,?)",
      code,
      name,
      addr,
      phone,
      email
    );
    else {
      one("SELECT id FROM tblSupplier WHERE id=? FOR UPDATE", id);
      run(
        "UPDATE tblSupplier SET supplierCode=?,supplierName=?,address=?,phone=?,email=? WHERE id=?",
        code,
        name,
        addr,
        phone,
        email,
        id
      );
    }
  }

  public void remove(boolean doc, int id) throws SQLException {
    String table = doc ? "tblDocument" : "tblSupplier";
    one("SELECT id FROM " + table + " WHERE id=? FOR UPDATE", id);
    boolean used = doc
      ? !rows(
          "SELECT id FROM tblBorrowOrderDetail WHERE documentId=? UNION ALL SELECT id FROM tblImportOrderDetail WHERE documentId=?",
          id,
          id
        ).isEmpty()
      : !rows("SELECT id FROM tblImportOrder WHERE supplierId=?", id).isEmpty();
    run(
      used
        ? "UPDATE " + table + " SET active=FALSE WHERE id=?"
        : "DELETE FROM " + table + " WHERE id=?",
      id
    );
  }
}
