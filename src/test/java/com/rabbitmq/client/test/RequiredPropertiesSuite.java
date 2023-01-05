package com.rabbitmq.client.test;


import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class RequiredPropertiesSuite { //extends Suite {

/*
    private static final Logger LOGGER = LoggerFactory.getLogger(RequiredPropertiesSuite.class);

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

    @Override
    protected void runChild(Runner runner, RunNotifier notifier) {
        LOGGER.info("Running test {}", runner.getDescription().getDisplayName());
        super.runChild(runner, notifier);
    }

 */
}
