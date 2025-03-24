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
package org.apache.ibatis.binding;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;

/**
 * Mapper代理类，实现了InvocationHandler接口：
 * 
 * 
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

    private static final long serialVersionUID = -6424540398559729838L;

    /**
     * SqlSession：MyBatis与数据库交互的桥梁，定义了一些通用的CRUD方法的接口。
     */
    private final SqlSession sqlSession;

    /**
     * Mapper接口
     */
    private final Class<T> mapperInterface;

    /**
     * 方法与MapperMethod的映射关系
     */
    private final Map<Method, MapperMethod> methodCache;

    public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
        this.sqlSession = sqlSession;
        this.mapperInterface = mapperInterface;
        this.methodCache = methodCache;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            // <1> 如果是Object定义的方法，则直接调用
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, args);
                // <2> 如果是默认方法，则调用invokeDefaultMethod
                // 默认方法（Default
                // Method）是Java8引入的特性，允许在接口中定义带有实现的方法。通过在方法前加default关键字，接口可以提供默认实现，实现该接口的类可以直接使用这个方法，也可以选择重写它。这样可以在不破坏现有实现的情况下，向接口添加新功能。
            } else if (isDefaultMethod(method)) {
                return invokeDefaultMethod(proxy, method, args);
            }
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }
        // <3> 从methodCache中获取MapperMethod
        final MapperMethod mapperMethod = cachedMapperMethod(method);
        // <4> 执行MapperMethod
        return mapperMethod.execute(sqlSession, args);
    }

    /**
     * 从methodCache中获取MapperMethod
     * 
     * @param method
     * @return
     */
    private MapperMethod cachedMapperMethod(Method method) {
        return methodCache.computeIfAbsent(method,
                k -> new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
    }

    private Object invokeDefaultMethod(Object proxy, Method method, Object[] args)
            throws Throwable {
        final Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class
                .getDeclaredConstructor(Class.class, int.class);
        if (!constructor.isAccessible()) {
            constructor.setAccessible(true);
        }
        final Class<?> declaringClass = method.getDeclaringClass();
        return constructor
                .newInstance(declaringClass,
                        MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
                                | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC)
                .unreflectSpecial(method, declaringClass).bindTo(proxy).invokeWithArguments(args);
    }

    /**
     * Backport of java.lang.reflect.Method#isDefault()
     */
    private boolean isDefaultMethod(Method method) {
        return (method.getModifiers()
                & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) == Modifier.PUBLIC
                && method.getDeclaringClass().isInterface();
    }
}
