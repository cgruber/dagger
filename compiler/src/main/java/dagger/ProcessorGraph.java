/*
 * Copyright (C) 2013 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dagger;

import dagger.internal.Binding;
import dagger.internal.Loader;
import dagger.internal.ModuleAdapter;
import dagger.internal.ProcessorLoaderUtil;
import dagger.internal.StaticInjection;
import dagger.internal.loaders.ReflectiveAtInjectBinding;


/**
 * A wrapper around ObjectGraph.create that creates a graph with a reflection-only
 * {@link Loader} for internal use of Dagger in the processors.
 *
 * @author Christian Gruber
 */
public final class ProcessorGraph {
  public static ObjectGraph create(Object ... modules) {
    return ObjectGraph.createWith(new Loader() {
      @Override public Binding<?> getAtInjectBinding(
          String key, String className, ClassLoader classLoader, boolean mustHaveInjections) {

        Class<?> type = loadClass(classLoader, className);
        if (type.equals(Void.class)) {
          throw new IllegalStateException(
              String.format("Could not load class %s needed for binding %s", className, key));
        }
        return ReflectiveAtInjectBinding.create(type, mustHaveInjections);
      }

      @Override public <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> type, T instance) {
        return ProcessorLoaderUtil.createModuleAdapter(instance);
      }

      @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
        throw new UnsupportedOperationException("Dagger processors don't use static injection.");
      }

    }, modules);
  }

}