/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger.internal.codegen;

import com.google.common.base.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Maintains the collection of provision bindings from {@link Inject} constructors and members
 * injection bindings from {@link Inject} fields and methods known to the annotation processor.
 *
 * @author Gregory Kick
 */
final class BindingRegistry {
  private final Registry<ProvisionBinding> provisions;
  private final Registry<MembersInjectionBinding> membersInjections;

  BindingRegistry(final Key.Factory keyFactory) {
    this.provisions = new Registry<ProvisionBinding>() {
      @Override void register(ProvisionBinding binding) {
        ProvisionBinding previousValue = bindingsByKey.put(binding.providedKey(), binding);
        checkState(previousValue == null);
      }
    };
    this.membersInjections = new Registry<MembersInjectionBinding>() {
      @Override void register(MembersInjectionBinding binding) {
        MembersInjectionBinding previousValue = bindingsByKey.put(
            keyFactory.forType(binding.typeElement().asType()), binding);
        checkState(previousValue == null);
      }
    };
  }

  Registry<ProvisionBinding> provisions() {
    return provisions;
  }

  Registry<MembersInjectionBinding> membersInjections() {
    return membersInjections;
  }

  abstract static class Registry<T> {
    protected final Map<Key, T> bindingsByKey;
    Registry() {
      this.bindingsByKey = new LinkedHashMap<Key, T>();
    }

    abstract void register(T binding);

    Optional<T> getBindingForKey(Key key) {
      return Optional.fromNullable(bindingsByKey.get(checkNotNull(key)));
    }
  }
}
