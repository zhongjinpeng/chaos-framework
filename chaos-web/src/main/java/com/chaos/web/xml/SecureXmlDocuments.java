package com.chaos.web.xml;

import com.chaos.core.exception.BizException;
import com.chaos.core.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * 受限 XML 解析工具：禁用 DOCTYPE、外部实体、XInclude 和外部 DTD，解析失败时使用调用方提供的业务错误码。
 *
 * <p>所有入口共用同一套安全配置，避免不同调用方出现防护差异。
 */
public final class SecureXmlDocuments {

  private SecureXmlDocuments() {}

  /**
   * 解析 XML 正文。
   *
   * @param content XML 正文字节，调用方已校验非空和大小上限
   * @param errorCode 解析失败时使用的业务错误码
   * @return 解析后的 DOM 文档
   * @throws BizException 正文不是合法 XML、包含 DOCTYPE 或外部实体时抛出
   */
  public static Document parse(byte[] content, ErrorCode errorCode) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setNamespaceAware(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      // 默认 ErrorHandler 会把解析错误直接打印到标准错误，这里改为抛出异常，由调用方统一映射为业务错误。
      builder.setErrorHandler(
          new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) {
              // 警告不影响结构校验结果。
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
              throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
              throw exception;
            }
          });
      return builder.parse(new ByteArrayInputStream(content));
    } catch (ParserConfigurationException ex) {
      throw new IllegalStateException("XML 解析器初始化失败", ex);
    } catch (SAXException | IOException ex) {
      throw new BizException(errorCode, "XML 文档不是合法格式");
    }
  }
}
