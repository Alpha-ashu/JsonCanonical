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

public class Process2_CompareLegacyAndPayerPackage {

    private static final Logger logger = LoggerFactory.getLogger(Process2_CompareLegacyAndPayerPackage.class);

    public static void main(String[] args) {
        String baseDirectory = "C:\\Users\\nezam\\source\\repos\\JsonCanonical\\target\\filteredRecord";
        String mappingFilePath = "C:\\Users\\nezam\\source\\repos\\JsonCanonical\\src\\main\\java\\Data\\mapping.xlsx";
        String outputExcelPath = "Data/output.xlsx";
        String outputJsonPath = "Data/output_matched.json";

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Comparison Results");

            // Create the header row for the Excel file
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Folder Name");
            headerRow.createCell(1).setCellValue("Old Path");
            headerRow.createCell(2).setCellValue("New Path");
            headerRow.createCell(3).setCellValue("Old Value");
            headerRow.createCell(4).setCellValue("New Value");
            headerRow.createCell(5).setCellValue("Matched/Not Matched");

            // Load the mapping file
            Map<String, String> mapping = readMapping(mappingFilePath);

            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode aggregatedJsonResults = objectMapper.createObjectNode();
            int rowIndex = 1; // Start writing data rows after the header

            // Get all subdirectories in the base directory
            File baseDir = new File(baseDirectory);
            File[] subDirectories = baseDir.listFiles(File::isDirectory);

            if (subDirectories != null) {
                for (File folder : subDirectories) {
                    String folderName = folder.getName();
                    File legacyFile = new File(folder, "Legacy_" + folderName + ".json");
                    File payerFile = new File(folder, "Payer_" + folderName + ".json");

                    // Ensure both files exist
                    if (legacyFile.exists() && payerFile.exists()) {
                        JsonNode legacyJson = objectMapper.readTree(legacyFile);
                        JsonNode payerJson = objectMapper.readTree(payerFile);

                        ObjectNode folderResultJson = objectMapper.createObjectNode();

                        // Compare files using the mapping
                        for (Map.Entry<String, String> entry : mapping.entrySet()) {
                            String oldPath = entry.getKey();
                            String newPath = entry.getValue();

                            rowIndex = processPaths(legacyJson, payerJson, oldPath, newPath, sheet, rowIndex, folderResultJson);
                        }

                        // Add results for the current folder to the aggregated JSON
                        aggregatedJsonResults.set(folderName, folderResultJson);
                    } else {
                        logger.warn("Skipping folder '{}' as required files are missing.", folderName);
                    }
                }
            }

            // Write aggregated JSON results to the output file
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputJsonPath), aggregatedJsonResults);

            // Write Excel results to the output file
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
                    logger.warn("Skipping row {} due to missing cells", Integer.valueOf(row.getRowNum()));
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
        String matchStatus;

        // Determine match status
        if (oldValue != null && oldValue.equals(newValue)) {
            matchStatus = "Matched";
        } else if (oldValue != null && newValue != null &&
                (oldValue.toLowerCase().contains(newValue.toLowerCase()) ||
                        newValue.toLowerCase().contains(oldValue.toLowerCase()))) {
            matchStatus = "Partial Match";
        }
        else {
            matchStatus = "Not Matched";
        }

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

        // Determine match status
        String matchStatus;
        if (oldValue != null && oldValue.equals(newValue)) {
            matchStatus = "Matched";
        } else if (oldValue != null && newValue != null &&
                (oldValue.toLowerCase().contains(newValue.toLowerCase()) ||
                        newValue.toLowerCase().contains(oldValue.toLowerCase()))) {
            matchStatus = "Partial Match";
        }
        else {
            matchStatus = "Not Matched";
        }

        // Add detailed result to the JSON
        ObjectNode valueNode = currentNode.putObject(key);
        valueNode.put("OldValue", oldValue != null ? oldValue : "null");
        valueNode.put("NewValue", newValue != null ? newValue : "null");
        valueNode.put("Status", matchStatus);
    }
}
