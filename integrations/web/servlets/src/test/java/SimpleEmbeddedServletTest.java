import dagger.web.servlets.DaggerServletFilter;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class SimpleEmbeddedServletTest {
  private Server server;

  @Before
  public void setUp() throws Exception {
    server = new Server(8080);
    ServletContextHandler servletContextHandler = new ServletContextHandler();
    servletContextHandler.setContextPath("/");
    EnumSet<DispatcherType> dispatches = EnumSet.allOf(DispatcherType.class);
    servletContextHandler.addFilter(DaggerServletFilter.class, "/*", dispatches);
    servletContextHandler.addServlet(DefaultServlet.class, "/*");
    server.setHandler(servletContextHandler);
    Logger.getLogger(DaggerServletFilter.class.getName()).setLevel(Level.FINER);
    server.start();
    Thread.sleep(5000);
  }

  @Test
  public void testPush() throws Exception {
    Thread.sleep(5000);
  }

  @After
  public void tearDown() throws Exception {
    server.stop();
  }

}
