package vn.libman;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.*;
import vn.libman.dao.Dao;
import vn.libman.web.Api;

/** Exercises the public servlet contract; each client has its own cookie jar and CSRF token. */
class LibraryFlowTest {

  static final ObjectMapper JSON = new ObjectMapper();
  static Tomcat server;
  static String base;

  static class Client {

    HttpClient http = HttpClient.newBuilder()
      .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
      .build();
    String csrf = "";

    JsonNode request(
      String endpoint,
      Object payload,
      int expected
    ) throws Exception {
      var b = HttpRequest.newBuilder(URI.create(base + endpoint));
      if (payload != null) b.header("Content-Type", "application/json")
        .header("X-CSRF-Token", csrf)
        .POST(
          HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload))
        );
      var r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(expected, r.statusCode(), endpoint + " " + r.body());
      return JSON.readTree(r.body());
    }

    void login(String name) throws Exception {
      csrf = request(
        "login",
        Map.of("username", name, "password", "ThuVien@2026"),
        200
      )
        .get("csrf")
        .asText();
    }

    JsonNode get(String path) throws Exception {
      return request(path, null, 200);
    }

    JsonNode post(String path, Object data) throws Exception {
      return request(path, data, 200);
    }
  }

  @BeforeAll
  static void setup() throws Exception {
    System.setProperty("libman.demo", "true");
    System.setProperty(
      "libman.test.url",
      System.getenv().getOrDefault(
        "LIBMAN_TEST_DB_URL",
        "jdbc:h2:mem:flows;MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1"
      )
    );
    server = new Tomcat();
    server.setBaseDir(Files.createTempDirectory("libman-test-").toString());
    server.setPort(0);
    server.getConnector().setProperty("address", "127.0.0.1");
    var context = server.addContext(
      "",
      Path.of("src/main/webapp").toAbsolutePath().toString()
    );
    Tomcat.addServlet(context, "api", new Api()).setLoadOnStartup(1);
    context.addServletMappingDecoded("/api/*", "api");
    server.start();
    base = "http://127.0.0.1:" + server.getConnector().getLocalPort() + "/api/";
  }

  @AfterAll
  static void stop() throws Exception {
    if (server != null) {
      server.stop();
      server.destroy();
    }
  }

  @Test
  void workflows() throws Exception {
    var staff = new Client();
    var reader = new Client();
    var manager = new Client();
    staff.request("imports", null, 401);
    reader.request(
      "login",
      Map.of("username", "bandoc", "password", "wrong"),
      401
    );
    reader.login("bandoc");
    manager.login("quanly");
    staff.login("thuthu");
    assertEquals(
      2,
      reader
        .get(
          "documents?q=" +
            URLEncoder.encode("Cơ sở", java.nio.charset.StandardCharsets.UTF_8)
        )
        .size()
    );
    assertEquals(0, reader.get("documents?q=%25").size());
    reader.request("documents?q=%20", null, 400);
    int doc = reader.get("documents").get(0).get("id").asInt();
    int supplier = staff.get("suppliers").get(0).get("id").asInt();
    int member = reader.get("me").get("id").asInt();
    var line = Map.of("id", doc, "quantity", 10, "importPrice", "50000");
    var data = Map.of(
      "supplierId",
      supplier,
      "token",
      UUID.randomUUID().toString(),
      "lines",
      List.of(line)
    );
    manager.request("import", data, 403);
    String csrf = staff.csrf;
    staff.csrf = "";
    staff.request("import", data, 403);
    staff.csrf = csrf;
    var invalid = Map.of(
      "supplierId",
      supplier,
      "token",
      UUID.randomUUID().toString(),
      "lines",
      List.of(line, Map.of("id", 99999, "quantity", 2, "importPrice", 20))
    );
    staff.request("import", invalid, 400);
    assertEquals(0, staff.get("imports").size());
    assertEquals(
      5,
      reader
        .get("document?id=" + doc)
        .get("totalQuantity")
        .asInt()
    );
    int order = staff.post("import", data).get("id").asInt();
    assertEquals(order, staff.post("import", data).get("id").asInt());
    assertEquals(
      15,
      reader
        .get("document?id=" + doc)
        .get("totalQuantity")
        .asInt()
    );
    var invoice = staff.get("invoice?id=" + order);
    assertEquals("UNPAID", invoice.get("paymentStatus").asText());
    assertEquals(500000, invoice.get("totalAmount").asInt());
    staff.post("payment", Map.of("id", order));
    String paid = staff
      .get("invoice?id=" + order)
      .get("paidAt")
      .asText();
    staff.post("payment", Map.of("id", order));
    assertEquals(
      paid,
      staff
        .get("invoice?id=" + order)
        .get("paidAt")
        .asText()
    );
    var profile = Map.of(
      "memberCode",
      "BD001",
      "fullName",
      "Nguyễn An",
      "address",
      "Hà Nội",
      "phone",
      "0900000000",
      "email",
      "reader@example.test",
      "dateOfBirth",
      "2002-01-02"
    );
    reader.post("register-card", profile);
    assertEquals("PENDING", reader.get("me").get("cardStatus").asText());
    staff.post(
      "card",
      Map.of(
        "id",
        member,
        "status",
        "ACTIVE",
        "expiry",
        Dao.today().plusYears(1).toString()
      )
    );
    var loanData = Map.of(
      "memberId",
      member,
      "dueDate",
      Dao.today().plusDays(7).toString(),
      "lines",
      List.of(Map.of("id", doc, "quantity", 15))
    );
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> borrow = () -> {
        var b = HttpRequest.newBuilder(URI.create(base + "borrow"))
          .header("Content-Type", "application/json")
          .header("X-CSRF-Token", staff.csrf)
          .POST(
            HttpRequest.BodyPublishers.ofString(
              JSON.writeValueAsString(loanData)
            )
          )
          .build();
        return staff.http
          .send(b, HttpResponse.BodyHandlers.discarding())
          .statusCode();
      };
      var outcomes = pool.invokeAll(List.of(borrow, borrow));
      assertEquals(
        Set.of(200, 400),
        Set.of(outcomes.get(0).get(), outcomes.get(1).get())
      );
    } finally {
      pool.shutdownNow();
    }
    int loan = staff.get("loans").get(0).get("id").asInt(),
      lid = staff
        .get("loan?id=" + loan)
        .get(0)
        .get("id")
        .asInt();
    for (int qty : List.of(2, 13))
      staff.post(
        "return",
        Map.of(
          "loanId",
          loan,
          "lines",
          List.of(Map.of("id", lid, "quantity", qty))
        )
      );
    staff.request(
      "return",
      Map.of(
        "loanId",
        loan,
        "lines",
        List.of(Map.of("id", lid, "quantity", 1))
      ),
      400
    );
    assertEquals(
      15,
      reader
        .get("document?id=" + doc)
        .get("availableQuantity")
        .asInt()
    );
    for (String type : List.of("documents", "members", "suppliers")) {
      var r = manager.get(
        "stats?type=" + type + "&from=" + Dao.today() + "&to=" + Dao.today()
      );
      assertEquals(
        type.equals("suppliers") ? 10 : 1,
        r.get(0).get("valueCount").asInt()
      );
    }
    reader.post("logout", Map.of());
    reader.request("me", null, 401);
  }
}
