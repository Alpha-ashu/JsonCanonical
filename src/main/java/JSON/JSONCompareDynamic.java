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

public class JSONCompareDynamic {

    private static final Logger logger = LoggerFactory.getLogger(JSONCompareDynamic.class);

    public static void main(String[] args) {
        String mappingFilePath = "C:\\Users\\nezam\\eclipse-workspace\\Canocial\\src\\main\\java\\Data\\mapping.xlsx";
        String oldFilePath = "C:\\Users\\nezam\\eclipse-workspace\\Canocial\\src\\main\\java\\Data\\document_20250122_160011.json";
        String newFilePath = "C:\\Users\\nezam\\eclipse-workspace\\Canocial\\src\\main\\java\\Data\\response2_20250122_161616.json";
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
                rowIndex = processPaths(oldJson, newJson, oldPath, newPath, sheet, rowIndex, resultJson);
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

    private static int processPaths(JsonNode oldJson, JsonNode newJson, String oldPath, String newPath, Sheet sheet, int rowIndex, ObjectNode resultJson) {
        String[] oldKeys = oldPath.split("/");
        String[] newKeys = newPath.split("/");

        String oldValue = findNodeValue(oldJson, oldKeys, 0, "");
        String newValue = findNodeValue(newJson, newKeys, 0, "");
        return processNode(oldValue, newValue, oldPath, newPath, rowIndex, sheet, resultJson);
    }
    
    private static String findNodeValue(JsonNode jsonContent, String[] keys, int level, String currentPath) {
        if (level == keys.length) {
            return jsonContent.asText(null);
        }

        String key = keys[level];
        if (key.isEmpty()) {
            return findNodeValue(jsonContent, keys, level + 1, currentPath);
        }

        if (key.endsWith("[*]")) {
            String arrayKey = key.substring(0, key.indexOf("[*]"));
            JsonNode arrayNode = jsonContent.path(arrayKey);

            if (arrayNode.isArray()) {
                Iterator<JsonNode> elements = arrayNode.elements();
                if (elements.hasNext()) {
                    // For simplicity, process the first element of the array
                    return findNodeValue(elements.next(), keys, level + 1, currentPath + "/" + arrayKey + "[*]");
                } else {
                    logger.warn("Array at key '{}' is empty", arrayKey);
                    return null;
                }
            } else {
                logger.warn("Expected array at key '{}' but found: {}", arrayKey, arrayNode);
                return null;
            }
        } else {
            return findNodeValue(jsonContent.path(key), keys, level + 1, currentPath + "/" + key);
        }
    }
    
    private static int processNode(String oldValue, String newValue, String oldPath, String newPath, int rowIndex, Sheet sheet, ObjectNode resultJson) {
        String matchStatus = (oldValue != null && oldValue.equals(newValue)) ? "Matched" : "Not Matched";

        // Write to Excel
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(oldPath); // Old Path
        row.createCell(1).setCellValue(newPath); // New Path
        row.createCell(2).setCellValue(oldValue != null ? oldValue : "null"); // Old Value
        row.createCell(3).setCellValue(newValue != null ? newValue : "null"); // New Value
        row.createCell(4).setCellValue(matchStatus); // Match Status

        // Update result JSON
        updateJsonResult(resultJson, newPath.split("/"), newValue, oldValue);

        return rowIndex;
    }

    /*private static int processNode(JsonNode oldNode, JsonNode newNode, String[] oldKeys, String[] newKeys, int level, String currentPath, Sheet sheet, int rowIndex, ObjectNode resultJson) {
        if (level == oldKeys.length) {
            rowIndex = createRecord(oldNode, newNode, currentPath, sheet, rowIndex);
            updateJsonResult(resultJson, newKeys, newNode, oldNode);
            return rowIndex;
        }

        String oldKey = oldKeys[level];
        String newKey = newKeys[level];

        if (oldKey.isEmpty() || newKey.isEmpty()) {
            return processNode(oldNode, newNode, oldKeys, newKeys, level + 1, currentPath, sheet, rowIndex, resultJson);
        }

        if (oldKey.endsWith("[*]") && newKey.endsWith("[*]")) {
            String oldArrayKey = oldKey.substring(0, oldKey.indexOf("[*]"));
            String newArrayKey = newKey.substring(0, newKey.indexOf("[*]"));

            JsonNode oldArrayNode = oldNode.path(oldArrayKey);
            JsonNode newArrayNode = newNode.path(newArrayKey);

            if (oldArrayNode.isArray() && newArrayNode.isArray()) {
                Iterator<JsonNode> oldElements = oldArrayNode.elements();
                Iterator<JsonNode> newElements = newArrayNode.elements();
                int index = 0;

                while (oldElements.hasNext() && newElements.hasNext()) {
                    rowIndex = processNode(oldElements.next(), newElements.next(), oldKeys, newKeys, level + 1, currentPath + "/" + oldArrayKey + "[" + index + "]", sheet, rowIndex, resultJson);
                    index++;
                }
            } else {
                logger.warn("Expected arrays at paths: {} and {}", oldArrayKey, newArrayKey);
            }
        } else {
            rowIndex = processNode(oldNode.path(oldKey), newNode.path(newKey), oldKeys, newKeys, level + 1, currentPath + "/" + oldKey, sheet, rowIndex, resultJson);
        }
        return rowIndex;
    }*/

    private static void updateJsonResult(ObjectNode resultJson, String[] newKeys, String newValue, String oldValue) {
        ObjectNode currentNode = resultJson;

        for (int i = 0; i < newKeys.length - 1; i++) {
            String key = newKeys[i];

            if (key.endsWith("[*]")) {
                // Handle array elements
                String arrayKey = key.substring(0, key.indexOf("[*]"));
                ArrayNode arrayNode = (ArrayNode) currentNode.withArray(arrayKey);

                // Add placeholder object if array is empty
                if (arrayNode.size() == 0) {
                    arrayNode.addObject();
                }
                currentNode = (ObjectNode) arrayNode.get(0); // Use first array element for simplicity
            } else {
                currentNode = currentNode.with(key);
            }
        }

        String key = newKeys[newKeys.length - 1];
        String resultValue = (oldValue != null && oldValue.equals(newValue)) ? newValue : "Value not matched";
        currentNode.put(key, resultValue);
    }
    
   /* private static int createRecord(JsonNode oldNode, JsonNode newNode, String currentPath, Sheet sheet, int rowIndex) {
        String oldValue = oldNode.asText(null);
        String newValue = newNode.asText(null);

        String matchStatus = (oldValue != null && oldValue.equals(newValue)) ? "Matched" : "Not Matched";

        // Write to Excel
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(currentPath); // Old Path
        row.createCell(1).setCellValue(currentPath); // New Path
        row.createCell(2).setCellValue(oldValue != null ? oldValue : "null"); // Old Path Value
        row.createCell(3).setCellValue(newValue != null ? newValue : "null"); // New Path Value
        row.createCell(4).setCellValue(matchStatus); // Match Status

        return rowIndex;
    }*/

    /*private static void updateJsonResult(ObjectNode resultJson, String[] newKeys, JsonNode newValueNode, JsonNode oldValueNode) {
        ObjectNode currentNode = resultJson;

        for (int i = 0; i < newKeys.length - 1; i++) {
            String key = newKeys[i];

            if (key.endsWith("[*]")) {
                // Handle array elements
                String arrayKey = key.substring(0, key.indexOf("[*]"));
                ArrayNode arrayNode = (ArrayNode) currentNode.withArray(arrayKey);

                // Add placeholder object if array is empty
                if (arrayNode.size() == 0) {
                    arrayNode.addObject();
                }
                currentNode = (ObjectNode) arrayNode.get(0); // Use first array element for simplicity
            } else {
                currentNode = currentNode.with(key);
            }
        }

        String key = newKeys[newKeys.length - 1];
        String newValue = newValueNode.asText(null);
        String oldValue = oldValueNode != null ? oldValueNode.asText(null) : null;

        String resultValue = (oldValue != null && oldValue.equals(newValue)) ? newValue : "Value not matched";
        currentNode.put(key, resultValue);
    }*/

}
