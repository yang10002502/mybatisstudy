# 源码解读
## SqlSessionFactoryBuilder
```java
public class SqlSessionFactoryBuilder {
    /**
     * 根据配置文件的输入构建SqlSessionFactory
     *
     * @param reader
     * @return
     */
    public SqlSessionFactory build(Reader reader) {
        return build(reader, null, null);
    }

    /**
     * 使用一个参照了XML文档或更特定的SqlMapConfig.xml文件的Reader实例
     * 可选的参数是environment和properties。Environment决定加载哪种环境(开发环境/生产环境)，包括数据源和事务管理器
     * 如果使用properties，那么就会加载那些properties（属性配置文件）
     *
     * @param reader
     * @param environment
     * @param properties
     * @return
     */
    public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
        try {
            // 根据xml配置文件构建XmlConfigBuilder对象，从磁盘中加载到内存对象加快数据的读取
            XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);
            // 将解析器解析得到的Configuration作为DefaultSqlSessionFactory构造函数的入参传入赋值
            return build(parser.parse());
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error building SqlSession.", e);
        } finally {
            ErrorContext.instance().reset();
            try {
                reader.close();
            } catch (IOException e) {
                // Intentionally ignore. Prefer previous error.
            }
        }
    }

}
```

## XMLConfigBuilder
```java
/**
 * 验证解析mybatis-config.xml文件，并转化成Configuration类
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {
    public Configuration parse() {
        // 配置文件只允许解析一次
        if (parsed) {
          throw new BuilderException("Each XMLConfigBuilder can only be used once.");
        }
        parsed = true;
        //在初始化过程中已经将mybatis-config.xml文件解析成document对象
        //这里使用xpath解析工具获取配置文件根节点中的configuration元素
        parseConfiguration(parser.evalNode("/configuration"));
        return configuration;
    }

    /**
      * 扫描configuration节点下的诸如properties、typeAliases节点做相应的处理
      *
      * @param root
      */
    private void parseConfiguration(XNode root) {
        try {
            //加载属性配置 (read properties first)
            propertiesElement(root.evalNode("properties"));
            //加载自定义实现
            Properties settings = settingsAsProperties(root.evalNode("settings"));
            loadCustomVfs(settings);
            loadCustomLogImpl(settings);
        
            //加载别名处理器
            typeAliasesElement(root.evalNode("typeAliases"));
            //加载自定义扩展点
            pluginElement(root.evalNode("plugins"));
            //加载结果集对象工厂
            objectFactoryElement(root.evalNode("objectFactory"));
            objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
            reflectorFactoryElement(root.evalNode("reflectorFactory"));
            settingsElement(settings);
            // read it after objectFactory and objectWrapperFactory issue #631
            //加载环境数据库厂商标志
            environmentsElement(root.evalNode("environments"));
            //加载数据库厂商标志
            databaseIdProviderElement(root.evalNode("databaseIdProvider"));
            //加载类型处理器
            typeHandlerElement(root.evalNode("typeHandlers"));
            //加载SQL中的mapper
            mapperElement(root.evalNode("mappers"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
    }

    /**
     * 内首先判断子元素是不是package，若是<mapper resource="mapper/demo.xml"/>这种形式的
     * <mappers>
     *   <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
     *   <mapper resource="org/mybatis/builder/BlogMapper.xml"/>
     *   <mapper resource="org/mybatis/builder/PostMapper.xml"/>
     * </mappers>
     */
    private void mapperElement(XNode parent) throws Exception {
        // 设置mappers标签
        if (parent != null) {
            // 依次遍历mappers的子标签mapper
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {//取出package标签
                    String mapperPackage = child.getStringAttribute("name");
                    configuration.addMappers(mapperPackage);
                } else {
                    String resource = child.getStringAttribute("resource");//取出resource标签
                    String url = child.getStringAttribute("url");
                    String mapperClass = child.getStringAttribute("class");
                    if (resource != null && url == null && mapperClass == null) {
                        //todo key
                        ErrorContext.instance().resource(resource);
                        InputStream inputStream = Resources.getResourceAsStream(resource);
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
                        mapperParser.parse();
                    } else if (resource == null && url != null && mapperClass == null) {
                        ErrorContext.instance().resource(url);
                        InputStream inputStream = Resources.getUrlAsStream(url);
                        // 加载xml文件，解析mapper接口
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
                        mapperParser.parse();
                    } else if (resource == null && url == null && mapperClass != null) {
                        Class<?> mapperInterface = Resources.classForName(mapperClass);
                        configuration.addMapper(mapperInterface);
                    } else {
                        throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
                    }
                }
            }
        }
    }
}
```

