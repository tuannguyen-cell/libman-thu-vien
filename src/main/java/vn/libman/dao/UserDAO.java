package vn.libman.dao;

import static vn.libman.model.Input.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import vn.libman.model.*;

public class UserDAO extends Dao {

  public UserDAO(Connection db) {
    super(db);
  }

  public User login(String name, String password) throws SQLException {
    var data = rows(
      "SELECT u.id,u.username,u.fullName,u.passwordHash, CASE WHEN l.id IS NOT NULL THEN 'Librarian' WHEN g.id IS NOT NULL THEN 'Manager' ELSE 'Member' END AS roleName FROM tblUser u LEFT JOIN tblLibrarian l ON l.id=u.id LEFT JOIN tblManager g ON g.id=u.id WHERE u.username=? AND u.active=TRUE",
      name
    );
    if (
      data.isEmpty() ||
      !Passwords.matches(password, (String) data.get(0).get("passwordHash"))
    ) return null;
    var r = data.get(0);
    return new User(
      number(r, "id"),
      (String) r.get("username"),
      (String) r.get("fullName"),
      (String) r.get("roleName")
    );
  }

  private static final String MEMBER =
    "SELECT u.id,u.username,u.fullName,u.email,u.phone,u.address,u.dateOfBirth,u.active,m.memberCode,m.registrationDate,m.cardIssueDate,m.cardExpiryDate,m.cardStatus FROM tblUser u JOIN tblMember m ON u.id=m.id";

  public List<Map<String, Object>> members() throws SQLException {
    return rows(MEMBER + " ORDER BY u.fullName");
  }

  public Map<String, Object> member(int id) throws SQLException {
    return one(MEMBER + " WHERE u.id=?", id);
  }

  private void profile(int id, JsonNode p) throws SQLException {
    String email = optional(p, "email", 100);
    if (email != null) require(
      email.matches("[^@ ]+@[^@ ]+\\.[^@ ]+"),
      "Email chưa hợp lệ."
    );
    String birth = optional(p, "dateOfBirth", 10);
    if (birth != null) require(
      !date(birth).isAfter(today()),
      "Ngày sinh không hợp lệ."
    );
    run(
      "UPDATE tblUser SET fullName=?,email=?,phone=?,address=?,dateOfBirth=? WHERE id=?",
      text(p, "fullName", 100),
      email,
      optional(p, "phone", 100),
      optional(p, "address", 255),
      birth,
      id
    );
  }

  public void register(int id, JsonNode p) throws SQLException {
    var m = one("SELECT cardStatus FROM tblMember WHERE id=? FOR UPDATE", id);
    require(
      "NONE".equals(m.get("cardStatus")),
      "Thẻ đang được xử lý hoặc đã được cấp."
    );
    for (String field : List.of("phone", "email", "address", "dateOfBirth"))
      text(p, field, 255);
    profile(id, p);
    run(
      "UPDATE tblMember SET memberCode=?,registrationDate=?,cardStatus='PENDING' WHERE id=?",
      text(p, "memberCode", 100),
      today(),
      id
    );
  }

  public void saveMember(JsonNode p) throws SQLException {
    int id = p.path("id").asInt();
    if (id == 0) {
      String password = text(p, "password", 128);
      require(password.length() >= 8, "Mật khẩu cần ít nhất 8 ký tự.");
      id = insert(
        "INSERT INTO tblUser(username,fullName,passwordHash,active) VALUES(?,?,?,TRUE)",
        text(p, "username", 100),
        text(p, "fullName", 100),
        Passwords.hash(password)
      );
      run("INSERT INTO tblMember(id,cardStatus) VALUES(?,'NONE')", id);
    }
    one("SELECT id FROM tblMember WHERE id=? FOR UPDATE", id);
    profile(id, p);
  }

  public void card(int id, JsonNode p) throws SQLException {
    var m = one("SELECT cardStatus FROM tblMember WHERE id=? FOR UPDATE", id);
    String target = text(p, "status", 12);
    if (target.equals("LOCKED")) {
      require(
        "ACTIVE".equals(m.get("cardStatus")),
        "Chỉ có thể khóa thẻ đang hoạt động."
      );
      run("UPDATE tblMember SET cardStatus='LOCKED' WHERE id=?", id);
    } else {
      require(
        target.equals("ACTIVE") &&
          List.of("PENDING", "LOCKED", "ACTIVE").contains(m.get("cardStatus")),
        "Bạn đọc cần gửi đăng ký trước khi cấp thẻ."
      );
      LocalDate expiry = date(text(p, "expiry", 10));
      require(!expiry.isBefore(today()), "Hạn thẻ phải từ ngày cấp trở đi.");
      run(
        "UPDATE tblMember SET cardStatus='ACTIVE',cardIssueDate=?,cardExpiryDate=? WHERE id=?",
        today(),
        expiry,
        id
      );
    }
  }

  public void remove(int id) throws SQLException {
    one("SELECT id FROM tblMember WHERE id=? FOR UPDATE", id);
    var ls = rows(
      "SELECT b.quantity-COALESCE((SELECT SUM(r.returnedQuantity) FROM tblReturnOrderDetail r WHERE r.borrowOrderDetailId=b.id),0) AS remaining FROM tblBorrowOrderDetail b JOIN tblBorrowOrder h ON h.id=b.borrowOrderId WHERE h.memberId=?",
      id
    );
    for (var l : ls)
      require(number(l, "remaining") == 0, "Bạn đọc còn nợ tài liệu.");
    if (ls.isEmpty()) {
      run("DELETE FROM tblMember WHERE id=?", id);
      run("DELETE FROM tblUser WHERE id=?", id);
    } else run("UPDATE tblUser SET active=FALSE WHERE id=?", id);
  }
}
