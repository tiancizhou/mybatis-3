/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * @author Clinton Begin
 *
 * 属性分词器，支持迭代器的访问方式
 *
 * 其主要功能是对属性名进行解析和分词处理。
 * 它可以将一个完整的属性名（如 user.address.street 或 list[0].name）按照特定规则拆分成多个部分，方便后续对属性进行访问和操作。
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {

  /**
   * 当前字符串
   */
  private String name;

  /**
   * 索引
   */
  private final String indexedName;

  /**
   * 编号
   *
   * 对于数组 name[0] ，则 index = 0
   * 对于 Map map[key] ，则 index = key
   */
  private String index;

  /**
   * 剩余字符串
   */
  private final String children;

  public PropertyTokenizer(String fullname) {
    //假设fullname是userList[1].address.street
    //经过第一个if，name是userList[1]，children是address.street
    //经过第二个if，name是userList,index是1，indexedName是userList[1]

    //<1> 初始化name，children字符串，使用.作为分隔符
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      name = fullname.substring(0, delim);
      children = fullname.substring(delim + 1);
    } else {
      name = fullname;
      children = null;
    }
    //<2> 记录当前name
    indexedName = name;
    //若存在[，则获得index，并修改name
    delim = name.indexOf('[');
    if (delim > -1) {
      index = name.substring(delim + 1, name.length() - 1);
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
