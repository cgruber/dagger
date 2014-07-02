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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Queues;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.MembersInjector;
import dagger.Module;
import dagger.Provides;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static dagger.internal.codegen.AnnotationMirrors.getAnnotationMirror;
import static dagger.internal.codegen.FrameworkKey.REQUEST_TO_FRAMEWORK_KEY;
import static javax.lang.model.element.Modifier.ABSTRACT;

/**
 * The logical representation of a {@link Component} definition.
 *
 * @author Gregory Kick
 * @author Christian Gruber
 * @since 2.0
 */
@AutoValue
abstract class ComponentDescriptor {
  ComponentDescriptor() {}

  /**
   * The type (interface or abstract class) that defines the component. This is the element to which
   * the {@link Component} annotation was applied.
   */
  abstract TypeElement componentDefinitionType();

  /**
   * The list of {@link DependencyRequest} instances whose sources are methods on the component
   * definition type.  These are the user-requested dependencies.
   */
  abstract ImmutableList<DependencyRequest> interfaceRequests();

  /**
   * The total set of modules (those declared in {@link Component#modules} and their transitive
   * dependencies) required to construct the object graph declared by the component.
   */
  abstract ImmutableSet<TypeElement> moduleDependencies();

  /*
   * Returns the mapping from {@link Key} to {@link ProvisionBinding} that
   * (with {@link #resolvedMembersInjectionBindings}) represents the full adjacency matrix for the
   * object graph.
   */
  //abstract ImmutableSetMultimap<Key, ProvisionBinding> resolvedProvisionBindings();

  /*
   * Returns the mapping from {@link Key} to {@link MembersInjectionBinding} that
   * (with {@link #resolvedProvisionBindings}) represents the full adjacency matrix for the object
   * graph.
   */
  //abstract ImmutableMap<Key, MembersInjectionBinding> resolvedMembersInjectionBindings();

