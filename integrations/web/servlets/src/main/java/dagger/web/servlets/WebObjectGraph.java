package dagger.web.servlets;

import dagger.ObjectGraph;
import javax.servlet.ServletRequest;

public class WebObjectGraph {

  public static ObjectGraph get(ServletRequest servletRequest) {
    ObjectGraph graph = (ObjectGraph)servletRequest.getAttribute("dagger.web.request.objectgraph");
    if (graph == null) {
      throw new IllegalStateException("No ObjectGraph found. "
          + "Did you remember to register DaggerServletFilter?");
    }
    return graph;
  }

}
