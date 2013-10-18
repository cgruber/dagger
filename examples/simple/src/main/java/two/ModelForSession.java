package two;

import dagger.Bindings;
import dagger.Provides;
import two.Context.HttpSession;
import two.ModelForApplication.UserFactory;
import two.Scopes.PerSession;

@SuppressWarnings("unused")
final class ModelForSession {
  private ModelForSession() { }

  // Types.

  @PerSession static class Session {
    static Session create(HttpSession s) { return new Session(); }
    Long getUserId() {
      return 0L;
    }
  }

  @PerSession static class User {
    User(Long id) { }
  }

  // Bindings

  @Bindings
  static class SessionBindings {
    private final HttpSession session;

    public SessionBindings(HttpSession session) {
      this.session = session;
    }

    @Provides @PerSession Session provideSession() {
      return Session.create(session);
    }
  }

  @Bindings(includes = { SessionBindings.class })
  static class MySessionBindings {
    @Provides @PerSession User provideCache(UserFactory u, Session s) {
      return u.create(s.getUserId());
    }
  }


}