  /**
   * Returns the in-order mapping from {@link FrameworkKey} to {@link Binding} that
   * represents the full adjacency matrix for the object graph.
   *
   * The iteration order of {@link FrameworkKey keys} allows all of the {@link Factory} and
   * {@link MembersInjector} implementations to initialize properly.
   */
  abstract ImmutableSetMultimap<FrameworkKey, Binding> bindings();

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final BindingRegistry bindingRegistry;
    private final ProvisionBinding.Factory provisionBindingFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(
        Elements elements,
        Types types,
        BindingRegistry injectBindingRegistry,
        ProvisionBinding.Factory provisionBindingFactory,
        DependencyRequest.Factory dependencyRequestFactory) {
      this.elements = elements;
      this.types = types;
      this.bindingRegistry = injectBindingRegistry;
      this.provisionBindingFactory = provisionBindingFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    private ImmutableSet<TypeElement> getTransitiveModules(ImmutableSet<TypeElement> seedModules) {
      Queue<TypeElement> moduleQueue = Queues.newArrayDeque(seedModules);
      LinkedHashSet<TypeElement> moduleElements = Sets.newLinkedHashSet();
      for (TypeElement moduleElement = moduleQueue.poll();
          moduleElement != null;
          moduleElement = moduleQueue.poll()) {
        moduleElements.add(moduleElement);
        AnnotationMirror moduleMirror =
            getAnnotationMirror(moduleElement, Module.class).get();
        ImmutableSet<TypeElement> moduleDependencies = MoreTypes.asTypeElements(types,
            ConfigurationAnnotations.getModuleIncludes(elements, moduleMirror));
        for (TypeElement dependencyType : moduleDependencies) {
          if (!moduleElements.contains(dependencyType)) {
            moduleQueue.add(dependencyType);
          }
        }
      }
      return ImmutableSet.copyOf(moduleElements);
    }

    ComponentDescriptor create(TypeElement componentDefinitionType) {
      AnnotationMirror componentMirror =
          getAnnotationMirror(componentDefinitionType, Component.class).get();
      ImmutableSet<TypeElement> moduleTypes = MoreTypes.asTypeElements(types,
          ConfigurationAnnotations.getComponentModules(elements, componentMirror));
      ImmutableSet<TypeElement> transitiveModules = getTransitiveModules(moduleTypes);

      ProvisionBinding componentBinding =
          provisionBindingFactory.forComponent(componentDefinitionType);

      ImmutableSetMultimap.Builder<Key, ProvisionBinding> bindingIndexBuilder =
          new ImmutableSetMultimap.Builder<Key, ProvisionBinding>()
              .put(componentBinding.providedKey(), componentBinding);


      for (TypeElement module : transitiveModules) {
        // traverse the modules, collect the bindings
        List<ExecutableElement> moduleMethods =
            ElementFilter.methodsIn(elements.getAllMembers(module));
        for (ExecutableElement moduleMethod : moduleMethods) {
          if (moduleMethod.getAnnotation(Provides.class) != null) {
            ProvisionBinding providesMethodBinding =
                provisionBindingFactory.forProvidesMethod(moduleMethod);
            bindingIndexBuilder.put(providesMethodBinding.providedKey(), providesMethodBinding);
          }
        }
      }

      ImmutableSetMultimap<Key, ProvisionBinding> explicitBindings = bindingIndexBuilder.build();

      ImmutableList.Builder<DependencyRequest> interfaceRequestsBuilder = ImmutableList.builder();

      Deque<DependencyRequest> requestsToResolve = Queues.newArrayDeque();

      for (ExecutableElement componentMethod
          : ElementFilter.methodsIn(elements.getAllMembers(componentDefinitionType))) {
        if (componentMethod.getModifiers().contains(ABSTRACT)) {
          List<? extends VariableElement> parameters = componentMethod.getParameters();
          switch (parameters.size()) {
            case 0:
              // provision method
              DependencyRequest provisionRequest =
                  dependencyRequestFactory.forComponentProvisionMethod(componentMethod);
              interfaceRequestsBuilder.add(provisionRequest);
              requestsToResolve.addLast(provisionRequest);
              break;
            case 1:
              // members injection method
              DependencyRequest membersInjectionRequest =
                  dependencyRequestFactory.forComponentMembersInjectionMethod(componentMethod);
              interfaceRequestsBuilder.add(membersInjectionRequest);
              requestsToResolve.addLast(membersInjectionRequest);
              break;
            default:
              throw new IllegalStateException();
          }
        }
      }


      SetMultimap<Key, ProvisionBinding> resolvedProvisionBindings = LinkedHashMultimap.create();
      SetMultimap<FrameworkKey, Binding> resolvedRequests = LinkedHashMultimap.create();

      for (DependencyRequest request : requestsToResolve) {
        Deque<Key> path = new ArrayDeque<Key>();
        path.push(request.key());
        switch (request.kind()) {
          case MEMBERS_INJECTOR:
            resolveDependencyForMembersInjectionBinding(request, explicitBindings, resolvedRequests,
                resolvedProvisionBindings, path);
            break;
          case INSTANCE:
          case LAZY:
          case PROVIDER:
            // all non-MEMBERS_INJECTOR requests are provision requests
            resolveDependencyForProvisionBinding(request, explicitBindings, resolvedRequests,
                resolvedProvisionBindings, path);
            break;
          default:
            throw new AssertionError("Unknown request kind for: " + request);
        }
      }
      return new AutoValue_ComponentDescriptor(
          componentDefinitionType,
          interfaceRequestsBuilder.build(),
          moduleTypes,
          ImmutableSetMultimap.copyOf(resolvedRequests));
    }



    private void resolveDependencyForMembersInjectionBinding(DependencyRequest request,
        ImmutableSetMultimap<Key, ProvisionBinding> explicitBindings,
        SetMultimap<FrameworkKey, Binding> resolvedRequests,
        SetMultimap<Key, ProvisionBinding> resolvedProvisionBindings,
        Deque<Key> path) {
      FrameworkKey frameworkKey = FrameworkKey.forDependencyRequest(request);
      if (!resolvedRequests.containsKey(frameworkKey)) {
        if (bindingRegistry.membersInjections().getBindingForKey(frameworkKey.key()).isPresent()) {
          MembersInjectionBinding binding =
              bindingRegistry.membersInjections().getBindingForKey(frameworkKey.key()).get();
          if (!isResolved(resolvedRequests, binding)) {
            for (DependencyRequest dependency: binding.dependencies()) {
              resolveDependencyForProvisionBinding(dependency, explicitBindings, resolvedRequests,
                  resolvedProvisionBindings, path);
            }
          }
          resolvedRequests.put(frameworkKey, binding);
        } else {
          // TODO: check and generate.
          throw new UnsupportedOperationException(
              "Unprocessesed MembersInjectors are (briefly) unsupported: " + frameworkKey.key());
        }
      }
    }

    private void resolveDependencyForProvisionBinding(DependencyRequest request,
        ImmutableSetMultimap<Key, ProvisionBinding> explicitBindings,
        SetMultimap<FrameworkKey, Binding> resolvedRequests,
        SetMultimap<Key, ProvisionBinding> resolvedProvisionBindings,
        Deque<Key> path) {
      FrameworkKey frameworkKey = FrameworkKey.forDependencyRequest(request);
      ImmutableSet<ProvisionBinding> explicitBindingsForKey =
          explicitBindings.get(frameworkKey.key());
      if (explicitBindingsForKey.isEmpty()) {
        // @Inject Constructor
        if (bindingRegistry.provisions().getBindingForKey(frameworkKey.key()).isPresent()) {
          ProvisionBinding binding =
              bindingRegistry.provisions().getBindingForKey(frameworkKey.key()).get();
          if (!isResolved(resolvedRequests, binding)) {
            for (DependencyRequest dependency: binding.dependencies()) {
              resolveDependencyForProvisionBinding(dependency, explicitBindings, resolvedRequests,
                  resolvedProvisionBindings, path);
            }
            for (DependencyRequest dependency: binding.membersInjector().asSet()) {
              resolveDependencyForMembersInjectionBinding(dependency, explicitBindings,
                  resolvedRequests, resolvedProvisionBindings, path);
            }
          }
          resolvedProvisionBindings.put(frameworkKey.key(), binding);
          resolvedRequests.put(frameworkKey, binding);
        } else {
          // TODO(gak): support this
          throw new UnsupportedOperationException(
              "Unprocessesed @Inject classes are (briefly) unsupported: "
              + frameworkKey.key());
        }
      } else {
        // @Provides binding, interface method binding, or component self-binding.
        for (ProvisionBinding explicitBinding : explicitBindingsForKey) {
          for (DependencyRequest dependency : explicitBinding.dependencies()) {
            resolveDependencyForProvisionBinding(dependency, explicitBindings, resolvedRequests,
                resolvedProvisionBindings, path);
          }
          resolvedProvisionBindings.put(frameworkKey.key(), explicitBinding);
          resolvedRequests.put(frameworkKey, explicitBinding);
        }
      }
    }

    private boolean isResolved(
        SetMultimap<FrameworkKey, Binding> resolvedRequests, MembersInjectionBinding binding) {
      return resolvedRequests.keySet().containsAll(binding.dependenciesByKey().keySet());
    }

    private boolean isResolved(
        SetMultimap<FrameworkKey, Binding> resolvedRequests, ProvisionBinding binding) {
      return resolvedRequests.keySet().containsAll(binding.dependenciesByKey().keySet())
          && resolvedRequests.keySet().containsAll(
              binding.membersInjector().transform(REQUEST_TO_FRAMEWORK_KEY).asSet());
    }
  }
}

