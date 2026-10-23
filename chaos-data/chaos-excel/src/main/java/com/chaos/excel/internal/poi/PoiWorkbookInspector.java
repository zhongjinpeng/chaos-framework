package com.chaos.excel.internal.poi;

import com.chaos.excel.ExcelProcessingException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;

/**
 * 使用 workbook.xml 的 StAX 解析读取 Sheet 名称，避免整本工作簿加载。
 */
public final class PoiWorkbookInspector {

    private PoiWorkbookInspector() {
    }

    /**
     * 按工作簿顺序读取 Sheet 名称。
     *
     * @param path XLSX 文件
     * @return Sheet 名列表
     */
    public static List<String> sheetNames(Path path) {
        try (OPCPackage packageFile = OPCPackage.open(path.toFile());
                InputStream workbook = new XSSFReader(packageFile).getWorkbookData()) {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader reader = factory.createXMLStreamReader(workbook);
            List<String> names = new ArrayList<>();
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT
                            && "sheet".equals(reader.getLocalName())) {
                        String name = attribute(reader, "name");
                        if (name != null && !name.isBlank()) {
                            names.add(name);
                        }
                    }
                }
            } finally {
                reader.close();
            }
            return List.copyOf(names);
        } catch (XMLStreamException exception) {
            throw new ExcelProcessingException("Excel workbook cannot be inspected", exception);
        } catch (Exception exception) {
            throw new ExcelProcessingException("Excel workbook cannot be inspected", exception);
        }
    }

    private static String attribute(XMLStreamReader reader, String name) {
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            if (name.equals(reader.getAttributeLocalName(index))) {
                return reader.getAttributeValue(index);
            }
        }
        return null;
    }
}
