/**
 *    Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;

/**
 * Builds {@link SqlSession} instances.
 *
 * @author Clinton Begin
 */
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

  public SqlSessionFactory build(Reader reader, String environment) {
    return build(reader, environment, null);
  }

  public SqlSessionFactory build(Reader reader, Properties properties) {
    return build(reader, null, properties);
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

  public SqlSessionFactory build(InputStream inputStream) {
    return build(inputStream, null, null);
  }

  public SqlSessionFactory build(InputStream inputStream, String environment) {
    return build(inputStream, environment, null);
  }

  public SqlSessionFactory build(InputStream inputStream, Properties properties) {
    return build(inputStream, null, properties);
  }

  /**
   * mybatis构建阶段的调用入口
   *
   * @param inputStream
   * @param environment
   * @param properties
   * @return
   */
  public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
    try {
      //将配置文件的inputStream验证解析Configuration
      XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);
      //parse.parse()返回Configuration，并通过build方法构建DefaultSqlSessionFactory返回
      return build(parser.parse());
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
        inputStream.close();
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

  public SqlSessionFactory build(Configuration config) {
    return new DefaultSqlSessionFactory(config);
  }

}
