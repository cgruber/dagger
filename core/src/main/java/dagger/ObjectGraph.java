/*
 * Copyright (C) 2012 Square, Inc.
 * Copyright (C) 2012 Google, Inc.
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


@SuppressWarnings("unused")
public final  class ObjectGraph {
  private ObjectGraph() { }
  public static <T> T create(Class<T> entryPointClass, Object... statefulModules) {
    return null;
  }

  public static With extend(Object graphRoot) {
    return new With() {
      @Override public <T> T with(Class<T> entryPointClass, Object... moduleInstances) {
        return null;
      }
    };
  }

  public interface With {
    <T> T with(Class<T> entryPointClass, Object... moduleInstances);
  }

}
