/**
 *    Copyright 2009-2016 the original author or authors.
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
 * Results 注解用于配置结果集映射,对应 XML 配置中的 <resultMap> 标签。
 * 
 * 主要功能:
 * 1. 定义结果集映射规则
 * 2. 可以包含多个 @Result 注解配置具体的字段映射
 * 3. 支持给映射规则指定一个唯一的 id
 * 
 * 示例:
 * 
 * @Results(id = "userResultMap", value = {
 * @Result(property = "id", column = "user_id", id = true),
 * @Result(property = "name", column = "user_name"),
 * @Result(property = "email", column = "user_email")
 *                  })
 * 
 *                  对应的 XML:
 *                  <resultMap id="userResultMap" type="User">
 *                  <id property="id" column="user_id"/>
 *                  <result property="name" column="user_name"/>
 *                  <result property="email" column="user_email"/>
 *                  </resultMap>
 *
 * @author Clinton Begin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Results {
    /**
     * The name of the result map.
     */
    String id() default "";

    Result[] value() default {};
}
