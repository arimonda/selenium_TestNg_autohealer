package com.automation.base;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

@Slf4j
@Getter
public class SeleniumFactory {
    private static SeleniumFactory instance;

    private WebDriver driver;
    private Properties config;

    private SeleniumFactory() {
        loadConfig();
        initializeDriver();
    }

    public static synchronized SeleniumFactory getInstance() {
        if (instance == null) {
            instance = new SeleniumFactory();
        }
        return instance;
    }

    private void loadConfig() {
        config = new Properties();
        try (FileInputStream fis = new FileInputStream("src/main/resources/config.properties")) {
            config.load(fis);
            log.info("Configuration loaded successfully");
        } catch (IOException e) {
            log.error("Failed to load config.properties", e);
            throw new RuntimeException("Configuration file not found", e);
        }
    }

    private void initializeDriver() {
        String browserType = config.getProperty("browser.type", "chrome").toLowerCase();
        boolean headless = Boolean.parseBoolean(config.getProperty("browser.headless", "true"));

        switch (browserType) {
            case "firefox":
                driver = createFirefoxDriver(headless);
                break;
            case "edge":
                driver = createEdgeDriver(headless);
                break;
            case "chromium":
            case "chrome":
            default:
                driver = createChromeDriver(headless);
                break;
        }

        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        driver.manage().window().maximize();
        log.info("Driver initialized: {} (headless: {})", browserType, headless);
    }

    private WebDriver createChromeDriver(boolean headless) {
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        return new ChromeDriver(options);
    }

    private WebDriver createFirefoxDriver(boolean headless) {
        FirefoxOptions options = new FirefoxOptions();
        if (headless) {
            options.addArguments("-headless");
        }
        return new FirefoxDriver(options);
    }

    private WebDriver createEdgeDriver(boolean headless) {
        EdgeOptions options = new EdgeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        return new EdgeDriver(options);
    }

    public WebDriver getDriver() {
        if (driver == null) {
            initializeDriver();
        }
        return driver;
    }

    public synchronized void close() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                log.warn("Error closing WebDriver", e);
            } finally {
                driver = null;
                instance = null;
            }
        }
        log.info("Selenium WebDriver closed");
    }
}
