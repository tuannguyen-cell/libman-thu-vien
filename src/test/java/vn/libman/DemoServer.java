package vn.libman;

import java.io.File;
import java.nio.file.*;
import org.apache.catalina.startup.Tomcat;

public class DemoServer {

  public static void main(String[] args) throws Exception {
    boolean demo = System.getenv("LIBMAN_DB_URL") == null;
    System.setProperty("libman.demo", Boolean.toString(demo));
    Files.createDirectories(Path.of(".runtime"));
    var server = new Tomcat();
    server.setBaseDir(new File(".runtime/tomcat").getAbsolutePath());
    server.setPort(Integer.getInteger("libman.port", 8080));
    server.getConnector().setProperty("address", "127.0.0.1");
    var app = server.addWebapp(
      "",
      new File("src/main/webapp").getAbsolutePath()
    );
    app.setParentClassLoader(DemoServer.class.getClassLoader());
    server.start();
    System.out.println(
      "LIBMAN http://127.0.0.1:" + server.getConnector().getLocalPort() + "/"
    );
    server.getServer().await();
  }
}
