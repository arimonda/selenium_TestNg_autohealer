package com.automation.utils;

import com.automation.base.BasePage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

@Slf4j
@Getter
public class SmartElement {
    private final WebDriver driver;
    private final By by;
    private String selector;
    private final BasePage basePage;
    private final Class<?> ownerPageClass;
    private final String fieldName;

    public SmartElement(WebDriver driver, By by, String selector) {
        this.driver = driver;
        this.by = by;
        this.selector = selector;
        this.basePage = null;
        this.ownerPageClass = null;
        this.fieldName = null;
    }

    public SmartElement(WebDriver driver, By by, String selector, BasePage basePage) {
        this.driver = driver;
        this.by = by;
        this.selector = selector;
        this.basePage = basePage;
        this.ownerPageClass = null;
        this.fieldName = null;
    }

    public SmartElement(WebDriver driver, By by, String selector, BasePage basePage, Class<?> ownerPageClass, String fieldName) {
        this.driver = driver;
        this.by = by;
        this.selector = selector;
        this.basePage = basePage;
        this.ownerPageClass = ownerPageClass;
        this.fieldName = fieldName;
    }

    public WebElement getElement() {
        return driver.findElement(by);
    }

    public void click() {
        if (basePage != null) {
            log.info("Using smart click for element with selector: {}", selector);
            basePage.smartClick(this);
        } else {
            log.info("Clicking element with selector: {}", selector);
            getElement().click();
        }
    }

    public void fill(String value) {
        if (basePage != null) {
            log.info("Using smart fill for element with selector: {} with value: {}", selector, value);
            basePage.smartFill(this, value);
        } else {
            log.info("Filling element with selector: {} with value: {}", selector, value);
            WebElement el = getElement();
            el.clear();
            el.sendKeys(value);
        }
    }

    public String getText() {
        return getElement().getText();
    }

    public boolean isVisible() {
        try {
            return getElement().isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    public void updateSelector(String newSelector) {
        if (newSelector != null && !newSelector.isBlank()) {
            this.selector = newSelector.trim();
        }
    }
}