## XMLMapperBuilder
```java
/**
 * 解析xml定义的SQL映射配置对象集合
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLMapperBuilder extends BaseBuilder {
    /**
     * 获取mapper文件的namespace来实例化mapper接口
     * 完成大部分配置内容的加载工作，使MapperRegistry注册机将构建好的Mapper注册到configuration对象中
     */
    public void parse() {
        //判断是否已经加载过，防止相同的SQL Mapper的配置被重复加载
        if (!configuration.isResourceLoaded(resource)) {
            //没有加载过，则解析mapper，并添加至configuration中
            configurationElement(parser.evalNode("/mapper"));
            // 在set集合对象loadedResources添加
            configuration.addLoadedResource(resource);
            bindMapperForNamespace();
        }

        parsePendingResultMaps();
        parsePendingCacheRefs();
        parsePendingStatements();
    }
    
    private void configurationElement(XNode context) {
        try {
            /**
             *   加载SQL的命名空间，必须指定namespace，若未设置，则抛出异常，一般使用mapper或Dao的接口名
             * <mapper namespace="com.chinaentropy.mapper.ConsumerMapper">
             */
            String namespace = context.getStringAttribute("namespace");
            if (namespace == null || namespace.equals("")) {
                throw new BuilderException("Mapper's namespace cannot be empty");
            }
            //将当前的配置的命名空间配置奥configuration对象
            builderAssistant.setCurrentNamespace(namespace);
            //加载缓存配置
            cacheRefElement(context.evalNode("cache-ref"));
            cacheElement(context.evalNode("cache"));
            //解析parameterMap标签（现在已废弃）
            parameterMapElement(context.evalNodes("/mapper/parameterMap"));
            //解析resultMap标签，加载结果集映射
            resultMapElements(context.evalNodes("/mapper/resultMap"));
            //解析sql标签，加载SQL片段
            sqlElement(context.evalNodes("/mapper/sql"));
            //绑定mapper接口和mapper.xml接口的方法，加载SQL语句
            buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
        }   catch (Exception e) {
            throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
        }
    }

    private void buildStatementFromContext(List<XNode> list) {
        if (configuration.getDatabaseId() != null) {
            buildStatementFromContext(list, configuration.getDatabaseId());
        }
        // 解析insert、select、update、delete标签
        buildStatementFromContext(list, null);
    }
    
    private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
        for (XNode context : list) {
            // 依次遍历mapper文件中定义的insert、select、update、delete标签
            final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
            try {
                statementParser.parseStatementNode();
            } catch (IncompleteElementException e) {
                configuration.addIncompleteStatement(statementParser);
            }
        }
    }
    
    private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType) throws Exception {
        ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
        // 从resultMap中获取type指定的实体类的全路径名
        String type = resultMapNode.getStringAttribute("type",
            resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
            resultMapNode.getStringAttribute("javaType"))));
        // 通过resolveClass方法来加载实体类
        Class<?> typeClass = resolveClass(type);
        if (typeClass == null) {
            typeClass = inheritEnclosingType(resultMapNode, enclosingType);
        }
        Discriminator discriminator = null;
        List<ResultMapping> resultMappings = new ArrayList<>();
        resultMappings.addAll(additionalResultMappings);
        //resultMap节点下的子节点result列表
        List<XNode> resultChildren = resultMapNode.getChildren();
        for (XNode resultChild : resultChildren) {
            if ("constructor".equals(resultChild.getName())) {
                processConstructorElement(resultChild, typeClass, resultMappings);
            }else if ("discriminator".equals(resultChild.getName())) {
                discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
            }else {
                List<ResultFlag> flags = new ArrayList<>();
                if ("id".equals(resultChild.getName())) {
                  flags.add(ResultFlag.ID);
                }
                // 解析result转化成ResultMapping，并添加至resultMappings列表中
                resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
            }
        }
        String id = resultMapNode.getStringAttribute("id", resultMapNode.getValueBasedIdentifier());
        String extend = resultMapNode.getStringAttribute("extends");
        Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
        // 封装ResultMapResolver对象
        ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
        try {
            //调用ResultMapResolver对象的resolve方法，生成ResultMap对象
            return resultMapResolver.resolve();
        } catch (IncompleteElementException e) {
            //在configuration中的incompleteResultMaps属性中加入未完成的任务
            configuration.addIncompleteResultMap(resultMapResolver);
            throw e;
        }
    }


}
```

## XMLStatementBuilder
```java
public class XMLStatementBuilder extends BaseBuilder {

    public void parseStatementNode() {
    
        String id = context.getStringAttribute("id");
        String databaseId = context.getStringAttribute("databaseId");
    
        if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
            return;
        }
    
        String nodeName = context.getNode().getNodeName();
        SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
        // 检验是否为select标签
        boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
        boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
        boolean useCache = context.getBooleanAttribute("useCache", isSelect);
        boolean resultOrdered = context.getBooleanAttribute("resultOrdered", false);
    
        // Include Fragments before parsing
        // 是否在语句中存在include标签
        XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
        includeParser.applyIncludes(context.getNode());
        //参数类型；将会传入这条语句的参数类的完全限定名或别名。
        // 这个属性是可选的，因为 MyBatis 可以通过 TypeHandler 推断出具体传入语句的参数，默认值为 unset。
        String parameterType = context.getStringAttribute("parameterType");
        Class<?> parameterTypeClass = resolveClass(parameterType);
    
        String lang = context.getStringAttribute("lang");
        LanguageDriver langDriver = getLanguageDriver(lang);
    
        // Parse selectKey after includes and remove them.
        processSelectKeyNodes(id, parameterTypeClass, langDriver);
    
        // Parse the SQL (pre: <selectKey> and <include> were parsed and removed)
        KeyGenerator keyGenerator;
        String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
        keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
        if (configuration.hasKeyGenerator(keyStatementId)) {
            keyGenerator = configuration.getKeyGenerator(keyStatementId);
        } else {
            keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
                configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
                ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
        }
    
        SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);
        StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));
        Integer fetchSize = context.getIntAttribute("fetchSize");
        Integer timeout = context.getIntAttribute("timeout");
        String parameterMap = context.getStringAttribute("parameterMap");
        // 解析resultType和resultMap属性
        String resultType = context.getStringAttribute("resultType");
        Class<?> resultTypeClass = resolveClass(resultType);
        String resultMap = context.getStringAttribute("resultMap");
        String resultSetType = context.getStringAttribute("resultSetType");
        ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);
        if (resultSetTypeEnum == null) {
          resultSetTypeEnum = configuration.getDefaultResultSetType();
        }
        String keyProperty = context.getStringAttribute("keyProperty");
        String keyColumn = context.getStringAttribute("keyColumn");
        String resultSets = context.getStringAttribute("resultSets");
    
        builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
            fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
            resultSetTypeEnum, flushCache, useCache, resultOrdered,
            keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets);
    }
}
```