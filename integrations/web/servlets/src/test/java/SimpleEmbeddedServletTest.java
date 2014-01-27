/*
 * Copyright (C) 2013 Square, Inc.
 * Copyright (C) 2013 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
