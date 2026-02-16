package com.automation.pages;

import com.automation.base.BasePage;
import com.automation.utils.SmartElement;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

/**
 * Login Page Object with INTENTIONALLY BROKEN selectors.
 * <p>
 * These selectors do NOT match any element on {@code https://the-internet.herokuapp.com/login}.
 * The AI Self-Healing engine will detect the failure, consult Gemini, and replace them
 * with working selectors at runtime.
 * <p>
 * Correct selectors for reference:
 * <ul>
 *   <li>username: {@code #username}</li>
 *   <li>password: {@code #password}</li>
 *   <li>login button: {@code button[type='submit']} or {@code .fa-sign-in}</li>
 * </ul>
 */
@Slf4j
public class HealingDemoLoginPage extends BasePage {

    // BROKEN: This ID does not exist on the page
    @SeleniumSelector(value = "input[data-qa='user-login-field']")
    private SmartElement usernameField;

    // BROKEN: This ID does not exist on the page
    @SeleniumSelector(value = "#pass-field-old")
    private SmartElement passwordField;

    // BROKEN: This class does not exist on the page
    @SeleniumSelector(value = "button.btn-primary-login")
    private SmartElement loginButton;

    public HealingDemoLoginPage(WebDriver driver) {
        super(driver);
    }

    public void enterUsername(String username) {
        log.info("[HEALING-DEMO] Entering username (using broken selector - healing expected)");
        usernameField.fill(username);
    }

    public void enterPassword(String password) {
        log.info("[HEALING-DEMO] Entering password (using broken selector - healing expected)");
        passwordField.fill(password);
    }

    public void clickLogin() {
        log.info("[HEALING-DEMO] Clicking login (using broken selector - healing expected)");
        loginButton.click();
    }

    public void login(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLogin();
    }
}
