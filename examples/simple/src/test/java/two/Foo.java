package two;

import dagger.Bindings;
import dagger.Provides;
import javax.inject.Inject;
import javax.inject.Singleton;
import two.ModelForSession.User;

final class Foo {
  private Foo() { }

  // Types.
  interface Cache { }

  // Not scoped
  static class CacheImpl implements Cache {
    CacheImpl() { }
  }

  @Singleton static class DataFetcher {
    @Inject Cache cache;
  }

  @Singleton static class UserFactory {
    @Inject Cache cache;
    @Inject DataFetcher fetcher;
    public User create(Long id) {
      return new User(id);
    }
  }

  // Bindings

  @Bindings
  static class ApplicationBindings {
    @Provides Cache provideCache(CacheImpl impl) {
      return impl;
    }
  }


}
