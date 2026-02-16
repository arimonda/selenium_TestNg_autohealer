package com.automation.listeners;

import com.automation.config.HealingConfig;
import lombok.extern.slf4j.Slf4j;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * TestNG {@link IRetryAnalyzer} that allows one automatic retry for tests that fail
 * due to locator issues, giving the inline healing in {@code BasePage} a second chance.
 * <p>
 * Combined with the {@link AiHealingListener}, this creates a two-layer safety net:
 * <ol>
 *   <li>First attempt: BasePage inline healing during execution</li>
 *   <li>If the test still fails: RetryAnalyzer triggers one more execution so
 *       cached healing results (from the first attempt) can be used immediately.</li>
 * </ol>
 */
@Slf4j
public class AiHealingRetryAnalyzer implements IRetryAnalyzer {

    private int retryCount = 0;
    private static final int MAX_RETRY = 1;

    @Override
    public boolean retry(ITestResult result) {
        HealingConfig config = HealingConfig.getInstance();
        if (!config.isHealingEnabled()) {
            return false;
        }

        if (retryCount < MAX_RETRY && AiHealingListener.isLocatorFailure(result.getThrowable())) {
            retryCount++;
            log.warn("[AiHealingRetry] Retrying test {} (attempt {}/{})",
                    result.getMethod().getMethodName(), retryCount + 1, MAX_RETRY + 1);
            return true;
        }
        return false;
    }
}
