/**
 *    Copyright 2009-2025 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */

/**
 * 反射器
 * 每个类型的类，都会创建一个 Reflector 对象，这个对象会缓存类的信息，包括类中的方法，属性，构造函数等信息。
 */
public class Reflector {

  /**
   * 对应的类
   */
  private final Class<?> type;

  /**
   * 可读属性名称
   */
  private final String[] readablePropertyNames;
  /**
   * 可写属性名称
   */

  private final String[] writeablePropertyNames;

  /**
   * 属性对应的set方法的映射
   * key是属性
   * value是Invoker对象
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();

  /**
   * 属性对应的get方法的映射
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();

  /**
   * 属性对应的set方法的方法参数类型的映射
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();

  /**
   * 属性对应的get方法的返回值类型的映射
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();

  /**
   * 默认的构造函数
   */
  private Constructor<?> defaultConstructor;


  /**
   * 不区分大小写的属性集合
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    //设置对应的类
    type = clazz;
    //<1> 初始化默认的构造函数
    addDefaultConstructor(clazz);
    //<2> 初始化get方法和get方法的返回值参数类型,通过遍历getting方法
      //（1）获取get方法：判断方法名是否以get或者is开头，如果是，则添加到getMethods中。
      //（2）获取get方法的返回值类型：在获取get方法的时候，有一个步骤是解决getting冲突，在这里会进行添加get方法的返回值类型。
    addGetMethods(clazz);
    //<3> 初始化setMethod和set方法的参数类型，通过遍历setting方法
    addSetMethods(clazz);
    //<4> 初始化上面两步的内容，通过遍历fields属性。todo 为什么这里需要重复初始化set和get？因为考虑到有些filed没有get和set方法，所以需要初始化。
    addFields(clazz);
    //<5> 初始化可读属性和可写属性、caseInsensitivePropertyMap属性
    readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
    writeablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writeablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addDefaultConstructor(Class<?> clazz) {
    //获取所有的构造方法
    Constructor<?>[] consts = clazz.getDeclaredConstructors();
    //遍历构造方法，找到无参的构造方法
    for (Constructor<?> constructor : consts) {
      if (constructor.getParameterTypes().length == 0) {
          this.defaultConstructor = constructor;
      }
    }
  }

  private void addGetMethods(Class<?> cls) {
    //<1> 属性与起getting方法的映射
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    //<2> 获取所有方法
    Method[] methods = getClassMethods(cls);
    //<3> 遍历所有方法，获取所有的getting方法
    for (Method method : methods) {
      //<3.1> 参数不为0，说明不是get方法
      if (method.getParameterTypes().length > 0) {
        continue;
      }
      //<3.2> 方法名以get和is开头
      String name = method.getName();
      if ((name.startsWith("get") && name.length() > 3)
          || (name.startsWith("is") && name.length() > 2)) {
        //<3.3> 通过get方法的命名规则，获取属性名称
        name = PropertyNamer.methodToProperty(name);
        //<3.4> 添加到属性与其getting方法的映射中，例如getId这个方法，添加进去，id是key，getId()是value
        addMethodConflict(conflictingGetters, name, method);
      }
    }
    //<4> 解决getting冲突方法，因为子类可以重写父类的方法，所以一个属性可能对应多个getting方法
    resolveGetterConflicts(conflictingGetters);
  }

  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    // 遍历所有属性，查找最匹配的方法，因为子类可以重写父类的方法，所以一个属性可能对应多个getting方法
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      String propName = entry.getKey();
      for (Method candidate : entry.getValue()) {
        //如果winner为空，说明是第一个方法，直接赋值给winner
        if (winner == null) {
          winner = candidate;
          continue;
        }
        //<1> 基于返回值类型比较
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        //返回值类型相同
        if (candidateType.equals(winnerType)) {
          //如果返回值类型不是boolean类型，则方法名相同、返回值类型相同、参数类型相同的方法在getClassMethod中已经合并。
          //如果是boolean类型，则返回方法名是is开头的方法。
          if (!boolean.class.equals(candidateType)) {
            throw new ReflectionException(
                "Illegal overloaded getter method with ambiguous type for property "
                    + propName + " in class " + winner.getDeclaringClass()
                    + ". This breaks the JavaBeans specification and can cause unpredictable results.");
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
        // 看candidateType是否是winnerType的父类
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        // <1.1> 看candidateType是否是winnerType的子类，因为子类可以修改放大返回值。例如，父类的一个方法的返回值为 List ，子类对该方法的返回值可以覆写为 ArrayList
        } else if (winnerType.isAssignableFrom(candidateType)) {
          winner = candidate;
        // <1.2> 返回类型冲突，则抛出异常
        } else {
          throw new ReflectionException(
              "Illegal overloaded getter method with ambiguous type for property "
                  + propName + " in class " + winner.getDeclaringClass()
                  + ". This breaks the JavaBeans specification and can cause unpredictable results.");
        }
      }
      //<2> 添加到getMethods和getTypes中
      addGetMethod(propName, winner);
    }
  }

  private void addGetMethod(String name, Method method) {
    //判断是合理的属性名
    if (isValidPropertyName(name)) {
      //<2.1> 添加到getMethods
      getMethods.put(name, new MethodInvoker(method));
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);
      //<2.2> 添加到getTypes
      getTypes.put(name, typeToClass(returnType));
    }
  }

  private void addSetMethods(Class<?> cls) {
    // 属性和其setting方法的映射
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    //获取所有方法
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      String name = method.getName();
      //<1> 通过获取以set开头，并且参数个数为1的method
      if (name.startsWith("set") && name.length() > 3) {
        if (method.getParameterTypes().length == 1) {
          //获取属性名
          name = PropertyNamer.methodToProperty(name);
          addMethodConflict(conflictingSetters, name, method);
        }
      }
    }
    //<2> 解决set方法冲突
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    //<1> 获取属性名对应的List<method>：如果不存在，则创建一个空的list，如果存在，则获取这个属性名对应的list
    List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
    list.add(method);
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (String propName : conflictingSetters.keySet()) {
      List<Method> setters = conflictingSetters.get(propName);
      Class<?> getterType = getTypes.get(propName);
      Method match = null;
      ReflectionException exception = null;
      //<1> 遍历属性对应的setting方法
      for (Method setter : setters) {
        //如果setting方法的参数类型和getting方法的返回值类型相同，则match赋值为这个setter方法
        Class<?> paramType = setter.getParameterTypes()[0];
        if (paramType.equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (exception == null) {
          try {
            //选择一个更加匹配的
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            // there could still be the 'best match'
            match = null;
            exception = e;
          }
        }
      }
      //<2> 添加到setMethods和setTypes中
      if (match == null) {
        throw exception;
      } else {
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
        + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
        + paramType2.getName() + "'.");
  }

  private void addSetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      setMethods.put(name, new MethodInvoker(method));
      Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
      setTypes.put(name, typeToClass(paramTypes[0]));
    }
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    //普通类型，直接使用类
    if (src instanceof Class) {
      result = (Class<?>) src;
    //泛型，使用泛型
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    //泛型数组，获得具体的类
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) { //普通类型
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType); //递归该方法
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    //都不符合，使用object类
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    //获取所有field
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      //如果setMethods和getMethods不包含field，则添加到setMethods和getMethods中
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    //递归处理父类
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler Class.getMethods(),
   * because we want to look for private methods as well.
   *
   * @param cls The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> cls) {
    //每个方法签名（不是简单的方法名，是方法名、返回值类型和参数类型的结合体）和方法的映射，
    Map<String, Method> uniqueMethods = new HashMap<>();
    // 循环类，一直向上找父类，直到父类是Object
    Class<?> currentClass = cls;
    while (currentClass != null && currentClass != Object.class) {
      //<1> 记录当前类定义的方法
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      //<2> 记录接口中定义的方法
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }
      // 获取父类
      currentClass = currentClass.getSuperclass();
    }
    // 返回方法数组
    Collection<Method> methods = uniqueMethods.values();
    return methods.toArray(new Method[methods.size()]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      if (!currentMethod.isBridge()) { //isBridge方法详情看https://www.zhihu.com/question/54895701/answer/141623158
        // 获取方法签名，
        //格式：returnType#方法名:参数名1,参数名2,参数名3 。
        //例如：void#checkPackageAccess:java.lang.ClassLoader,boolean 。
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method

        //将方法签名与方法进行映射添加到uniqueMethods集合中
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    // 方法返回值类型
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    //方法名
    sb.append(method.getName());
    //方法参数类型
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      if (i == 0) {
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   * System.getSecurityManager()：该方法用于获取当前 Java 虚拟机的安全管理器。
   * 安全管理器是 Java 中用于实施安全策略的机制，它可以控制应用程序对系统资源的访问。
   * 如果没有设置安全管理器，该方法返回 null。
   *
   * securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"))：
   * 如果安全管理器不为 null，则调用其 checkPermission 方法来检查是否具有 ReflectPermission("suppressAccessChecks") 权限。
   * ReflectPermission("suppressAccessChecks") 表示允许代码绕过 Java 的访问控制检查，例如可以访问私有字段或调用私有方法。
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   *
   *

   *
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writeablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
