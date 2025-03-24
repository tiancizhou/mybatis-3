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
package org.apache.ibatis.logging;

import java.lang.reflect.Constructor;

/**
 * LogFactory 类是 MyBatis 日志模块的入口类，主要用于获取日志实例。
 * 
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public final class LogFactory {

    /**
     * 日志标记，用于标识 MyBatis 日志
     * Marker to be used by logging implementations that support markers
     */
    public static final String MARKER = "MYBATIS";

    /**
     * 日志构造器，用于创建日志实例
     * 使用的 Log 的构造方法
     */
    private static Constructor<? extends Log> logConstructor;

    static {
        // <1> 逐个尝试，判断使用哪个 Log 的实现类，初始化 logConstructor
        tryImplementation(LogFactory::useSlf4jLogging);
        tryImplementation(LogFactory::useCommonsLogging);
        tryImplementation(LogFactory::useLog4J2Logging);
        tryImplementation(LogFactory::useLog4JLogging);
        tryImplementation(LogFactory::useJdkLogging);
        tryImplementation(LogFactory::useNoLogging);
    }

    private LogFactory() {
        // disable construction
    }

    /**
     * 获取 Log 实例
     * 
     * @param aClass
     * @return
     */
    public static Log getLog(Class<?> aClass) {
        return getLog(aClass.getName());
    }

    public static Log getLog(String logger) {
        try {
            return logConstructor.newInstance(logger);
        } catch (Throwable t) {
            throw new LogException("Error creating logger for logger " + logger + ".  Cause: " + t, t);
        }
    }

    /**
     * 使用自定义日志：可以是自己实现的 Log 类，也可以是第三方日志框架的实现类
     * 
     * @param clazz
     */
    public static synchronized void useCustomLogging(Class<? extends Log> clazz) {
        setImplementation(clazz);
    }

    /**
     * 使用 Slf4j 日志
     */
    public static synchronized void useSlf4jLogging() {
        setImplementation(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);
    }

    /**
     * 使用 Commons Logging 日志
     */
    public static synchronized void useCommonsLogging() {
        setImplementation(org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl.class);
    }

    /**
     * 使用 Log4j 日志
     */
    public static synchronized void useLog4JLogging() {
        setImplementation(org.apache.ibatis.logging.log4j.Log4jImpl.class);
    }

    /**
     * 使用 Log4j2 日志
     */
    public static synchronized void useLog4J2Logging() {
        setImplementation(org.apache.ibatis.logging.log4j2.Log4j2Impl.class);
    }

    /**
     * 使用 JDK 日志
     */
    public static synchronized void useJdkLogging() {
        setImplementation(org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl.class);
    }

    /**
     * 使用标准输出日志
     */
    public static synchronized void useStdOutLogging() {
        setImplementation(org.apache.ibatis.logging.stdout.StdOutImpl.class);
    }

    /**
     * 使用无日志
     */
    public static synchronized void useNoLogging() {
        setImplementation(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
    }

    /**
     * 尝试实现
     * 
     * @param runnable
     */
    private static void tryImplementation(Runnable runnable) {
        if (logConstructor == null) {
            try {
                runnable.run();
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    private static void setImplementation(Class<? extends Log> implClass) {
        try {
            // 获取 Log 的构造方法
            Constructor<? extends Log> candidate = implClass.getConstructor(String.class);
            // 创建 Log 实例
            Log log = candidate.newInstance(LogFactory.class.getName());
            // 如果日志级别为 DEBUG，则输出日志
            if (log.isDebugEnabled()) {
                log.debug("Logging initialized using '" + implClass + "' adapter.");
            }
            // 设置 Log 的构造方法
            logConstructor = candidate;
        } catch (Throwable t) {
            throw new LogException("Error setting Log implementation.  Cause: " + t, t);
        }
    }

}
