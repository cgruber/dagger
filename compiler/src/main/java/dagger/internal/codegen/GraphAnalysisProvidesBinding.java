package dagger.internal.codegen;

import dagger.internal.Binding;
import dagger.internal.Linker;
import java.util.Collections;
import java.util.Set;
import javax.inject.Singleton;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

import static dagger.internal.codegen.Util.methodName;

/**
 * A build time binding that handles dependency information for @{link @Provides} methods.
 */
final class GraphAnalysisProvidesBinding extends Binding<Object> {
  private final ExecutableElement method;
  private final Binding<?>[] parameters;

  protected GraphAnalysisProvidesBinding(String key, ExecutableElement method, boolean library) {
    super(key, null, method.getAnnotation(Singleton.class) != null, methodName(method));
    this.method = method;
    this.parameters = new Binding[method.getParameters().size()];
    setLibrary(library);
  }

  @Override public void attach(Linker linker) {
    for (int i = 0; i < method.getParameters().size(); i++) {
      VariableElement parameter = method.getParameters().get(i);
      String parameterKey = GeneratorKeys.get(parameter);
      parameters[i] = linker.requestBinding(parameterKey, method.toString(),
          getClass().getClassLoader());
    }
  }

  @Override public Object get() {
    throw new AssertionError("Compile-time binding should never be called to inject.");
  }

  @Override public void injectMembers(Object t) {
    throw new AssertionError("Compile-time binding should never be called to inject.");
  }

  @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
    Collections.addAll(get, parameters);
  }
}