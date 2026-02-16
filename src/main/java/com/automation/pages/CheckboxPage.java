package com.automation.pages;

import com.automation.base.BasePage;
import com.automation.utils.SmartElement;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * Page Object for {@code https://the-internet.herokuapp.com/checkboxes}.
 */
@Slf4j
public class CheckboxPage extends BasePage {

    @SeleniumSelector(value = "#checkboxes")
    private SmartElement checkboxContainer;

    public CheckboxPage(WebDriver driver) {
        super(driver);
    }

    public List<WebElement> getCheckboxes() {
        return driver.findElements(By.cssSelector("#checkboxes input[type='checkbox']"));
    }

    public boolean isChecked(int index) {
        List<WebElement> boxes = getCheckboxes();
        if (index >= 0 && index < boxes.size()) {
            return boxes.get(index).isSelected();
        }
        return false;
    }

    public void toggleCheckbox(int index) {
        List<WebElement> boxes = getCheckboxes();
        if (index >= 0 && index < boxes.size()) {
            log.info("Toggling checkbox at index {}", index);
            boxes.get(index).click();
        }
    }
}
