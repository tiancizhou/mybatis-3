/**
 *    Copyright 2009-2018 the original author or authors.
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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.session.SqlSession;

/**
 * 代理工厂类，负责创建对应的Mapper接口的代理实例。
 * 
 * @author Lasse Voss
 */
public class MapperProxyFactory<T> {

    /**
     * Mapper接口
     */
    private final Class<T> mapperInterface;

    /**
     * 方法与MapperMethod的映射关系
     */
    private final Map<Method, MapperMethod> methodCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * 
     * @param mapperInterface Mapper接口
     */
    public MapperProxyFactory(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    /**
     * 获取Mapper接口
     * 
     * @return Mapper接口
     */
    public Class<T> getMapperInterface() {
        return mapperInterface;
    }

    /**
     * 获取方法缓存
     * 
     * @return 方法缓存
     */
    public Map<Method, MapperMethod> getMethodCache() {
        return methodCache;
    }

    /**
     * 通过MapperProxy创建Mapper的代理实例：
     * 
     * 1. 获取Mapper接口的ClassLoader
     * 2、接口数组
     * 3、实现InvocationHandler接口的MapperProxy实例
     * 
     * @param mapperProxy
     * @return
     */
    @SuppressWarnings("unchecked")
    protected T newInstance(MapperProxy<T> mapperProxy) {
        // 创建Mapper代理实例
        return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface },
                mapperProxy);
    }

    /**
     * 创建Mapper代理实例
     * 
     * @param sqlSession SqlSession
     * @return Mapper代理实例
     */
    public T newInstance(SqlSession sqlSession) {
        final MapperProxy<T> mapperProxy = new MapperProxy<>(sqlSession, mapperInterface, methodCache);
        return newInstance(mapperProxy);
    }

}
