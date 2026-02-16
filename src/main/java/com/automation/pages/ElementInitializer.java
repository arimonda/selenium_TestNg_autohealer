package com.automation.pages;

import com.automation.base.BasePage;
import com.automation.utils.SmartElement;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.Field;

@Slf4j
public class ElementInitializer {
    public static void initElements(WebDriver driver, Object pageObject) {
        Class<?> clazz = pageObject.getClass();
        Field[] fields = clazz.getDeclaredFields();
        BasePage basePage = pageObject instanceof BasePage ? (BasePage) pageObject : null;

        for (Field field : fields) {
            if (field.isAnnotationPresent(SeleniumSelector.class)) {
                SeleniumSelector annotation = field.getAnnotation(SeleniumSelector.class);
                String selector = annotation.value();
                By by = toBy(selector);

                try {
                    field.setAccessible(true);
                    SmartElement element = basePage != null 
                        ? new SmartElement(driver, by, selector, basePage, clazz, field.getName())
                        : new SmartElement(driver, by, selector);
                    field.set(pageObject, element);
                    log.debug("Initialized element: {} with selector: {}", field.getName(), selector);
                } catch (IllegalAccessException e) {
                    log.error("Failed to initialize element: {}", field.getName(), e);
                    throw new RuntimeException("Failed to initialize page element", e);
                }
            }
        }
    }

    private static By toBy(String selector) {
        if (selector == null) {
            throw new IllegalArgumentException("Selector cannot be null");
        }
        String trimmed = selector.trim();
        if (trimmed.startsWith("xpath=")) {
            return By.xpath(trimmed.substring("xpath=".length()));
        }
        return By.cssSelector(trimmed);
    }
}

