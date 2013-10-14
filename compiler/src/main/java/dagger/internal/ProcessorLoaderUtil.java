package dagger.internal;

import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;


/**
 * An internal utility class for manipulating ModuleAdapters
 * @author Christian Gruber
 */
public final class ProcessorLoaderUtil {

  public static <M> ModuleAdapter<M> createModuleAdapter(M instance) {
    Module annotation = instance.getClass().getAnnotation(Module.class);
    if (annotation == null) {
      throw new IllegalArgumentException("No @Module on " + instance.getClass().getName());
    }
    return new InternalModuleAdapter<M>(instance, annotation);
  }

  private static final class InternalModuleAdapter<M> extends ModuleAdapter<M> {
    final Class<?> moduleClass;

    public InternalModuleAdapter(M instance, Module annotation) {
      super(
          injectableTypesToKeys(annotation.injects()),
          annotation.staticInjections(),
          annotation.overrides(),
          annotation.includes(),
          annotation.complete(),
          annotation.library());
      this.moduleClass = instance.getClass();
      this.module = instance;
    }

    private static String[] injectableTypesToKeys(Class<?>[] injectableTypes) {
      String[] result = new String[injectableTypes.length];
      for (int i = 0; i < injectableTypes.length; i++) {
        Class<?> injectableType = injectableTypes[i];
        result[i] = injectableType.isInterface()
            ? Keys.get(injectableType)
            : Keys.getMembersKey(injectableType);
      }
      return result;
    }

    @Override public void getBindings(Map<String, Binding<?>> bindings) {
      for (Class<?> c = moduleClass; !c.equals(Object.class); c = c.getSuperclass()) {
        for (Method method : c.getDeclaredMethods()) {
          Provides provides = method.getAnnotation(Provides.class);
          if (provides != null) {
            Type genericReturnType = method.getGenericReturnType();
            String key = Keys.get(genericReturnType, method.getAnnotations(), method);
            switch (provides.type()) {
              case UNIQUE:
                bindings.put(key, new ProviderMethodBinding<M>(method, key, module, library));
                break;
              default:
                throw new AssertionError("Unsupported @Provides type " + provides.type());
            }
          }
        }
      }
    }

    /**
     * Invokes a method to provide a value. The method's parameters are injected.
     */
    private final class ProviderMethodBinding<T> extends Binding<T> {
      private Binding<?>[] parameters;
      private final Method method;
      private final Object instance;

      public ProviderMethodBinding(Method method, String key, Object instance, boolean library) {
        super(key, null, method.isAnnotationPresent(Singleton.class),
            moduleClass.getName() + "." + method.getName() + "()");
        this.method = method;
        this.instance = instance;
        method.setAccessible(true);
        setLibrary(library);
      }

      @Override public void attach(Linker linker) {
        Type[] types = method.getGenericParameterTypes();
        Annotation[][] annotations = method.getParameterAnnotations();
        parameters = new Binding[types.length];
        for (int i = 0; i < parameters.length; i++) {
          String key = Keys.get(types[i], annotations[i], method + " parameter " + i);
          parameters[i] = linker.requestBinding(key, method, instance.getClass().getClassLoader());
        }
      }

      @Override public T get() {
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
          args[i] = parameters[i].get();
        }
        try {
          return (T) method.invoke(instance, args);
        } catch (InvocationTargetException e) {
          Throwable cause = e.getCause();
          throw cause instanceof RuntimeException
              ? (RuntimeException) cause
              : new RuntimeException(cause);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }

      @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
        for (Binding<?> binding : parameters) {
          get.add(binding);
        }
      }

      @Override public void injectMembers(T t) {
        throw new AssertionError("Provides method bindings are not MembersInjectors");
      }

      @Override public String toString() {
        return method.toString();
      }
    }
  }
}
