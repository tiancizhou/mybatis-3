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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SelectProvider 是 MyBatis 提供的一个注解,用于实现动态 SQL 查询。
 * 
 * 它有两个主要属性:
 * 1. type: 指定一个类,这个类包含生成 SQL 的方法
 * 2. method: 指定 type 类中的具体方法名
 * 
 * 工作原理:
 * - 当执行被 @SelectProvider 注解的 Mapper 方法时
 * - MyBatis 会调用 type 类中指定的 method 方法
 * - 该方法返回一个 String 类型的 SQL 语句
 * - MyBatis 执行这个动态生成的 SQL
 * 
 * 示例:
 * 
 * @SelectProvider(type = UserSqlProvider.class, method = "findById")
 *                      User getUser(int id);
 * 
 *                      public class UserSqlProvider {
 *                      public String findById(int id) {
 *                      return "SELECT * FROM users WHERE id = #{id}";
 *                      }
 *                      }
 *
 * @author Clinton Begin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SelectProvider {
    Class<?> type();

    String method();
}
