//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.tools.json;

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
    /**
     * Uses reflection to fill public fields and Bean properties of
     * the target object from the source Map.
     */
    public static Object fill(Object target, Map<String, Object> source)
	throws IntrospectionException, IllegalAccessException, InvocationTargetException
    {
	return fill(target, source, true);
    }

    /**
     * Uses reflection to fill public fields and optionally Bean
     * properties of the target object from the source Map.
     */
    public static Object fill(Object target, Map<String, Object> source, boolean useProperties)
	throws IntrospectionException, IllegalAccessException, InvocationTargetException
    {
	if (useProperties) {
	    BeanInfo info = Introspector.getBeanInfo(target.getClass());

	    PropertyDescriptor[] props = info.getPropertyDescriptors();
	    for (int i = 0; i < props.length; ++i) {
		PropertyDescriptor prop = props[i];
		String name = prop.getName();
		Method setter = prop.getWriteMethod();
		if (setter != null && !Modifier.isStatic(setter.getModifiers())) {
		    //System.out.println(target + " " + name + " <- " + source.get(name));
		    setter.invoke(target, new Object[] { source.get(name) });
		}
	    }
	}

	Field[] ff = target.getClass().getDeclaredFields();
	for (int i = 0; i < ff.length; ++i) {
	    Field field = ff[i];
            int fieldMod = field.getModifiers();
	    if (Modifier.isPublic(fieldMod) && !(Modifier.isFinal(fieldMod) ||
                                                 Modifier.isStatic(fieldMod)))
            {
		//System.out.println(target + " " + field.getName() + " := " + source.get(field.getName()));
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
	    ie.printStackTrace();
	} catch (IllegalAccessException iae) {
	    iae.printStackTrace();
	} catch (InvocationTargetException ite) {
	    ite.printStackTrace();
	}
    }
}
