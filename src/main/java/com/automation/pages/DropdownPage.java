package com.automation.pages;

import com.automation.base.BasePage;
import com.automation.utils.SmartElement;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.Select;

/**
 * Page Object for {@code https://the-internet.herokuapp.com/dropdown}.
 */
@Slf4j
public class DropdownPage extends BasePage {

    @SeleniumSelector(value = "#dropdown")
    private SmartElement dropdownElement;

    public DropdownPage(WebDriver driver) {
        super(driver);
    }

    public void selectByVisibleText(String text) {
        log.info("Selecting dropdown option: {}", text);
        Select select = new Select(dropdownElement.getElement());
        select.selectByVisibleText(text);
    }

    public void selectByValue(String value) {
        log.info("Selecting dropdown value: {}", value);
        Select select = new Select(dropdownElement.getElement());
        select.selectByValue(value);
    }

    public String getSelectedText() {
        Select select = new Select(dropdownElement.getElement());
        return select.getFirstSelectedOption().getText();
    }
}
