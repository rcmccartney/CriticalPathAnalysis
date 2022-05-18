package kvprog.common;

import dagger.grpc.server.CallScoped;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import dagger.producers.monitoring.ProducerToken;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

/**
 * Registry that gives each Dagger production component instance (in a given request) a unique
 * name.
 */
@CallScoped
@ThreadSafe
final class Names {

  private final Map<Object, String> componentNamesMap = new HashMap<>();
  private final Set<String> componentNamesSet = new HashSet<>();

  @Inject
  Names() {
  }

  /**
   * Returns a human-readable name for the producer identified by this token.
   */
  static String producerName(ProducerToken token) {
    String name = token.toString();
    if (name.startsWith("class ")) {
      return name.substring(6);
    } else {
      return name;
    }
  }

  static String componentName(Class<?> implClass) {
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

  synchronized String getName(Object component) {
    if (componentNamesMap.containsKey(component)) {
      return componentNamesMap.get(component);
    }

    String baseName = Names.componentName(component.getClass());
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