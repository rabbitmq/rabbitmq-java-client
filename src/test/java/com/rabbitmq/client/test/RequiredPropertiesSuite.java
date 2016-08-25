package com.rabbitmq.client.test;

import org.junit.runner.Runner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class RequiredPropertiesSuite extends Suite {

    public RequiredPropertiesSuite(Class<?> klass, RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    public RequiredPropertiesSuite(RunnerBuilder builder, Class<?>[] classes) throws InitializationError {
        super(builder, classes);
    }

    protected RequiredPropertiesSuite(Class<?> klass, Class<?>[] suiteClasses) throws InitializationError {
        super(klass, suiteClasses);
    }

    protected RequiredPropertiesSuite(RunnerBuilder builder, Class<?> klass, Class<?>[] suiteClasses) throws InitializationError {
        super(builder, klass, suiteClasses);
    }

    protected RequiredPropertiesSuite(Class<?> klass, List<Runner> runners) throws InitializationError {
        super(klass, runners);
    }

    @Override
    protected List<Runner> getChildren() {
        if(!AbstractRMQTestSuite.requiredProperties()) {
            return new ArrayList<Runner>();
        } else {
            return super.getChildren();
        }
    }
}
