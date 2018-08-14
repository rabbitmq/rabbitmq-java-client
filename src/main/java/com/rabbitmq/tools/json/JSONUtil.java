// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.tools.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * Utility methods for working with JSON objects in Java.
 */
public class JSONUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(JSONUtil.class);

    /**
     * Uses reflection to fill public fields and Bean properties of
     * the target object from the source Map.
     */
    public static Object fill(Object target, Map<String, Object> source)
        throws IntrospectionException, IllegalAccessException, InvocationTargetException {
        return fill(target, source, true);
    }

    /**
     * Uses reflection to fill public fields and optionally Bean
     * properties of the target object from the source Map.
     */
    public static Object fill(Object target, Map<String, Object> source, boolean useProperties)
        throws IntrospectionException, IllegalAccessException, InvocationTargetException {
        if (useProperties) {
            BeanInfo info = Introspector.getBeanInfo(target.getClass());

            PropertyDescriptor[] props = info.getPropertyDescriptors();
            for (int i = 0; i < props.length; ++i) {
                PropertyDescriptor prop = props[i];
                String name = prop.getName();
                Method setter = prop.getWriteMethod();
                if (setter != null && !Modifier.isStatic(setter.getModifiers())) {
                    setter.invoke(target, source.get(name));
                }
            }
        }

        Field[] ff = target.getClass().getDeclaredFields();
        for (int i = 0; i < ff.length; ++i) {
            Field field = ff[i];
            int fieldMod = field.getModifiers();
            if (Modifier.isPublic(fieldMod) && !(Modifier.isFinal(fieldMod) ||
                Modifier.isStatic(fieldMod))) {
                try {
                    field.set(target, source.get(field.getName()));
                } catch (IllegalArgumentException iae) {
                    // no special error processing required
                }
            }
        }

        return target;
    }

    /**
     * Ignores reflection exceptions while using reflection to fill
     * public fields and Bean properties of the target object from the
     * source Map.
     */
    public static void tryFill(Object target, Map<String, Object> source) {
        try {
            fill(target, source);
        } catch (IntrospectionException ie) {
            LOGGER.error("Error in tryFill", ie);
        } catch (IllegalAccessException iae) {
            LOGGER.error("Error in tryFill", iae);
        } catch (InvocationTargetException ite) {
            LOGGER.error("Error in tryFill", ite);
        }
    }
}
