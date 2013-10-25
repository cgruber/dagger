package two;

import dagger.ObjectGraph;
import dagger.internal.Creator;
import javax.inject.Provider;
import javax.inject.Singleton;


final class ModelForApplication$ApplicationBindings$$BindingMapper
    implements Creator<ModelForApplication.Cache> {

  private final Creator<ModelForApplication.CacheImpl> cache = 
      new ModelForApplication$CacheImpl$$Creator();
  private final ModelForApplication.ApplicationBindings bindings =
      new ModelForApplication.ApplicationBindings();

  public ModelForApplication$ApplicationBindings$$BindingMapper(
      ModelForApplication.ApplicationBindings bindings
      ) {
    this.bindings = bindings;
  }

  private static final String KEY = ModelForApplication.CacheImpl.class.getName();

  public ModelForApplication.Cache get(ObjectGraph.Memoizer graph) {
    // If the @Bindings class has no parameters in the constructor, we can just create it
    // statically and omit this line.
    ModelForApplication.ApplicationBindings bindings =
        graph.getBindings(ModelForApplication.ApplicationBindings.class);
    
    return graph.getInstance(Singleton.class, KEY, new Provider<ModelForApplication.CacheImpl>() {
      @Override public ModelForApplication.CacheImpl get() {
        return new ModelForApplication.CacheImpl();
      }
    });
  }
}
