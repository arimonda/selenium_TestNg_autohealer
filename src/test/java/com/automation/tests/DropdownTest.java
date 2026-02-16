package com.automation.tests;

import com.automation.base.SeleniumFactory;
import com.automation.pages.DropdownPage;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Functional test for the Dropdown page.
 * Uses correct selectors to verify normal operation alongside the healing tests.
 */
@Slf4j
public class DropdownTest {

    private SeleniumFactory seleniumFactory;
    private DropdownPage dropdownPage;

    @BeforeClass
    public void setUp() {
        seleniumFactory = SeleniumFactory.getInstance();
        dropdownPage = new DropdownPage(seleniumFactory.getDriver());
    }

    @Test(description = "Verify dropdown selection works")
    public void testSelectDropdownOption() {
        dropdownPage.navigateTo("https://the-internet.herokuapp.com/dropdown");

        dropdownPage.selectByVisibleText("Option 1");
        Assert.assertEquals(dropdownPage.getSelectedText(), "Option 1",
                "Dropdown should show 'Option 1'");

        dropdownPage.selectByVisibleText("Option 2");
        Assert.assertEquals(dropdownPage.getSelectedText(), "Option 2",
                "Dropdown should show 'Option 2'");

        log.info("Dropdown test completed successfully");
    }
}
