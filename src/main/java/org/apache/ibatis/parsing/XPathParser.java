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
package org.apache.ibatis.parsing;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.ibatis.builder.BuilderException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XPathParser {

/**
 * XPathParser 的作用是解析 XML 文件，并返回解析结果。
 *
 * 1、校验：entityResolver对xml文件进行格式验证，确保是一个合法的xml文件。
 * 2、分析：XPath对xml文件的节点和元素进行解析，配合variables进行替换变量。
 * 3、转换：将解析的节点和元素转换成document对象
 */

  /**
   * document：XML文件被解析为org.w3c.dom.Document对象
   */
  private final Document document;

  /**
   * validation：是否开启验证，对XML文件进行校验，一般是true
   */
  private boolean validation;

  /**
   * entityResolver：XML实体解析器。
   * DTD：用来定义 XML 文档的结构和语法的，确保 XML 文件符合特定的格式要求
   * 默认情况下，对XML进行校验的时候，会基于XML文档开头指定的dtd文件或者xsd文件。例如，mybatis-config.xml文档开头，指定了http://mybatis.org/dtd/mybatis-3-config.dtd
   * 如果所有应用都去在线下载dtd文件，这样会增加网络开销。
   * 所以实际场景下，mybatis自定义了XML实体解析器的实现，在启动的时候，使用本地dtd文件，从而减少网络开销。
   */
  private EntityResolver entityResolver;

  /**
   * variables：用于替换XML文件中的变量
   * 例如：
   * <dataSource type="POOLED">
   *   <property name="driver" value="${driver}"/>
   *   <property name="url" value="${url}"/>
   *   <property name="username" value="${username}"/>
   *   <property name="password" value="${password}"/>
   * </dataSource>
   *
   * variables的来源可以是yml、properties文件等。也可以是mybatis-config.xml文件中的properties标签。
   * <properties resource="org/mybatis/example/config.properties">
   *   <property name="username" value="dev_user"/>
   *   <property name="password" value="F2Fa3!33TYyg"/>
   * </properties>
   *

   */
  private Properties variables;

  /**
   * xpath：XPath对象，用于查询XML文件中的节点和元素。
   */
  private XPath xpath;

  public XPathParser(String xml) {
    commonConstructor(false, null, null);
    this.document = createDocument(new InputSource(new StringReader(xml)));
  }

  public XPathParser(Reader reader) {
    commonConstructor(false, null, null);
    this.document = createDocument(new InputSource(reader));
  }

  public XPathParser(InputStream inputStream) {
    commonConstructor(false, null, null);
    this.document = createDocument(new InputSource(inputStream));
  }

  public XPathParser(Document document) {
    commonConstructor(false, null, null);
    this.document = document;
  }

  public XPathParser(String xml, boolean validation) {
    commonConstructor(validation, null, null);
    this.document = createDocument(new InputSource(new StringReader(xml)));
  }

  public XPathParser(Reader reader, boolean validation) {
    commonConstructor(validation, null, null);
    this.document = createDocument(new InputSource(reader));
  }

  public XPathParser(InputStream inputStream, boolean validation) {
    commonConstructor(validation, null, null);
    this.document = createDocument(new InputSource(inputStream));
  }

  public XPathParser(Document document, boolean validation) {
    commonConstructor(validation, null, null);
    this.document = document;
  }

  public XPathParser(String xml, boolean validation, Properties variables) {
    commonConstructor(validation, variables, null);
    this.document = createDocument(new InputSource(new StringReader(xml)));
  }

  public XPathParser(Reader reader, boolean validation, Properties variables) {
    commonConstructor(validation, variables, null);
    this.document = createDocument(new InputSource(reader));
  }

  public XPathParser(InputStream inputStream, boolean validation, Properties variables) {
    commonConstructor(validation, variables, null);
    this.document = createDocument(new InputSource(inputStream));
  }

  public XPathParser(Document document, boolean validation, Properties variables) {
    commonConstructor(validation, variables, null);
    this.document = document;
  }

  /**
   *
   * @param xml xml文件地址
   * @param validation 是否开启校验
   * @param variables  xml文件中的变量对象
   * @param entityResolver xml文件实体解析器
   */
  public XPathParser(String xml, boolean validation, Properties variables, EntityResolver entityResolver) {
    //进行赋值操作
    commonConstructor(validation, variables, entityResolver);
    //创建document对象
    this.document = createDocument(new InputSource(new StringReader(xml)));
  }

  public XPathParser(Reader reader, boolean validation, Properties variables, EntityResolver entityResolver) {
    commonConstructor(validation, variables, entityResolver);
    this.document = createDocument(new InputSource(reader));
  }

  public XPathParser(InputStream inputStream, boolean validation, Properties variables, EntityResolver entityResolver) {
    commonConstructor(validation, variables, entityResolver);
    this.document = createDocument(new InputSource(inputStream));
  }

  public XPathParser(Document document, boolean validation, Properties variables, EntityResolver entityResolver) {
    commonConstructor(validation, variables, entityResolver);
    this.document = document;
  }

  public void setVariables(Properties variables) {
    this.variables = variables;
  }

  /**
   * 各种eval开头的方法
   * 用于从document中获取不同类型的属性
   *
   */
  public String evalString(String expression) {
    return evalString(document, expression);
  }

  public String evalString(Object root, String expression) {
    //1> 获取值
    String result = (String) evaluate(expression, root, XPathConstants.STRING);
    //2> 基于variables替换动态值。
    result = PropertyParser.parse(result, variables);
    return result;
  }

  public Boolean evalBoolean(String expression) {
    return evalBoolean(document, expression);
  }

  public Boolean evalBoolean(Object root, String expression) {
    return (Boolean) evaluate(expression, root, XPathConstants.BOOLEAN);
  }

  public Short evalShort(String expression) {
    return evalShort(document, expression);
  }

  public Short evalShort(Object root, String expression) {
    return Short.valueOf(evalString(root, expression));
  }

  public Integer evalInteger(String expression) {
    return evalInteger(document, expression);
  }

  public Integer evalInteger(Object root, String expression) {
    return Integer.valueOf(evalString(root, expression));
  }

  public Long evalLong(String expression) {
    return evalLong(document, expression);
  }

  public Long evalLong(Object root, String expression) {
    return Long.valueOf(evalString(root, expression));
  }

  public Float evalFloat(String expression) {
    return evalFloat(document, expression);
  }

  public Float evalFloat(Object root, String expression) {
    return Float.valueOf(evalString(root, expression));
  }

  public Double evalDouble(String expression) {
    return evalDouble(document, expression);
  }

  public Double evalDouble(Object root, String expression) {
    return (Double) evaluate(expression, root, XPathConstants.NUMBER);
  }

public List<XNode> evalNodes(String expression) {
  return evalNodes(document, expression);
}

public List<XNode> evalNodes(Object root, String expression) {
  List<XNode> xnodes = new ArrayList<>();
  NodeList nodes = (NodeList) evaluate(expression, root, XPathConstants.NODESET);
  for (int i = 0; i < nodes.getLength(); i++) {
    xnodes.add(new XNode(this, nodes.item(i), variables));
  }
  return xnodes;
}

  public XNode evalNode(String expression) {
    return evalNode(document, expression);
  }

  public XNode evalNode(Object root, String expression) {
    Node node = (Node) evaluate(expression, root, XPathConstants.NODE);
    if (node == null) {
      return null;
    }
    return new XNode(this, node, variables);
  }

  /**
   * 上面所有的eval开头的方法都是调用evaluate方法
   * 通过xpath的evaluate方法,从document中获取值。
   *
   * @param expression 表达式 xml文件里面的表达式
   * @param root 指定节点 这里是document对象
   * @param returnType 返回类型
   * @return 值
   */
  private Object evaluate(String expression, Object root, QName returnType) {
    try {
      return xpath.evaluate(expression, root, returnType);
    } catch (Exception e) {
      throw new BuilderException("Error evaluating XPath.  Cause: " + e, e);
    }
  }

  /**
   * 创建document对象
   *
   * @param inputSource xml文件的inputSource对象
   * @return document对象
   */
  private Document createDocument(InputSource inputSource) {
    // important: this must only be called AFTER common constructor
    try {
      //1> 创建DocumentBuilderFactory对象
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setValidating(validation);//是否开启校验

      factory.setNamespaceAware(false);
      factory.setIgnoringComments(true);
      factory.setIgnoringElementContentWhitespace(false);
      factory.setCoalescing(false);
      factory.setExpandEntityReferences(true);

      //2> 创建DocumentBuilder对象
      DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setEntityResolver(entityResolver);//设置实体解析器
      builder.setErrorHandler(new ErrorHandler() {
        @Override
        public void error(SAXParseException exception) throws SAXException {
          throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
          throw exception;
        }

        @Override
        public void warning(SAXParseException exception) throws SAXException {
        }
      });
      //3> 解析xml文件，返回Document对象
      return builder.parse(inputSource);
    } catch (Exception e) {
      throw new BuilderException("Error creating document instance.  Cause: " + e, e);
    }
  }

  private void commonConstructor(boolean validation, Properties variables, EntityResolver entityResolver) {
    this.validation = validation;
    this.entityResolver = entityResolver;
    this.variables = variables;
    XPathFactory factory = XPathFactory.newInstance();
    this.xpath = factory.newXPath();
  }

}
