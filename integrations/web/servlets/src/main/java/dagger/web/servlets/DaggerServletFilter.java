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
package dagger.web.servlets;

import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;
import dagger.Module;
import dagger.ObjectGraph;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.inject.Inject;
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

  private static final Splitter COMMA_SPLITTER = Splitter.on(',').omitEmptyStrings();
  private static final String REQUEST_MODULES_KEY = "dagger.servlet.modules.request";
  private static final String SESSION_MODULES_KEY = "dagger.servlet.modules.session";
  private static final String SINGLETON_MODULES_KEY = "dagger.servlet.modules.singleton";
  private static final String DAGGER_GRAPH_KEY = "dagger.graph";
  private static final String FILTER_MODULE_CONFIGURATOR_KEY = "dagger.servlet.filter.configurator";

  private static Logger logger = Logger.getLogger(DaggerServletFilter.class.getName());

  private FilterModuleConfigurator configurator = null;
  private List<Object> singletonModules = null;
  private List<Object> sessionModules = null;
  private List<Object> requestModules = null;

  @Override
  public synchronized void init(FilterConfig filterConfig) throws ServletException {
    logger.log(FINE, "Dagger Servlet Filter - Initialize");
    ServletContext context = filterConfig.getServletContext();
    configurator = getConfigurator(context);
    singletonModules = prepareModules(SINGLETON_MODULES_KEY, context);
    sessionModules = prepareModules(SESSION_MODULES_KEY, context);
    // debug
    String param = context.getInitParameter(REQUEST_MODULES_KEY);
    context.setInitParameter(REQUEST_MODULES_KEY, (param == null ? "" : param)
        + ",dagger.web.servlets.DaggerServerFilter.FooModule");
    // debug
    requestModules = prepareModules(REQUEST_MODULES_KEY, context);
    ObjectGraph applicationGraph = ObjectGraph.create(
        mergeModules(configurator.applicationModule(context), singletonModules));
    context.setAttribute(DAGGER_GRAPH_KEY, applicationGraph);
  }

  private static List<Object> prepareModules(String key, ServletContext context)
      throws ServletException {
    String modulesParameter = context.getInitParameter(key);
    if (modulesParameter == null) {
      return ImmutableList.<Object>of();
    }
    Iterable<String> classes = COMMA_SPLITTER.split(modulesParameter);
    List<String> unloadableModuleClasses = new ArrayList<String>();
    Builder<Object> builder = ImmutableList.builder();
    for (String moduleClass : classes) {
      try {
        builder.add(Class.forName(moduleClass));
        //builder.add(context.getClassLoader().loadClass(moduleClass));
      } catch (AccessControlException e) {
        unloadableModuleClasses.add(moduleClass);
      } catch (ClassNotFoundException e) {
        unloadableModuleClasses.add(moduleClass);
      }
    }
    if (unloadableModuleClasses.isEmpty()) {
      return builder.build();
    }
    throw new ServletException(String.format(
        "Classes defined in servlet paramter %s could not be found: %s",
        key, unloadableModuleClasses));
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
            sessionGraph = applicationGraph.plus(
                mergeModules(configurator.sessionModule(session), sessionModules));
            session.setAttribute(DAGGER_GRAPH_KEY, sessionGraph);
          }
        }
      }
      requestGraphParent = sessionGraph;
    }

    HttpServletResponse response = (HttpServletResponse) servletResponse;
    ObjectGraph requestGraph = requestGraphParent.plus(
        mergeModules(configurator.requestModule(request, response), requestModules));
    request.setAttribute(DAGGER_GRAPH_KEY, requestGraph);

    try {
      PrintWriter writer = response.getWriter();
      writer.append(requestGraphParent.get(Foo.class).request.getRequestURL());
      response.flushBuffer();
      filterChain.doFilter(request, response);
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

  private static Object[] mergeModules(Object mainModule, List<Object> extraModules) {
    Iterable<Object> modules = Iterables.concat(extraModules, ImmutableList.of(mainModule));
    return Iterables.toArray(modules, Object.class);
  }

  private static FilterModuleConfigurator getConfigurator(ServletContext context)
      throws ServletException{
    String className = context.getInitParameter(FILTER_MODULE_CONFIGURATOR_KEY);
    if (className == null || className.trim().equals("")) {
      return new DefaultFilterModuleConfigurator();
    }
    Class<?> moduleConfiguratorClass;
    try {
      moduleConfiguratorClass = context.getClassLoader().loadClass(className);
    } catch (ClassNotFoundException e) {
      throw new ServletException(String.format(
          "Class %s defined in servlet context parameter %s could not be found.",
          className, FILTER_MODULE_CONFIGURATOR_KEY), e);
    }
    if (!FilterModuleConfigurator.class.isAssignableFrom(moduleConfiguratorClass)) {
      throw new ServletException(String.format(
          "Class %s defined in servlet context parameter %s is not an implementation of %s",
          className, FILTER_MODULE_CONFIGURATOR_KEY, FilterModuleConfigurator.class.getName()));
    }
    try {
      return (FilterModuleConfigurator) moduleConfiguratorClass.newInstance();
    } catch (InstantiationException e) {
      throw new ServletException(String.format(
          "Class %s defined in servlet context parameter %s could not be instantiated.",
          className, FILTER_MODULE_CONFIGURATOR_KEY), e);
    } catch (IllegalAccessException e) {
      throw new ServletException(String.format(
          "Class %s defined in servlet context parameter %s could not be accessed.",
          className, FILTER_MODULE_CONFIGURATOR_KEY), e);
    }
  }

  /**
   * Provides instances of Objects annotated by the {@link @Module} annotation which
   * may consume servlet lifecycle objects and expose them to an object graph.  Implementations
   * of {@code FilterModuleConfigurator} must have a public, no-args constructor (or no
   * constructor but be public).
   *
   * Applications which use the built-in web scopes do not need to implement this interface
   * and configure servers with it, and should use the framework-provided
   * {@link ApplicationModule}, {@link SessionModule}, and {@link RequestModule} respectively.
   * {@code FilterModuleConfigurator} is only needed for applications which use custom
   * scoping annotations (as opposed to {@link @PerSession} and {@link @PerRequest}).
   *
   */
  public static abstract class FilterModuleConfigurator {
    /**
     * Returns a dagger module instance which exposes a binding for {@link ServletContext}
     */
    final Object applicationModule(ServletContext context) {
      return new ApplicationModule(context);
    }
    /**
     * Returns a dagger module instance which exposes a binding for HttpSession
     */
    abstract Object sessionModule(HttpSession session);
    abstract Object requestModule(HttpServletRequest request, HttpServletResponse response);
  }

  private static class DefaultFilterModuleConfigurator extends FilterModuleConfigurator {
    @Override Object sessionModule(HttpSession session) {
      return new SessionModule(session);
    }
    @Override Object requestModule(HttpServletRequest req, HttpServletResponse res) {
      return new RequestModule(req, res);
    }
  }
  static class Foo {
    @Inject HttpServletRequest request;
  }
  @Module(injects = Foo.class, includes = RequestModule.class)
  static class FooModule { }

}
