package com.automation.tests;

import com.automation.base.SeleniumFactory;
import com.automation.models.User;
import com.automation.pages.LoginPage;
import com.automation.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Login tests using correct selectors.
 * These tests verify normal operation and serve as a baseline.
 */
@Slf4j
public class LoginTest {
    private SeleniumFactory seleniumFactory;
    private LoginPage loginPage;

    @BeforeClass
    public void setUp() {
        seleniumFactory = SeleniumFactory.getInstance();
        loginPage = new LoginPage(seleniumFactory.getDriver());
    }

    @Test(description = "Valid login using JSON test data")
    public void testValidLogin() {
        User user = JsonUtils.getTestData("login/valid_credentials.json", User.class);
        log.info("Loaded user data: {}", user.getUsername());

        loginPage.navigateTo("https://the-internet.herokuapp.com/login");
        loginPage.enterUsername(user.getUsername());
        loginPage.enterPassword(user.getPassword());
        loginPage.clickLogin();

        String pageSource = seleniumFactory.getDriver().getPageSource();
        Assert.assertTrue(pageSource.contains("secure area") || pageSource.contains("Secure Area"),
                "Should land on the secure area page after valid login");

        log.info("Valid login test completed successfully");
    }

    @Test(description = "Invalid login shows error message")
    public void testInvalidLogin() {
        User user = JsonUtils.getTestData("login/invalid_credentials.json", User.class);
        log.info("Testing invalid login with user: {}", user.getUsername());

        loginPage.navigateTo("https://the-internet.herokuapp.com/login");
        loginPage.enterUsername(user.getUsername());
        loginPage.enterPassword(user.getPassword());
        loginPage.clickLogin();

        String pageSource = seleniumFactory.getDriver().getPageSource();
        Assert.assertTrue(pageSource.contains("invalid") || pageSource.contains("Your username is invalid"),
                "Should show error for invalid credentials");

        log.info("Invalid login test completed successfully");
    }

    @AfterSuite
    public void tearDown() {
        if (seleniumFactory != null) {
            seleniumFactory.close();
        }
    }
}
