package com.chaos.web.xml;

import static org.junit.jupiter.api.Assertions.*;

import com.chaos.core.exception.CommonErrorCode;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class SecureXmlDocumentsSerializationTest {
    @Test
    void preservesUnicodeAttributesAndEscapingAcrossRoundTrip() throws Exception {
        var doc =
                SecureXmlDocuments.parse(
                        "<root label='检测 &amp; test'>中文</root>".getBytes(StandardCharsets.UTF_8),
                        CommonErrorCode.BAD_REQUEST);
        byte[] serialized = SecureXmlDocuments.serialize(doc);
        var read = SecureXmlDocuments.parse(serialized, CommonErrorCode.BAD_REQUEST);
        assertEquals("检测 & test", read.getDocumentElement().getAttribute("label"));
        assertEquals("中文", read.getDocumentElement().getTextContent());
        assertTrue(new String(serialized, StandardCharsets.UTF_8).contains("UTF-8"));
    }
}
