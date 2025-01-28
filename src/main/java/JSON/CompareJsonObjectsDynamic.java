package JSON;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//Import necessary classes
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CompareJsonObjectsDynamic {

    private static final Logger logger = LoggerFactory.getLogger(CompareJsonObjectsDynamic.class);

    public static void main(String[] args) {
        String mappingFilePath = "C:\\Users\\nezam\\eclipse-workspace\\Canocial\\src\\main\\java\\Data\\mappingForFilteringFiles.xlsx";        
        String response1FilePath = "C:\\Users\\nezam\\eclipse-workspace\\Canocial\\src\\main\\java\\Data\\response1.json";
        String response2FilePath = "C:\\Users\\nezam\\eclipse-workspace\\Canocial\\src\\main\\java\\Data\\response2.json";

        try {
            // Load the mapping file
            Map<String, String> mapping = readMapping(mappingFilePath);

            // Load JSON files
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode response1Root = objectMapper.readTree(new File(response1FilePath));
            JsonNode response2Root = objectMapper.readTree(new File(response2FilePath));

         // Extract nodes to compare
            JsonNode response1Array = response1Root.get("searchResult").get("searchOutput").get("claims");
            JsonNode response2Array = response2Root.get("data");

            if (!response1Array.isArray() || !response2Array.isArray()) {
                logger.error("Expected both responses to contain arrays");
                return;
            }

            // Compare and create output files
            for (JsonNode response1Document : response1Array) {
                for (JsonNode response2Document : response2Array) {
                    if (compareUsingMapping(response1Document, response2Document, mapping)) {
                        // Create output files for matched pairs
                    	System.out.println(response1Document);
                    	System.out.println(response2Document);
                    	// Get current DateTime for naming
                        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

                     // Adjust the loop to wrap the JSON arrays before creating files
                        ArrayNode wrappedResponse1Array = objectMapper.createArrayNode();
                        ArrayNode wrappedResponse2Array = objectMapper.createArrayNode();
                        
                        wrappedResponse1Array.add(response1Document);
                        wrappedResponse2Array.add(response2Document);                        


                        // Create the final JSON structure for response1
                        ObjectNode finalResponse1 = objectMapper.createObjectNode();
                        finalResponse1.set("searchResult", objectMapper.createObjectNode()
                            .set("searchOutput", objectMapper.createObjectNode()
                                .set("claims", wrappedResponse1Array)));

                        // Create the final JSON structure for response2
                        ObjectNode finalResponse2 = objectMapper.createObjectNode();
                        finalResponse2.set("data", wrappedResponse2Array);

                        // Create output files
                        createJsonFile("response1_" + dateTime + ".json", finalResponse1, objectMapper);
                        createJsonFile("response2_" + dateTime + ".json", finalResponse2, objectMapper);
                        
                        //logger.info("Match found: Document ID {} with User ID {}", response1Document.get("claimNumber").asText(), response2Document.get("payerClaimControlNumber").asText());
                    }
                }
            }

        } catch (IOException e) {
            logger.error("An error occurred while processing", e);
        }
    }

    private static Map<String, String> readMapping(String filePath) throws IOException {
        Map<String, String> mapping = new HashMap<>();
        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean isFirstRow = true; // Skip header row
            for (Row row : sheet) {
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }
                if (row.getCell(0) == null || row.getCell(1) == null) {
                    logger.warn("Skipping row {} due to missing cells", row.getRowNum());
                    continue;
                }
                String key = row.getCell(0).getStringCellValue();
                String value = row.getCell(1).getStringCellValue();
                mapping.put(key, value);
            }
        }
        return mapping;
    }

    private static boolean compareUsingMapping(JsonNode response1Document, JsonNode response2Document, Map<String, String> mapping) {
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String oldPathKey = entry.getKey();
            String newPathKey = entry.getValue();

            String oldValue = findNodeValue(response1Document, oldPathKey.split("/"), 0);
            String newValue = findNodeValue(response2Document, newPathKey.split("/"), 0);

            if (oldValue != null && oldValue.equals(newValue)) {
                return true;
            }
        }
        return false;
    }

    private static String findNodeValue(JsonNode jsonNode, String[] keys, int level) {
        if (level >= keys.length) {
            return jsonNode.isValueNode() ? jsonNode.asText(null) : null;
        }

        String key = keys[level];

        if (key.endsWith("[*]")) {
            String arrayKey = key.substring(0, key.indexOf("[*]"));
            JsonNode arrayNode = jsonNode.path(arrayKey);

            if (arrayNode.isArray() && arrayNode.size() > 0) {
                // For simplicity, check the first element of the array
                return findNodeValue(arrayNode.get(0), keys, level + 1);
            } else {
                logger.warn("Expected array at key '{}' but found: {}", arrayKey, arrayNode);
                return null;
            }
        } else {
            return findNodeValue(jsonNode.path(key), keys, level + 1);
        }
    }

    private static void createJsonFile(String fileName, JsonNode jsonContent, ObjectMapper objectMapper) {
        try (FileWriter fileWriter = new FileWriter(fileName)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(fileWriter, jsonContent);
            logger.info("File created: {}", fileName);
        } catch (IOException e) {
            logger.error("Failed to create file {}: {}", fileName, e.getMessage());
        }
    }
}
