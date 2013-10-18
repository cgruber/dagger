package two;

import dagger.Bindings;
import dagger.Provides;
import javax.inject.Inject;
import two.Context.HttpRequest;
import two.ModelForSession.User;
import two.Scopes.PerRequest;

@SuppressWarnings("unused")
final class ModelForRequest {
  private  ModelForRequest() { }

  // Types.

  @PerRequest static class Request {
    static Request create(HttpRequest s) { return new Request(); }
  }

  @PerRequest static class HomeAction {
    @Inject User user;
    @Inject Request request;
    void doStuff() { }
  }

  // bindings

  @Bindings
  static class RequestBindings {
    private final HttpRequest req;

    public RequestBindings(HttpRequest req) {
      this.req = req;
    }

    @Provides @PerRequest Request provideRequest() {
      return Request.create(req);
    }
  }

}
