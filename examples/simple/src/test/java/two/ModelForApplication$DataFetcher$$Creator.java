package two;

import dagger.ObjectGraph;
import dagger.internal.Creator;

public enum ModelForApplication$DataFetcher$$Creator
    implements Creator<ModelForApplication.DataFetcher> {
  INSTANCE;

  public ModelForApplication.DataFetcher get(ObjectGraph.Memoizer graphState) {
    ModelForApplication.DataFetcher instance = new ModelForApplication.DataFetcher();
    Creator<ModelForApplication.Cache> cache = 
        graphState.creatorFor(ModelForApplication.Cache.class);
    instance.cache = cache.get(graphState);
    
  }
}
