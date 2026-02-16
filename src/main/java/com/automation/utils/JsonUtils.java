package com.automation.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class JsonUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static <T> T getTestData(String fileName, Class<T> clazz) {
        try {
            String filePath = "src/test/resources/data/" + fileName;
            File file = new File(filePath);
            
            if (!file.exists()) {
                // Try loading from classpath
                InputStream inputStream = JsonUtils.class.getClassLoader()
                    .getResourceAsStream("data/" + fileName);
                if (inputStream != null) {
                    T result = objectMapper.readValue(inputStream, clazz);
                    log.info("Loaded test data from classpath: {}", fileName);
                    return result;
                }
                throw new IOException("File not found: " + filePath);
            }
            
            T result = objectMapper.readValue(file, clazz);
            log.info("Loaded test data from file: {}", filePath);
            return result;
        } catch (IOException e) {
            log.error("Failed to load test data from file: {}", fileName, e);
            throw new RuntimeException("Failed to load test data", e);
        }
    }
}

