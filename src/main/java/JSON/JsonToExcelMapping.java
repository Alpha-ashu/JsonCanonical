package JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class JsonToExcelMapping {

    // Method to generate JSON pointers
    public static List<String> generateJsonPointers(Object json, String path) {
        List<String> pointers = new ArrayList<>();
        if (json instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) json;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey().replace("~", "~0").replace("/", "~1");
                String newPath = path + "/" + key;
                pointers.addAll(generateJsonPointers(entry.getValue(), newPath));
            }
        } else if (json instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) json;
            for (int i = 0; i < list.size(); i++) {
                String newPath = path + "/" + i;
                pointers.addAll(generateJsonPointers(list.get(i), newPath));
            }
        } else {
            pointers.add(path); // Leaf node
        }
        return pointers;
    }

    // Method to extract key-value pair as a string using JSON pointer
    public static String getKeyValueFromPointer(Object json, String pointer) {
        String[] keys = pointer.split("/");
        Object current = json;
        String key = "";

        for (String k : keys) {
            if (k.isEmpty()) continue;
            key = k.replace("~1", "/").replace("~0", "~"); // Decode the JSON pointer
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(key);
            } else if (current instanceof List) {
                current = ((List<?>) current).get(Integer.parseInt(key));
            } else {
                return "\"" + key + "\": \"" + current + "\""; // Leaf node with key and value
            }
        }
        return "\"" + key + "\": \"" + current + "\""; // Final Key:Value
    }

    // Method to create Excel file
    public static void createExcel(Map<String, Object> response1, Map<String, Object> response2, String filePath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("JSON Mapping");

        // Header Row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Response 1 (Key:Value)");
        headerRow.createCell(1).setCellValue("Response 1 JSON Pointer");
        headerRow.createCell(2).setCellValue("Response 2 (Key:Value)");
        headerRow.createCell(3).setCellValue("Response 2 JSON Pointer");

        // Generate JSON pointers and values for Response 1 and Response 2
        List<String> response1Pointers = generateJsonPointers(response1, "");
        List<String> response2Pointers = generateJsonPointers(response2, "");

        // Write data to rows
        int rowIndex = 1; // Start from the second row as the first is the header
        for (int i = 0; i < Math.max(response1Pointers.size(), response2Pointers.size()); i++) {
            Row row = sheet.createRow(rowIndex++);

            // Response 1
            if (i < response1Pointers.size()) {
                String pointer = response1Pointers.get(i);
                String keyValue = getKeyValueFromPointer(response1, pointer); // Get Key:Value
                row.createCell(0).setCellValue(keyValue); // Column A: Response 1 Key:Value
                row.createCell(1).setCellValue(pointer); // Column B: Response 1 Pointer
            }

            // Response 2
            if (i < response2Pointers.size()) {
                String pointer = response2Pointers.get(i);
                String keyValue = getKeyValueFromPointer(response2, pointer); // Get Key:Value
                row.createCell(2).setCellValue(keyValue); // Column C: Response 2 Key:Value
                row.createCell(3).setCellValue(pointer); // Column D: Response 2 Pointer
            }
        }

        // Auto-size columns for better readability
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }

        // Write the workbook to a file
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
        }
        workbook.close();
        System.out.println("Excel file successfully created: " + filePath);
    }

    public static void main(String[] args) throws IOException {
        // Read JSON files
        ObjectMapper objectMapper = new ObjectMapper();        
        Map<String, Object> response1 = objectMapper.readValue(new File("C:\\\\Users\\\\nezam\\\\eclipse-workspace\\\\Canocial\\\\src\\\\main\\\\java\\\\Data\\\\response1.json"), Map.class);
        Map<String, Object> response2 = objectMapper.readValue(new File("C:\\\\Users\\\\nezam\\\\eclipse-workspace\\\\Canocial\\\\src\\\\main\\\\java\\\\Data\\\\response2.json"), Map.class);

        // Step 1: Generate Excel
        String excelFilePath = "json_mapping.xlsx";
        createExcel(response1, response2, excelFilePath);
        System.out.println("Excel file generated: " + excelFilePath);
    }
}
