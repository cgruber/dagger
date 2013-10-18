package two;

import dagger.Graph;
import dagger.GraphSpec;
import two.ModelForApplication.ApplicationBindings;
import two.ModelForApplication.Cache;
import two.ModelForApplication.DataFetcher;
import two.ModelForApplication.UserFactory;
import two.ModelForRequest.HomeAction;
import two.ModelForSession.MySessionBindings;
import two.Scopes.PerRequest;
import two.Scopes.PerSession;

final class Graphs {
  private  Graphs() { }

  @GraphSpec
  interface ApplicationGraph {
    UserFactory userFactory();
  }

  @Graph(bindings = ApplicationBindings.class)
  interface ProductionApplicationGraph extends ApplicationGraph {
    @Override UserFactory userFactory();
    Cache cache();
    DataFetcher dataFetcher();
  }

  @GraphSpec
  interface SessionGraph {
    UserFactory userFactory();
  }

  @Graph(
      scope = PerSession.class,
      dependencies = ApplicationGraph.class,
      bindings = MySessionBindings.class)
  interface ProductionSessionGraph extends SessionGraph {
    @Override UserFactory userFactory();
  }

  @Graph(
      scope = PerRequest.class,
      dependencies = SessionGraph.class,
      bindings = MySessionBindings.class)
  interface RequestGraph {
    HomeAction homeAction();
  }

}
