package com.rabbitmq.examples.perf;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;

/**
 * PerfUtil
 */
public class PerfUtil {
    /**
     * Update property of object using a setter method on the object
     * @param obj object to update
     * @param name of property to update
     * @param value value to update property to
     */
    public static void setValue(Object obj, Object name, Object value) {
        try {
            PropertyDescriptor[] props = Introspector.getBeanInfo(obj.getClass()).getPropertyDescriptors();
            for (PropertyDescriptor prop : props) {
                if (prop.getName().equals(name)) {
                    prop.getWriteMethod().invoke(obj, value);
                    return;
                }
            }
            throw new RuntimeException("Could not find property " + name + " in " + obj.getClass());
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
