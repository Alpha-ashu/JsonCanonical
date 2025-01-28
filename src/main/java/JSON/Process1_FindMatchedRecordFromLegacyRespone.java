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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Process1_FindMatchedRecordFromLegacyRespone {

    private static final Logger logger = LoggerFactory.getLogger(Process1_FindMatchedRecordFromLegacyRespone.class);

    public static void main(String[] args) {
        String mappingFilePath = "C:\\Users\\nezam\\eclipse-workspace\\Canocial\\src\\main\\java\\Data\\mappingForFilteringFiles.xlsx";
        String legacyFilePath = "C:\\Users\\nezam\\eclipse-workspace\\Canocial\\src\\main\\java\\Data\\response1.json";
        String payerFilePath = "C:\\Users\\nezam\\eclipse-workspace\\Canocial\\src\\main\\java\\Data\\response2.json";

        try {
            // Load the mapping file
            Map<String, String> mapping = readMapping(mappingFilePath);

            // Load JSON files
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode legacyResponse = objectMapper.readTree(new File(legacyFilePath));
            JsonNode payerResponse = objectMapper.readTree(new File(payerFilePath));

            // Extract nodes to compare
            JsonNode legacyResponseArray = legacyResponse.get("searchResult").get("searchOutput").get("claims");
            JsonNode payerResponseArray = payerResponse.get("data");

            if (!legacyResponseArray.isArray() || !payerResponseArray.isArray()) {
                logger.error("Expected both responses to contain arrays");
                return;
            }

            // Compare and create output files
            for (JsonNode legacyRecord : legacyResponseArray) {
                for (JsonNode payerRecord : payerResponseArray) {
                    Status status = compareUsingMapping(legacyRecord, payerRecord, mapping);
                    if (status.StatusCode.equals("MATCHED")) {
                        // Create output files for matched pairs
                        System.out.println(legacyRecord);
                        System.out.println(payerRecord);

                        // Adjust the loop to wrap the JSON arrays before creating files
                        ArrayNode wrappedResponse1Array = objectMapper.createArrayNode();
                        ArrayNode wrappedResponse2Array = objectMapper.createArrayNode();

                        wrappedResponse1Array.add(legacyRecord);
                        wrappedResponse2Array.add(payerRecord);


                        // Create the final JSON structure for response1
                        ObjectNode finalResponse1 = objectMapper.createObjectNode();
                        finalResponse1.set("searchResult", objectMapper.createObjectNode()
                                .set("searchOutput", objectMapper.createObjectNode()
                                        .set("claims", wrappedResponse1Array)));

                        // Create the final JSON structure for response2
                        ObjectNode finalResponse2 = objectMapper.createObjectNode();
                        finalResponse2.set("data", wrappedResponse2Array);

                        // Assuming status.Payer contains the payer ID
                        String payerId = status.Payer;

                        // Create output files
                        // Create the two files in the payer-specific directory
                        createJsonFile(payerId, "Legacy_" + payerId + ".json", finalResponse1, objectMapper);
                        createJsonFile(payerId, "Payer_" + payerId + ".json", finalResponse2, objectMapper);

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
                    logger.warn("Skipping row {} due to missing cells");
                    continue;
                }
                String key = row.getCell(0).getStringCellValue();
                String value = row.getCell(1).getStringCellValue();
                mapping.put(key, value);
            }
        }
        return mapping;
    }

    private static Status compareUsingMapping(JsonNode response1Document, JsonNode response2Document, Map<String, String> mapping) {
        Status status = new Status();
        int matchedCount = 0;
        int totalKeys = mapping.size();
        String payerValue = null;

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String oldPathKey = entry.getKey();
            String newPathKey = entry.getValue();

            // Fetch values from both JSON documents
            String oldValue = findNodeValue(response1Document, oldPathKey.split("/"), 0);
            String newValue = findNodeValue(response2Document, newPathKey.split("/"), 0);

            // Check for matching values
            if (oldValue != null && oldValue.equals(newValue)) {
                matchedCount++;
                // Fetch the "Payer" value if it exists in the mapping
                if ("claimIdentifiers/patientAccountNumber".equals(newPathKey)) {
                    payerValue = (oldValue != null) ? oldValue : newValue;
                }
            }
        }

        // Set the status properties based on the comparison
        status.Length = totalKeys;
        if (matchedCount == totalKeys) {
            status.StatusCode = "MATCHED";
        } else {
            status.StatusCode = "PARTIAL_MATCH (" + matchedCount + "/" + totalKeys + ")";
        }

        // Add payerValue to the Status object
        status.Payer = payerValue;

        return status;
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
    private static void createJsonFile(String payerId, String fileName, JsonNode jsonContent, ObjectMapper objectMapper) {
        // Define the base directory
        String baseDirectory = "target/filteredRecord/";

        // Define the payer-specific directory
        String payerDirectory = baseDirectory + payerId + "/";

        // Ensure the payer directory exists
        File directory = new File(payerDirectory);
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                logger.info("Directory created: {}", payerDirectory);
            } else {
                logger.error("Failed to create directory: {}", payerDirectory);
                return;
            }
        }

        // Create the full file path
        File outputFile = new File(payerDirectory + fileName);

        // Write the JSON content to the file
        try (FileWriter fileWriter = new FileWriter(outputFile)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(fileWriter, jsonContent);
            logger.info("File created: {}", outputFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to create file {}: {}", outputFile.getAbsolutePath(), e.getMessage());
        }
    }

}


