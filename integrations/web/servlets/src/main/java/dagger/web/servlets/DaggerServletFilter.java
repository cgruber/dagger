package dagger.web.servlets;

import com.google.common.base.Throwables;
import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;

public class DaggerServletFilter implements Filter {

  private static final String DAGGER_GRAPH_KEY = "dagger.graph";

  private static Logger logger = Logger.getLogger(DaggerServletFilter.class.getName());

  private final List<Object> singletonModules = new ArrayList<Object>();
  private final List<Object> sessionModules = new ArrayList<Object>();
  private final List<Object> requestModules = new ArrayList<Object>();

  @Override
  public synchronized void init(FilterConfig filterConfig) throws ServletException {
    logger.log(FINE, "Dagger Servlet Filter - Initialize");
    ServletContext context = filterConfig.getServletContext();
    ObjectGraph applicationGraph = ObjectGraph.create(singletonModules.toArray());
    context.setAttribute(DAGGER_GRAPH_KEY, applicationGraph);
    System.err.println();
  }

  @Override
  public void doFilter(
      final ServletRequest servletRequest,
      final ServletResponse servletResponse,
      final FilterChain filterChain)
      throws IOException, ServletException {
    logger.log(FINE, "Dagger Servlet Filter - doFilter() start.");
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    ServletContext context = request.getServletContext();

    logger.log(FINER, "Dagger Servlet Filter - doFilter() get application graph.");
    ObjectGraph applicationGraph = (ObjectGraph)context.getAttribute(DAGGER_GRAPH_KEY);
    ObjectGraph requestGraphParent = applicationGraph;

    if (!sessionModules.isEmpty()) {
      HttpSession session = request.getSession(true);
      ObjectGraph sessionGraph = (ObjectGraph)session.getAttribute(DAGGER_GRAPH_KEY);
      if (sessionGraph == null) {
        synchronized (session) {
          sessionGraph = (ObjectGraph)session.getAttribute(DAGGER_GRAPH_KEY);
          if (sessionGraph == null) {
            sessionGraph = applicationGraph.plus(sessionModules.toArray());
            session.setAttribute(DAGGER_GRAPH_KEY, sessionGraph);
          }
        }
      }
      requestGraphParent = sessionGraph;
    }

    HttpServletResponse response = (HttpServletResponse) servletResponse;
    ObjectGraph requestGraph = requestGraphParent.plus(requestModules);
    request.setAttribute(DAGGER_GRAPH_KEY, requestGraph);

    try {
      PrintWriter writer = response.getWriter();
      writer.append(requestGraphParent.get(HttpServletRequest.class).getRequestURL());
      response.flushBuffer();
      filterChain.doFilter(request, response);
      // filterPipeline.dispatch(servletRequest, servletResponse, filterChain);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      Throwables.propagate(e);
    } finally {
      logger.log(FINE, "Dagger Servlet Filter - doFilter() end.");
    }
  }

  @Override
  public void destroy() {
    logger.log(FINE, "Dagger Servlet Filter - Initialize");
  }

  @Module(library = true)
  static class SessionModule {
    private final HttpSession session;
    private SessionModule(HttpSession session) {
      this.session = session;
    }
    @Provides @Singleton /* PerSession */ HttpSession provideSession() {
      return session;
    }
  }
}
