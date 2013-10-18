package two;

import dagger.Bindings;
import dagger.Graph;
import dagger.Provides;
import two.Graphs.ApplicationGraph;
import two.Graphs.SessionGraph;
import two.ModelForApplication.UserFactory;
import two.Scopes.PerSession;

final class TestGraphs {
  private  TestGraphs() { }

  @Bindings
  static class TestAppBindings {
    @Provides UserFactory provideFactory() {
      return new UserFactory() {
        // fake user factory.
      };
    }
  }

  @Graph(
      scope = PerSession.class,
      dependencies = ApplicationGraph.class,
      bindings = TestAppBindings.class)
  interface TestSessionGraph extends SessionGraph {
    @Override UserFactory userFactory();
  }


}
