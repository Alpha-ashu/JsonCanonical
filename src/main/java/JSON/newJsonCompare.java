package JSON;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class newJsonCompare {

	private static final Logger logger = LoggerFactory.getLogger(JSONCompareDynamic.class);

    public static void main(String[] args) {
        String mappingFilePath = "C:\\Users\\nezam\\eclipse-workspace\\Canocial\\src\\main\\java\\Data\\mapping.xlsx";
        String oldFilePath = "C:\\Users\\nezam\\eclipse-workspace\\Canocial\\src\\main\\java\\Data\\response1.json";
        String newFilePath = "C:\\Users\\nezam\\eclipse-workspace\\Canocial\\src\\main\\java\\Data\\response2.json";
        String outputExcelPath = "Data/output.xlsx";
        String outputJsonPath = "Data/output_matched.json";

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Comparison Results");

            // Create the header row for the Excel file
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Old Path");
            headerRow.createCell(1).setCellValue("New Path");
            headerRow.createCell(2).setCellValue("Old Value");
            headerRow.createCell(3).setCellValue("New Value");
            headerRow.createCell(4).setCellValue("Matched/Not Matched");

            // Load the mapping file
            Map<String, String> mapping = readMapping(mappingFilePath);

            // Load the JSON files
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode oldJson = objectMapper.readTree(new File(oldFilePath));
            JsonNode newJson = objectMapper.readTree(new File(newFilePath));

            // Initialize the result JSON node
            ObjectNode resultJson = objectMapper.createObjectNode();

            // Compare and generate results
            int rowIndex = 1; // Start writing data rows after the header

            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                String oldPath = entry.getKey();
                String newPath = entry.getValue();

                // Process paths for both Excel and JSON creation
                String oldValue = findNodeValue(oldJson, oldPath.split("/"), 0, "");
                String newValue = findNodeValue(newJson, newPath.split("/"), 0, "");

                rowIndex = processNode(oldValue, newValue, rowIndex, sheet, resultJson);
            }

            // Write the output JSON file
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputJsonPath), resultJson);

            // Write the Excel file
            try (FileOutputStream fos = new FileOutputStream(outputExcelPath)) {
                workbook.write(fos);
            }

            logger.info("Comparison complete. Output written to: {} and {}", outputExcelPath, outputJsonPath);

        } catch (Exception e) {
            logger.error("An error occurred during comparison", e);
        }
    }

    private static Map<String, String> readMapping(String filePath) throws IOException {
        Map<String, String> mapping = new HashMap<>();
        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean isFirstRow = true; // Flag to skip the header row
            for (Row row : sheet) {
                if (isFirstRow) {
                    isFirstRow = false; // Skip the first row
                    continue;
                }
                if (row.getCell(0) == null || row.getCell(1) == null) {
                    logger.warn("Skipping row {} due to missing cells", row.getRowNum());
                    continue;
                }
                String oldPath = row.getCell(0).getStringCellValue();
                String newPath = row.getCell(1).getStringCellValue();
                mapping.put(oldPath, newPath);
                logger.debug("Mapping added: {} -> {}", oldPath, newPath);
            }
        }
        return mapping;
    }

    private static String findNodeValue(JsonNode jsonNode, String[] keys, int level, String currentPath) {
        if (level >= keys.length) {
            return jsonNode.asText(null);
        }

        String key = keys[level];

        if (key.endsWith("[*]")) {
            String arrayKey = key.substring(0, key.indexOf("[*]"));
            JsonNode arrayNode = jsonNode.path(arrayKey);

            if (arrayNode.isArray() && arrayNode.size() > 0) {
                // Process the first element of the array for simplicity
                return findNodeValue(arrayNode.get(0), keys, level + 1, currentPath + "/" + arrayKey + "[*]");
            } else {
                logger.warn("Expected array but found: {} at path: {}", arrayNode, currentPath);
                return null;
            }
        } else {
            return findNodeValue(jsonNode.path(key), keys, level + 1, currentPath + "/" + key);
        }
    }

    private static int processNode(String oldValue, String newValue, int rowIndex, Sheet sheet, ObjectNode resultJson) {
        String matchStatus = (oldValue != null && oldValue.equals(newValue)) ? "Matched" : "Not Matched";

        // Write to Excel
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(oldValue != null ? oldValue : "null"); // Old Value
        row.createCell(1).setCellValue(newValue != null ? newValue : "null"); // New Value
        row.createCell(2).setCellValue(matchStatus); // Match Status

        // Update result JSON
        resultJson.put("comparison", matchStatus);

        return rowIndex;
    }

}
