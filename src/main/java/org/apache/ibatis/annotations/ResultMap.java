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
 * ResultMap 注解用于引用已有的结果映射。与 @Results 注解的主要区别:
 * 
 * 1. @Results 用于定义新的结果映射:
 * - 直接定义字段映射规则
 * - 需要配置详细的 @Result 注解
 * - 适用于首次定义映射关系
 * 
 * 2. @ResultMap 用于复用已有映射:
 * - 只需要指定要引用的映射 id
 * - 更简洁,避免重复配置
 * - 可以复用 XML 和注解中的映射
 * 
 * 示例对比:
 * 
 * // 使用 @Results 定义映射
 * 
 * @Results(id = "userResultMap", value = {
 * @Result(property = "id", column = "user_id", id = true),
 * @Result(property = "name", column = "user_name"),
 * @Result(property = "email", column = "user_email")
 *                  })
 *                  User getById(int id);
 * 
 *                  // 使用 @ResultMap 复用已定义的映射
 *                  @Select("SELECT * FROM users")
 *                  @ResultMap("userResultMap") // 直接引用,无需重复配置
 *                  List<User> getAllUsers();
 * 
 *                  // 复用多个映射
 *                  @Select("SELECT u.*, r.* FROM users u JOIN roles r")
 *                  @ResultMap({"userResultMap", "roleResultMap"}) // 可以引用多个映射
 *                  List<UserWithRole> getUsersWithRoles();
 * 
 * @author Jeff Butler
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ResultMap {
    String[] value();
}
