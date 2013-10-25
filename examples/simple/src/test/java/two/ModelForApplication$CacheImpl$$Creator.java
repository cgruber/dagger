package two;

import dagger.ObjectGraph;
import dagger.internal.Creator;
import javax.inject.Provider;
import javax.inject.Singleton;


public enum ModelForApplication$CacheImpl$$Creator
    implements Creator<ModelForApplication.CacheImpl> {
  INSTANCE;

  private static final String KEY = ModelForApplication.CacheImpl.class.getName();

  public ModelForApplication.CacheImpl get(ObjectGraph.Memoizer graph) {
    return graph.getInstance(Singleton.class, KEY, new Provider<ModelForApplication.CacheImpl>() {
      @Override public ModelForApplication.CacheImpl get() {
        return new ModelForApplication.CacheImpl();
      }
    });
  }
}
