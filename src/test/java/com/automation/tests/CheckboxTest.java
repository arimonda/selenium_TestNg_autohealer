package com.automation.tests;

import com.automation.base.SeleniumFactory;
import com.automation.pages.CheckboxPage;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Functional test for the Checkboxes page.
 * Uses correct selectors to verify normal operation alongside the healing tests.
 */
@Slf4j
public class CheckboxTest {

    private SeleniumFactory seleniumFactory;
    private CheckboxPage checkboxPage;

    @BeforeClass
    public void setUp() {
        seleniumFactory = SeleniumFactory.getInstance();
        checkboxPage = new CheckboxPage(seleniumFactory.getDriver());
    }

    @Test(description = "Verify checkboxes can be toggled")
    public void testToggleCheckboxes() {
        checkboxPage.navigateTo("https://the-internet.herokuapp.com/checkboxes");

        boolean initialState = checkboxPage.isChecked(0);
        log.info("Checkbox 1 initial state: {}", initialState);

        checkboxPage.toggleCheckbox(0);
        Assert.assertNotEquals(checkboxPage.isChecked(0), initialState,
                "Checkbox 1 should have toggled");

        log.info("Checkbox toggle test completed successfully");
    }
}
