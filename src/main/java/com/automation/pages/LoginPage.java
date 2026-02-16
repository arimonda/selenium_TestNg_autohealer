package com.automation.pages;

import com.automation.base.BasePage;
import com.automation.utils.SmartElement;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class LoginPage extends BasePage {
    
    @SeleniumSelector(value = "input[name='username']")
    private SmartElement usernameField;
    
    @SeleniumSelector(value = "input[name='password']")
    private SmartElement passwordField;
    
    @SeleniumSelector(value = "button[type='submit']")
    private SmartElement loginButton;
    
    public LoginPage(WebDriver driver) {
        super(driver);
    }
    
    public void enterUsername(String username) {
        log.info("Entering username: {}", username);
        usernameField.fill(username);
    }
    
    public void enterPassword(String password) {
        log.info("Entering password");
        passwordField.fill(password);
    }
    
    public void clickLogin() {
        log.info("Clicking login button");
        loginButton.click();
    }
    
    public void login(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLogin();
    }
}

