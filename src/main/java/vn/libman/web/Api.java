package vn.libman.web;

import static vn.libman.model.Input.*;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import vn.libman.dao.*;
import vn.libman.model.*;

public final class Api extends HttpServlet {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static class Denied extends RuntimeException {

    final int status;

    Denied(int status, String s) {
      super(s);
      this.status = status;
    }
  }

  @Override
  public void init() throws ServletException {
    try {
      Database.initialize();
    } catch (Exception e) {
      throw new ServletException("Không thể mở cơ sở dữ liệu LIBMAN.", e);
    }
  }

  private static String param(HttpServletRequest r, String key) {
    return Objects.toString(r.getParameter(key), "").strip();
  }

  private static int id(HttpServletRequest r) {
    try {
      return Integer.parseInt(param(r, "id"));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Mã chọn không hợp lệ.");
    }
  }

  private static void allow(User u, String... roles) {
    if (!List.of(roles).contains(u.role())) throw new Denied(
      403,
      "Tài khoản không có quyền thực hiện thao tác này."
    );
  }

  private static Object session(HttpSession s, User u) {
    return Map.of(
      "user",
      u,
      "csrf",
      s.getAttribute("csrf"),
      "demo",
      Database.demo()
    );
  }

  private static JsonNode body(HttpServletRequest r) throws IOException {
    if (
      !Objects.toString(r.getContentType(), "").startsWith("application/json")
    ) throw new Denied(415, "Yêu cầu cần dùng JSON.");
    byte[] bytes = r.getInputStream().readNBytes(128001);
    require(bytes.length <= 128000, "Dữ liệu gửi quá lớn.");
    try {
      JsonNode data = JSON.readTree(bytes);
      require(data != null && data.isObject(), "Nội dung không hợp lệ.");
      return data;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("JSON không hợp lệ.");
    }
  }

  @Override
  protected void service(
    HttpServletRequest r,
    HttpServletResponse res
  ) throws IOException {
    res.setContentType("application/json;charset=UTF-8");
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Content-Type-Options", "nosniff");
    Object output;
    int status = 200;
    try {
      output = handle(r);
    } catch (Denied e) {
      status = e.status;
      output = Map.of("error", e.getMessage());
    } catch (IllegalArgumentException e) {
      status = 400;
      output = Map.of("error", e.getMessage());
    } catch (SQLException e) {
      String state = Objects.toString(e.getSQLState(), "");
      status = state.startsWith("23")
        ? 409
        : state.startsWith("40")
          ? 409
          : 500;
      if (status == 500) getServletContext().log(
        "Database operation failed; SQL state " + state
      );
      output = Map.of(
        "error",
        status == 409
          ? "Bản ghi trùng, đang được sử dụng hoặc vừa thay đổi. Hãy kiểm tra rồi thử lại."
          : "Hiện chưa thể truy cập dữ liệu."
      );
    }
    res.setStatus(status);
    JSON.writeValue(res.getOutputStream(), output);
  }

  private Object handle(HttpServletRequest r) throws IOException, SQLException {
    boolean post = "POST".equals(r.getMethod());
    if (!post && !"GET".equals(r.getMethod())) throw new Denied(
      405,
      "Phương thức không được hỗ trợ."
    );
    String path = Objects.toString(r.getPathInfo(), "");
    HttpSession s = r.getSession(false);
    User user = s == null ? null : (User) s.getAttribute("user");
    if (!post && path.equals("/session")) return user == null
      ? Map.of("demo", Database.demo())
      : session(s, user);
    JsonNode p = post ? body(r) : null;
    try (Connection db = Database.open()) {
      try {
        UserDAO users = new UserDAO(db);
        CatalogDAO catalog = new CatalogDAO(db);
        TransactionsDAO tx = new TransactionsDAO(db);
        if (post && path.equals("/login")) {
          User found = users.login(
            text(p, "username", 100),
            text(p, "password", 128)
          );
          if (found == null) throw new Denied(
            401,
            "Tên đăng nhập hoặc mật khẩu không đúng."
          );
          if (s != null) s.invalidate();
          s = r.getSession(true);
          s.setMaxInactiveInterval(1800);
          s.setAttribute("user", found);
          s.setAttribute("csrf", UUID.randomUUID().toString());
          return session(s, found);
        }
        if (
          user == null ||
          users
            .rows(
              "SELECT id FROM tblUser WHERE id=? AND active=TRUE",
              user.id()
            )
            .isEmpty()
        ) throw new Denied(401, "Vui lòng đăng nhập lại.");
        if (
          post &&
          !Objects.equals(s.getAttribute("csrf"), r.getHeader("X-CSRF-Token"))
        ) throw new Denied(403, "Phiên thao tác không hợp lệ.");
        Object result = Map.of("ok", true);
        if (post && path.equals("/logout")) {
          s.invalidate();
          return result;
        }
        if (!post) {
          result = switch (path) {
            case "/documents" -> {
              allow(user, "Member", "Librarian");
              String q = param(r, "q");
              if (r.getParameter("q") != null) require(
                !q.isBlank(),
                "Vui lòng nhập tên tài liệu."
              );
              yield catalog.documents(
                q,
                user.role().equals("Librarian") && param(r, "all").equals("1")
              );
            }
            case "/document" -> {
              allow(user, "Member", "Librarian");
              yield catalog.detail(id(r));
            }
            case "/me" -> {
              allow(user, "Member");
              yield users.member(user.id());
            }
            case "/members" -> {
              allow(user, "Librarian");
              yield users.members();
            }
            case "/suppliers" -> {
              allow(user, "Librarian");
              yield catalog.suppliers(param(r, "q"));
            }
            case "/imports" -> {
              allow(user, "Librarian");
              yield tx.imports();
            }
            case "/invoice" -> {
              allow(user, "Librarian");
              yield tx.invoice(id(r));
            }
            case "/loans" -> {
              allow(user, "Librarian");
              yield tx.loans();
            }
            case "/loan" -> {
              allow(user, "Librarian");
              yield tx.loanLines(id(r));
            }
            case "/stats" -> {
              allow(user, "Manager");
              yield tx.stats(
                param(r, "type"),
                param(r, "from"),
                param(r, "to")
              );
            }
            case "/stats-detail" -> {
              allow(user, "Manager");
              yield tx.reportDetail(id(r), param(r, "from"), param(r, "to"));
            }
            default -> throw new Denied(404, "Không tìm thấy chức năng.");
          };
        } else if (path.equals("/register-card")) {
          allow(user, "Member");
          users.register(user.id(), p);
        } else {
          allow(user, "Librarian");
          switch (path) {
            case "/save-document" -> catalog.saveDocument(p);
            case "/save-supplier" -> catalog.saveSupplier(p);
            case "/remove-document" -> catalog.remove(true, positive(p, "id"));
            case "/remove-supplier" -> catalog.remove(false, positive(p, "id"));
            case "/save-member" -> users.saveMember(p);
            case "/remove-member" -> users.remove(positive(p, "id"));
            case "/card" -> users.card(positive(p, "id"), p);
            case "/import" -> result = Map.of(
              "id",
              tx.createImport(user.id(), p)
            );
            case "/payment" -> tx.pay(positive(p, "id"));
            case "/borrow" -> result = Map.of("id", tx.borrow(user.id(), p));
            case "/return" -> result = Map.of("id", tx.receive(user.id(), p));
            default -> throw new Denied(404, "Không tìm thấy chức năng.");
          }
        }
        db.commit();
        return result;
      } catch (SQLException | RuntimeException ex) {
        db.rollback();
        throw ex;
      }
    }
  }
}
