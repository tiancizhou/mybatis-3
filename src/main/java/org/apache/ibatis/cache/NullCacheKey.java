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
package org.apache.ibatis.cache;

/**
 * NullCacheKey 有一个静态的实例 NULL_CACHE_KEY，这表明它是一个单例模式的实现。这样可以确保在整个应用中只有一个空缓存键实例，避免重复创建，节省内存。
 * 在 MyBatis 中的使用场景：
 * 例如，当用户明确禁用了某个查询的缓存时，MyBatis 可能会使用 NullCacheKey 来替代常规的 CacheKey，从而避免缓存操作。
 * 此外，在一些特殊情况下，比如查询结果本身就是空的，或者需要区分有效缓存键和无效缓存键时，NullCacheKey 也能发挥作用。
 *
 * 同时，用户可能想知道为什么需要这样一个类，而不是直接使用 null 或者其他方式。这可能是因为在 MyBatis 的缓存机制中，缓存键必须是一个非 null 的对象，
 * 而 NullCacheKey 作为一个占位符，能够更清晰地表示空缓存键的状态，避免空指针异常，并且方便后续的处理。
 *
 *
 * @author Clinton Begin
 */
public final class NullCacheKey extends CacheKey {

  private static final long serialVersionUID = 3704229911977019465L;

  public NullCacheKey() {
    super();
  }

  @Override
  public void update(Object object) {
    throw new CacheException("Not allowed to update a NullCacheKey instance.");
  }

  @Override
  public void updateAll(Object[] objects) {
    throw new CacheException("Not allowed to update a NullCacheKey instance.");
  }
}
