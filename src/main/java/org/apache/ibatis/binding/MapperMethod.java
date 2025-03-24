/**
 *    Copyright 2009-2019 the original author or authors.
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

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * MapperMethod：Mapper接口方法的执行器，负责将Mapper接口的方法转换为数据库操作。
 * 每个Mapper接口的方法都会对应一个MapperMethod实例。
 * 
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
public class MapperMethod {

    /**
     * SqlCommand：Mapper方法的SQL命令信息
     * 
     * SqlCommand 是 MapperMethod 中的一个内部类，主要用于封装 Mapper 接口方法对应的 SQL 命令信息。它包含两个关键属性：
     * name - SQL 语句的唯一标识符（由 Mapper 接口名 + 方法名组成）
     * type - SQL 命令类型（INSERT/UPDATE/DELETE/SELECT/FLUSH）
     * 这个类的主要作用是将 Mapper 接口中的方法与 XML 配置文件中的 SQL 语句建立映射关系，便于后续执行具体的数据库操作。
     * 
     */
    private final SqlCommand command;

    /**
     * MethodSignature：Mapper方法的签名信息
     * 
     */
    private final MethodSignature method;

    public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
        this.command = new SqlCommand(config, mapperInterface, method);
        this.method = new MethodSignature(config, mapperInterface, method);
    }

    public Object execute(SqlSession sqlSession, Object[] args) {
        Object result;
        switch (command.getType()) {
            case INSERT: {
                Object param = method.convertArgsToSqlCommandParam(args);
                result = rowCountResult(sqlSession.insert(command.getName(), param));
                break;
            }
            case UPDATE: {
                Object param = method.convertArgsToSqlCommandParam(args);
                result = rowCountResult(sqlSession.update(command.getName(), param));
                break;
            }
            case DELETE: {
                Object param = method.convertArgsToSqlCommandParam(args);
                result = rowCountResult(sqlSession.delete(command.getName(), param));
                break;
            }
            case SELECT:
                if (method.returnsVoid() && method.hasResultHandler()) {
                    executeWithResultHandler(sqlSession, args);
                    result = null;
                } else if (method.returnsMany()) {
                    result = executeForMany(sqlSession, args);
                } else if (method.returnsMap()) {
                    result = executeForMap(sqlSession, args);
                } else if (method.returnsCursor()) {
                    result = executeForCursor(sqlSession, args);
                } else {
                    Object param = method.convertArgsToSqlCommandParam(args);
                    result = sqlSession.selectOne(command.getName(), param);
                    if (method.returnsOptional() &&
                            (result == null || !method.getReturnType().equals(result.getClass()))) {
                        result = Optional.ofNullable(result);
                    }
                }
                break;
            case FLUSH:
                result = sqlSession.flushStatements();
                break;
            default:
                throw new BindingException("Unknown execution method for: " + command.getName());
        }
        if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
            throw new BindingException("Mapper method '" + command.getName()
                    + " attempted to return null from a method with a primitive return type (" + method.getReturnType()
                    + ").");
        }
        return result;
    }

    private Object rowCountResult(int rowCount) {
        final Object result;
        if (method.returnsVoid()) {
            result = null;
        } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
            result = rowCount;
        } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
            result = (long) rowCount;
        } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
            result = rowCount > 0;
        } else {
            throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: "
                    + method.getReturnType());
        }
        return result;
    }

    private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
        MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
        if (!StatementType.CALLABLE.equals(ms.getStatementType())
                && void.class.equals(ms.getResultMaps().get(0).getType())) {
            throw new BindingException("method " + command.getName()
                    + " needs either a @ResultMap annotation, a @ResultType annotation,"
                    + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
        }
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
        } else {
            sqlSession.select(command.getName(), param, method.extractResultHandler(args));
        }
    }

    private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
        List<E> result;
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            result = sqlSession.<E>selectList(command.getName(), param, rowBounds);
        } else {
            result = sqlSession.<E>selectList(command.getName(), param);
        }
        // issue #510 Collections & arrays support
        if (!method.getReturnType().isAssignableFrom(result.getClass())) {
            if (method.getReturnType().isArray()) {
                return convertToArray(result);
            } else {
                return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
            }
        }
        return result;
    }

    private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
        Cursor<T> result;
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            result = sqlSession.<T>selectCursor(command.getName(), param, rowBounds);
        } else {
            result = sqlSession.<T>selectCursor(command.getName(), param);
        }
        return result;
    }

    private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
        Object collection = config.getObjectFactory().create(method.getReturnType());
        MetaObject metaObject = config.newMetaObject(collection);
        metaObject.addAll(list);
        return collection;
    }

    @SuppressWarnings("unchecked")
    private <E> Object convertToArray(List<E> list) {
        Class<?> arrayComponentType = method.getReturnType().getComponentType();
        Object array = Array.newInstance(arrayComponentType, list.size());
        if (arrayComponentType.isPrimitive()) {
            for (int i = 0; i < list.size(); i++) {
                Array.set(array, i, list.get(i));
            }
            return array;
        } else {
            return list.toArray((E[]) array);
        }
    }

    private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
        Map<K, V> result;
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
        } else {
            result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
        }
        return result;
    }

    public static class ParamMap<V> extends HashMap<String, V> {

        private static final long serialVersionUID = -2212268410512043556L;

        @Override
        public V get(Object key) {
            if (!super.containsKey(key)) {
                throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
            }
            return super.get(key);
        }

    }

    public static class SqlCommand {

        /**
         * SQL命令名称
         */
        private final String name;

        /**
         * SQL命令类型
         */
        private final SqlCommandType type;

        /**
         * 构造函数
         * 
         * @param configuration   配置信息
         * @param mapperInterface Mapper接口类
         * @param method          Mapper接口方法
         */
        public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
            final String methodName = method.getName();
            final Class<?> declaringClass = method.getDeclaringClass();
            // <1> 获得MappedStatement
            MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
                    configuration);
            // <2> 找不到MappedStatement
            if (ms == null) {
                // 如果方法上有@Flush注解，则设置name为null，type为FLUSH
                if (method.getAnnotation(Flush.class) != null) {
                    name = null;
                    type = SqlCommandType.FLUSH;
                } else {// 抛出异常
                    throw new BindingException("Invalid bound statement (not found): "
                            + mapperInterface.getName() + "." + methodName);
                }
            } else {// <3> 找到MappedStatement
                name = ms.getId();
                type = ms.getSqlCommandType();
                if (type == SqlCommandType.UNKNOWN) {// <4> 抛出异常
                    throw new BindingException("Unknown execution method for: " + name);
                }
            }
        }

        public String getName() {
            return name;
        }

        public SqlCommandType getType() {
            return type;
        }

        private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
                Class<?> declaringClass, Configuration configuration) {
            // <1> 生成statementId
            String statementId = mapperInterface.getName() + "." + methodName;
            // <2> 判断是否存在MappedStatement
            // Configuration 里缓存了所有的 MappedStatement ，并且每一个 XML 里声明的例如 <select /> 或者
            // <update/> 等等，都对应一个 MappedStatement 对象。
            if (configuration.hasStatement(statementId)) {
                return configuration.getMappedStatement(statementId);
                // <3> 如果Mapper接口和声明类相同，则返回null
            } else if (mapperInterface.equals(declaringClass)) {
                return null;
            }
            // <4> 遍历Mapper接口的父接口，递归查找MappedStatement
            for (Class<?> superInterface : mapperInterface.getInterfaces()) {
                if (declaringClass.isAssignableFrom(superInterface)) {
                    MappedStatement ms = resolveMappedStatement(superInterface, methodName,
                            declaringClass, configuration);
                    if (ms != null) {
                        return ms;
                    }
                }
            }
            return null;
        }
    }

    public static class MethodSignature {

        /**
         * 是否返回多个结果
         */
        private final boolean returnsMany;
        /**
         * 是否返回Map类型结果
         */
        private final boolean returnsMap;
        /**
         * 是否返回void
         */
        private final boolean returnsVoid;
        /**
         * 是否返回Cursor类型结果
         */
        private final boolean returnsCursor;
        /**
         * 是否返回Optional类型结果
         */
        private final boolean returnsOptional;
        /**
         * 返回类型
         */
        private final Class<?> returnType;
        /**
         * 返回方法上的 {@link MapKey#value()} ，前提是返回类型为 Map
         */
        private final String mapKey;
        /**
         * ResultHandler参数的索引位置
         */
        private final Integer resultHandlerIndex;
        /**
         * RowBounds参数的索引位置
         */
        private final Integer rowBoundsIndex;
        /**
         * 参数名称解析器
         */
        private final ParamNameResolver paramNameResolver;

        public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
            // 初始化 returnType 属性
            Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
            if (resolvedReturnType instanceof Class<?>) { // 普通类
                this.returnType = (Class<?>) resolvedReturnType;
            } else if (resolvedReturnType instanceof ParameterizedType) { // 泛型类
                this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
            } else { // 其他类型
                this.returnType = method.getReturnType();
            }

            this.returnsVoid = void.class.equals(this.returnType);

            this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType)
                    || this.returnType.isArray();
            this.returnsCursor = Cursor.class.equals(this.returnType);
            this.returnsOptional = Optional.class.equals(this.returnType);
            this.mapKey = getMapKey(method);
            this.returnsMap = this.mapKey != null;
            this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
            this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
            this.paramNameResolver = new ParamNameResolver(configuration, method);
        }

        /**
         * 获得SQl通用参数
         * 
         * @param args
         * @return
         */
        public Object convertArgsToSqlCommandParam(Object[] args) {
            return paramNameResolver.getNamedParams(args);
        }

        public boolean hasRowBounds() {
            return rowBoundsIndex != null;
        }

        public RowBounds extractRowBounds(Object[] args) {
            return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
        }

        public boolean hasResultHandler() {
            return resultHandlerIndex != null;
        }

        public ResultHandler extractResultHandler(Object[] args) {
            return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
        }

        public String getMapKey() {
            return mapKey;
        }

        public Class<?> getReturnType() {
            return returnType;
        }

        public boolean returnsMany() {
            return returnsMany;
        }

        public boolean returnsMap() {
            return returnsMap;
        }

        public boolean returnsVoid() {
            return returnsVoid;
        }

        public boolean returnsCursor() {
            return returnsCursor;
        }

        /**
         * return whether return type is {@code java.util.Optional}
         * 
         * @return return {@code true}, if return type is {@code java.util.Optional}
         * @since 3.5.0
         */
        public boolean returnsOptional() {
            return returnsOptional;
        }

        /**
         * 这个方法 `getUniqueParamIndex` 用于获取方法参数中指定类型的唯一参数索引。例如，如果有一个方法 `void
         * exampleMethod(String name, Integer age)`，调用
         * `getUniqueParamIndex(exampleMethod, Integer.class)` 将返回 `1`，因为 `Integer`
         * 类型的参数在索引 `1` 处。如果方法中有多个 `Integer` 类型的参数，则会抛出异常。
         * 
         * @param method
         * @param paramType
         * @return
         */
        private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
            Integer index = null;
            // 获取方法的参数类型数组
            final Class<?>[] argTypes = method.getParameterTypes();
            // 遍历参数类型数组
            for (int i = 0; i < argTypes.length; i++) {
                // 如果参数类型是目标类型
                if (paramType.isAssignableFrom(argTypes[i])) {
                    // 如果还没有找到唯一参数索引，则设置唯一参数索引
                    if (index == null) {
                        index = i;
                    } else {
                        throw new BindingException(method.getName() + " cannot have multiple "
                                + paramType.getSimpleName() + " parameters");
                    }
                }
            }
            return index;
        }

        /**
         * 例如，假设有一个方法如下：
         * 
         * ```java
         * @MapKey("id")
         * Map<String, User> getUsers();
         * ```
         * 
         * 在这个例子中，`mapKey` 字段将存储 `"id"`，表示在返回的 `Map` 中，`User` 对象的 `id` 属性将作为键。
         * 
         * @param method
         * @return
         */
        private String getMapKey(Method method) {
            String mapKey = null;
            if (Map.class.isAssignableFrom(method.getReturnType())) {
                final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
                if (mapKeyAnnotation != null) {
                    mapKey = mapKeyAnnotation.value();
                }
            }
            return mapKey;
        }
    }

}
