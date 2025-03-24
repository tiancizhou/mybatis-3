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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * 继承 BaseBuilder 抽象类，XML 配置构建器，主要负责解析 mybatis-config.xml 配置文件
 * 
 * 1、通过 XpathParser 解析 mybatis-config.xml 配置文件，XpathParser提供了一系列eval方法来获取元素和节点
 * 
 * 
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

    /**
     * 是否已解析
     */
    private boolean parsed;
    /**
     * 解析器
     */
    private final XPathParser parser;
    /**
     * 环境，environment 字段的主要作用是存储 MyBatis 当前运行环境的标识符（ID），用于在多个环境配置中选择并初始化正确的数据库连接和其他相关资源
     */
    private String environment;

    /**
     * 类元数据工厂
     */
    private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

    public XMLConfigBuilder(Reader reader) {
        this(reader, null, null);
    }

    public XMLConfigBuilder(Reader reader, String environment) {
        this(reader, environment, null);
    }

    /**
     * 通过创建 XPathParser 对象，来解析 mybatis-config.xml 配置文件
     * 
     * @param reader
     * @param environment
     * @param props
     */
    public XMLConfigBuilder(Reader reader, String environment, Properties props) {
        this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    public XMLConfigBuilder(InputStream inputStream) {
        this(inputStream, null, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment) {
        this(inputStream, environment, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
        this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
        // <1> 创建 Configuration 对象,通过父类 BaseBuilder 的构造方法
        super(new Configuration());
        ErrorContext.instance().resource("SQL Mapper Configuration");
        // <2> 设置configuration的 variables 属性
        this.configuration.setVariables(props);
        this.parsed = false;
        this.environment = environment;
        this.parser = parser;
    }

    /**
     * 解析 XML 成 Configuration 对象
     * 
     * @return
     */
    public Configuration parse() {
        // 判断是否解析过
        if (parsed) {
            throw new BuilderException("Each XMLConfigBuilder can only be used once.");
        }
        // 设置被解析标志
        parsed = true;
        // 解析XMl中的 configuration 节点，知识达到闭环，通过eval方法族
        parseConfiguration(parser.evalNode("/configuration"));
        return configuration;
    }

    /**
     * 解析 XML 成 Configuration 对象
     *
     * @param root
     */
    private void parseConfiguration(XNode root) {
        try {
            // issue #117 read properties first
            // <1> 解析 <properties> 标签
            propertiesElement(root.evalNode("properties"));
            // <2> 解析 <settings> 标签
            Properties settings = settingsAsProperties(root.evalNode("settings"));
            // <3> 加载自定义VFS
            loadCustomVfs(settings);
            // <4> 加载自定义日志实现
            loadCustomLogImpl(settings);
            // <5> 解析 <typeAliases> 标签
            typeAliasesElement(root.evalNode("typeAliases"));
            // <6> 解析 <plugins> 标签
            pluginElement(root.evalNode("plugins"));
            // <7> 解析 <objectFactory> 标签
            objectFactoryElement(root.evalNode("objectFactory"));
            // <8> 解析 <objectWrapperFactory> 标签
            objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
            // <9> 解析 <reflectorFactory> 标签
            reflectorFactoryElement(root.evalNode("reflectorFactory"));
            // <10> 赋值 <settings /> 到 Configuration 属性
            //settingsElement(settings);
            // read it after objectFactory and objectWrapperFactory issue #631
            // <11> 解析 <environments> 标签
            environmentsElement(root.evalNode("environments"));
            // <12> 解析 <databaseIdProvider> 标签
            databaseIdProviderElement(root.evalNode("databaseIdProvider"));
            // <13> 解析 <typeHandlers> 标签
            typeHandlerElement(root.evalNode("typeHandlers"));
            // <14> 解析 <mappers> 标签
            mapperElement(root.evalNode("mappers"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
    }

    /**
     * 将子标签 settings 的内容，解析成 Properties 对象，并赋值给 Configuration 对象
     * @param context
     * @return
     */
    private Properties settingsAsProperties(XNode context) {
        //如果子标签 settings 为空，则返回一个空的 Properties 对象
        if (context == null) {
            return new Properties();
        }
        // 获取子标签 settings 的内容，并赋值给 Properties 对象
        Properties props = context.getChildrenAsProperties();
        // Check that all settings are known to the configuration class
        //创建 Configuration 类元数据（知识回顾：MetaClass是对Reflector的封装）
        MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
        // 遍历 Properties 对象，判断 Configuration 类是否拥有该属性的 setter 方法
        //这一步骤的目的是判断setting属性是否在Configuration类中
        for (Object key : props.keySet()) {
            if (!metaConfig.hasSetter(String.valueOf(key))) {
                throw new BuilderException(
                        "The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
            }
        }
        return props;
    }

    /**
     * 加载自定义的 VFS
     * 知识回顾：VFS是MyBatis用来加载资源的抽象，它提供了一种机制，用来加载类路径下的资源，或者加载网络资源。
     * @param props settings节点的内容转换成的Properties对象
     * @throws ClassNotFoundException
     */
    private void loadCustomVfs(Properties props) throws ClassNotFoundException {
        //获得自定义的VFS
        String value = props.getProperty("vfsImpl");
        if (value != null) {
            //使用，作为分隔符，拆成VFS类名的数组，因为可能存在多个VFS
            String[] clazzes = value.split(",");
            //遍历
            for (String clazz : clazzes) {
                if (!clazz.isEmpty()) {
                    //通过反射创建VFS实例
                    @SuppressWarnings("unchecked")
                    Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
                    configuration.setVfsImpl(vfsImpl);
                }
            }
        }
    }

    private void loadCustomLogImpl(Properties props) {
        Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
        configuration.setLogImpl(logImpl);
    }

    /**
     * 解析 <typeAliases> 标签
     * 配合 TypeAliasRegistry 来解析类的别名，如果没有设置别名，则使用简单类名作为别名
     * @param parent
     */
    private void typeAliasesElement(XNode parent) {
        if (parent != null) {
            //遍历子节点
            for (XNode child : parent.getChildren()) {
                //对于<package>子节点，注册包下的每个类
                if ("package".equals(child.getName())) {
                    //todo 这里为什么要使用configuration.getTypeAliasRegistry()来进行注册别名？
                    //不能统一使用该类的属性 typeAliasRegistry 是因为 typeAliasRegistry 可能在不同的上下文中被修改或重置，
                    //使用 configuration.getTypeAliasRegistry() 可以确保始终获取到最新的、与当前配置相关的 TypeAliasRegistry 实例，从而避免潜在的状态不一致问题。
                    String typeAliasPackage = child.getStringAttribute("name");
                    configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
                } else {
                    String alias = child.getStringAttribute("alias");
                    String type = child.getStringAttribute("type");
                    try {
                        Class<?> clazz = Resources.classForName(type);
                        if (alias == null) {
                            typeAliasRegistry.registerAlias(clazz);
                        } else {
                            typeAliasRegistry.registerAlias(alias, clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
                    }
                }
            }
        }
    }


    private void pluginElement(XNode parent) throws Exception {
        if (parent != null) {
            //遍历 plugin 标签
            for (XNode child : parent.getChildren()) {
                String interceptor = child.getStringAttribute("interceptor");
                Properties properties = child.getChildrenAsProperties();
                //<1> 创建 Interceptor 实例，并设置属性
                Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
                interceptorInstance.setProperties(properties);
                //<2> 添加到 configuration 中
                configuration.addInterceptor(interceptorInstance);
            }
        }
    }

    private void objectFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties properties = context.getChildrenAsProperties();
            ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
            factory.setProperties(properties);
            configuration.setObjectFactory(factory);
        }
    }

    private void objectWrapperFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
            configuration.setObjectWrapperFactory(factory);
        }
    }

    private void reflectorFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
            configuration.setReflectorFactory(factory);
        }
    }

    private void propertiesElement(XNode context) throws Exception {
        if (context != null) {
            // <1> 获取 <properties> 标签的子标签
            Properties defaults = context.getChildrenAsProperties();
            //读取 resource 和 url 属性，如果同时存在，则抛出异常。
            //这两个属性是引入的资源文件，例如jdbc.properties文件
            String resource = context.getStringAttribute("resource");
            String url = context.getStringAttribute("url");
            if (resource != null && url != null) {
                throw new BuilderException(
                        "The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
            }
            //读取本地 properties 文件到 defaults 中
            if (resource != null) {
                defaults.putAll(Resources.getResourceAsProperties(resource));
            } else if (url != null) {
                defaults.putAll(Resources.getUrlAsProperties(url));
            }
            // 覆盖 configuration 中的 Properties 对象到 defaults 中。
            Properties vars = configuration.getVariables();
            if (vars != null) {
                defaults.putAll(vars);
            }
            //设置 defaults 到 parser 和 configuration 中
            parser.setVariables(defaults);
            configuration.setVariables(defaults);
        }
    }

    private void settingsElement(Properties props) {
        configuration.setAutoMappingBehavior(
                AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
        configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior
                .valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
        configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
        configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
        configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
        configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
        configuration
                .setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
        configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
        configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
        configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
        configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
        configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
        configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
        configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
        configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
        configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
        configuration.setLazyLoadTriggerMethods(
                stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
        configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
        configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
        configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
        configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
        configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
        configuration
                .setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
        configuration.setLogPrefix(props.getProperty("logPrefix"));
        configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    }

    private void environmentsElement(XNode context) throws Exception {
        if (context != null) {
            //<1> environment 节点为空，则则default 属性获得
            if (environment == null) {
                environment = context.getStringAttribute("default");
            }
            //<2> 遍历子节点
            for (XNode child : context.getChildren()) {
                String id = child.getStringAttribute("id");
                if (isSpecifiedEnvironment(id)) {
                    //<3> 解析 transactionManager 和 dataSource
                    TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
                    DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
                    DataSource dataSource = dsFactory.getDataSource();
                    Environment.Builder environmentBuilder = new Environment.Builder(id)
                            .transactionFactory(txFactory)
                            .dataSource(dataSource);
                    configuration.setEnvironment(environmentBuilder.build());
                }
            }
        }
    }

    private void databaseIdProviderElement(XNode context) throws Exception {
        DatabaseIdProvider databaseIdProvider = null;
        if (context != null) {
            String type = context.getStringAttribute("type");
            // awful patch to keep backward compatibility
            if ("VENDOR".equals(type)) {
                type = "DB_VENDOR";
            }
            Properties properties = context.getChildrenAsProperties();
            databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
            databaseIdProvider.setProperties(properties);
        }
        Environment environment = configuration.getEnvironment();
        if (environment != null && databaseIdProvider != null) {
            String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
            configuration.setDatabaseId(databaseId);
        }
    }

    private TransactionFactory transactionManagerElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a TransactionFactory.");
    }

    private DataSourceFactory dataSourceElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a DataSourceFactory.");
    }

    private void typeHandlerElement(XNode parent) {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {
                    String typeHandlerPackage = child.getStringAttribute("name");
                    typeHandlerRegistry.register(typeHandlerPackage);
                } else {
                    String javaTypeName = child.getStringAttribute("javaType");
                    String jdbcTypeName = child.getStringAttribute("jdbcType");
                    String handlerTypeName = child.getStringAttribute("handler");
                    Class<?> javaTypeClass = resolveClass(javaTypeName);
                    JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
                    Class<?> typeHandlerClass = resolveClass(handlerTypeName);
                    if (javaTypeClass != null) {
                        if (jdbcType == null) {
                            typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
                        } else {
                            typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
                        }
                    } else {
                        typeHandlerRegistry.register(typeHandlerClass);
                    }
                }
            }
        }
    }

    /**
     * 在mybatis-config.xml中显示配置的mapper.xml文件，会注册起来，一个mapper接口对应一个mapperProxy。
     * @param parent
     * @throws Exception
     */
    private void mapperElement(XNode parent) throws Exception {
        if (parent != null) {
            // <0> 遍历子节点
            for (XNode child : parent.getChildren()) {
                //<1> 如果是package节点，则扫描该包
                if ("package".equals(child.getName())) {
                    String mapperPackage = child.getStringAttribute("name");
                    configuration.addMappers(mapperPackage);
                } else {
                    //获得 resource、url、class属性
                    String resource = child.getStringAttribute("resource");
                    String url = child.getStringAttribute("url");
                    String mapperClass = child.getStringAttribute("class");
                    //<2> 使用相对于类路径的资源引用
                    if (resource != null && url == null && mapperClass == null) {
                        ErrorContext.instance().resource(resource);
                        //获得输入流
                        InputStream inputStream = Resources.getResourceAsStream(resource);
                        //解析xml
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource,
                                configuration.getSqlFragments());
                        mapperParser.parse();
                     //<3> 使用完全限定资源定位符
                    } else if (resource == null && url != null && mapperClass == null) {
                        ErrorContext.instance().resource(url);
                        InputStream inputStream = Resources.getUrlAsStream(url);
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url,
                                configuration.getSqlFragments());
                        mapperParser.parse();
                     //<4> 使用映射器接口实现类的完全限定类名
                    } else if (resource == null && url == null && mapperClass != null) {
                        Class<?> mapperInterface = Resources.classForName(mapperClass);
                        configuration.addMapper(mapperInterface);
                    } else {
                        throw new BuilderException(
                                "A mapper element may only specify a url, resource or class, but not more than one.");
                    }
                }
            }
        }
    }

    private boolean isSpecifiedEnvironment(String id) {
        if (environment == null) {
            throw new BuilderException("No environment specified.");
        } else if (id == null) {
            throw new BuilderException("Environment requires an id attribute.");
        } else if (environment.equals(id)) {
            return true;
        }
        return false;
    }

}
