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
package org.apache.ibatis.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * Result 注解用于配置结果集映射关系。主要用途:
 * 1. 将数据库列名映射到 Java 对象属性
 * 2. 配置类型转换和类型处理器
 * 3. 配置关联关系映射(一对一、一对多)
 * 
 * 主要属性:
 * - id: 是否为主键列
 * - column: 数据库列名
 * - property: Java 对象属性名
 * - javaType: Java 类型
 * - jdbcType: JDBC 类型
 * - typeHandler: 类型处理器
 * - one: 一对一关联配置
 * - many: 一对多关联配置
 * 
 * 示例:
 * 
 * @Results({
 *            @Result(property = "id", column = "user_id", id = true),
 * @Result(property = "name", column = "user_name"),
 * @Result(property = "email", column = "user_email")
 *                  })
 * 
 * @author Clinton Begin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Result {
    boolean id() default false;

    String column() default "";

    String property() default "";

    Class<?> javaType() default void.class;

    JdbcType jdbcType() default JdbcType.UNDEFINED;

    Class<? extends TypeHandler> typeHandler() default UnknownTypeHandler.class;

    One one() default @One;

    Many many() default @Many;
}
