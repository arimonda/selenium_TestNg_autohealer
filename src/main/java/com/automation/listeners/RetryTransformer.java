package com.automation.listeners;

import lombok.extern.slf4j.Slf4j;
import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * TestNG {@link IAnnotationTransformer} that automatically sets
 * {@link AiHealingRetryAnalyzer} on every test method.
 * <p>
 * Register this in {@code testng.xml} as a listener so that all tests
 * get the self-healing retry behavior without annotating each method.
 */
@Slf4j
public class RetryTransformer implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation, Class testClass,
                          Constructor testConstructor, Method testMethod) {
        if (annotation.getRetryAnalyzerClass() == null
                || annotation.getRetryAnalyzerClass() == org.testng.IRetryAnalyzer.class) {
            annotation.setRetryAnalyzer(AiHealingRetryAnalyzer.class);
            if (testMethod != null) {
                log.debug("[RetryTransformer] Applied AiHealingRetryAnalyzer to: {}", testMethod.getName());
            }
        }
    }
}
