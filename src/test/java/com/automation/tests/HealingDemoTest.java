package com.automation.tests;

import com.automation.base.SeleniumFactory;
import com.automation.pages.HealingDemoLoginPage;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Demonstrates the AI Self-Healing engine in action.
 * <p>
 * This test uses {@link HealingDemoLoginPage} which contains <b>intentionally broken</b>
 * selectors. The AI engine (Gemini) will:
 * <ol>
 *   <li>Detect the broken selectors at runtime</li>
 *   <li>Capture page HTML + screenshot</li>
 *   <li>Consult Gemini for replacement selectors</li>
 *   <li>Validate each suggestion sequentially</li>
 *   <li>Auto-update the source code with the working selector</li>
 * </ol>
 * <p>
 * Target site: {@code https://the-internet.herokuapp.com/login}
 */
@Slf4j
public class HealingDemoTest {

    private SeleniumFactory seleniumFactory;
    private HealingDemoLoginPage loginPage;

    @BeforeClass
    public void setUp() {
        seleniumFactory = SeleniumFactory.getInstance();
        loginPage = new HealingDemoLoginPage(seleniumFactory.getDriver());
    }

    /**
     * All three selectors in HealingDemoLoginPage are broken:
     * <ul>
     *   <li>{@code input[data-qa='user-login-field']} - does not exist</li>
     *   <li>{@code #pass-field-old} - does not exist</li>
     *   <li>{@code button.btn-primary-login} - does not exist</li>
     * </ul>
     * The AI should heal each one on the fly.
     */
    @Test(description = "Demonstrates AI healing of 3 broken selectors during login flow")
    public void testLoginWithBrokenSelectors() {
        log.info("=== HEALING DEMO: Starting login with intentionally broken selectors ===");

        loginPage.navigateTo("https://the-internet.herokuapp.com/login");

        // Each of these will fail initially, trigger Gemini, and heal
        loginPage.enterUsername("tomsmith");
        loginPage.enterPassword("SuperSecretPassword!");
        loginPage.clickLogin();

        // If we get here, all 3 selectors were healed successfully
        String pageSource = seleniumFactory.getDriver().getPageSource();
        Assert.assertTrue(pageSource.contains("secure area") || pageSource.contains("Secure Area"),
                "Expected to land on the secure area page after healed login");

        log.info("=== HEALING DEMO: All 3 broken selectors were healed successfully! ===");
    }

    /**
     * Uses {@code smartFill/smartClick} with broken inline selectors (not from annotations).
     */
    @Test(description = "Demonstrates AI healing with inline broken selectors")
    public void testSmartActionsWithBrokenSelectors() {
        log.info("=== HEALING DEMO: Starting smart actions with broken inline selectors ===");

        loginPage.navigateTo("https://the-internet.herokuapp.com/login");

        // These selectors are wrong - AI will heal them
        loginPage.smartFill("input#login-username-field", "tomsmith");
        loginPage.smartFill("input.password-input-old", "SuperSecretPassword!");
        loginPage.smartClick("button#submit-login-btn");

        String pageSource = seleniumFactory.getDriver().getPageSource();
        Assert.assertTrue(pageSource.contains("secure area") || pageSource.contains("Secure Area"),
                "Expected to land on the secure area after healed login");

        log.info("=== HEALING DEMO: Inline broken selectors were healed successfully! ===");
    }

    @AfterClass
    public void tearDown() {
        // Don't close here - other tests may still need the driver
    }
}
