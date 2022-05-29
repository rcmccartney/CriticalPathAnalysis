package kvprog.common;

import dagger.grpc.server.CallScoped;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import dagger.producers.monitoring.ProducerToken;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry that gives each Dagger production component instance in a given request a unique name, and makes
 * Producer names more readable.
 */
@CallScoped
@ThreadSafe
final class Namer {

  // State used to create unique names per component, numbering them as required.
  private final Map<Object, String> componentNamesMap = new HashMap<>();
  private final Set<String> componentNamesSet = new HashSet<>();

  @Inject
  Namer() {
  }

  /**
   * Returns a human-readable name for the producer identified by this token.
   */
  static String producerName(ProducerToken token) {
    String name = token.toString();
    if (name.startsWith("class ")) {
      name = name.substring(6);
    }
    if (name.endsWith("Factory")) {
      name = name.substring(0, name.length() - 7);
    }

    return name;
  }

  private static String componentName(Class<?> implClass) {
    Class<?> currentImplClass = implClass;
    while (!currentImplClass.equals(Object.class)) {
      Class<?> superClass = currentImplClass.getSuperclass();
      if (isProductionComponent(superClass)) {
        return classToName(superClass);
      }
      Class<?>[] interfaces = currentImplClass.getInterfaces();
      if (interfaces.length > 0 && isProductionComponent(interfaces[0])) {
        return classToName(interfaces[0]);
      }
      currentImplClass = superClass;
    }
    throw new IllegalArgumentException(implClass + " is not a production component implementation");
  }

  private static boolean isProductionComponent(Class<?> clazz) {
    return clazz.isAnnotationPresent(ProductionComponent.class)
        || clazz.isAnnotationPresent(ProductionSubcomponent.class);
  }

  private static String classToName(Class<?> componentClass) {
    return componentClass.getName().replace('$', '.');
  }

  /**
   * Returns a unique component name, where conflicting names are incremented as #2, #3, etc.
   */
  synchronized String getName(Object component) {
    if (componentNamesMap.containsKey(component)) {
      return componentNamesMap.get(component);
    }

    String baseName = Namer.componentName(component.getClass());
    if (componentNamesSet.add(baseName)) {
      componentNamesMap.put(component, baseName);
      return baseName;
    }
    for (int i = 2; true; i++) {
      String name = baseName + "#" + i;
      if (componentNamesSet.add(name)) {
        componentNamesMap.put(component, name);
        return name;
      }
    }
  }
}