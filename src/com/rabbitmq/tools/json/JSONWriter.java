/*
Copyright 2006, 2007 Frank Carver
Copyright 2007 Tony Garnock-Jones

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
/*
 * Based on org.stringtree.json.JSONWriter, licensed under APL and
 * LGPL. We've chosen APL (see above). The original code was written
 * by Frank Carver. Tony Garnock-Jones has made many changes to it
 * since then.
 */
package com.rabbitmq.tools.json;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class JSONWriter {
    private boolean indentMode = false;
    private int indentLevel = 0;
    private StringBuffer buf = new StringBuffer();

    public JSONWriter() {}

    public JSONWriter(boolean indenting) {
        indentMode = indenting;
    }

    public boolean getIndentMode() {
        return indentMode;
    }

    public void setIndentMode(boolean value) {
        indentMode = value;
    }

    private void newline() {
        if (indentMode) {
            add('\n');
            for (int i = 0; i < indentLevel; i++) add(' ');
        }
    }

    public String write(Object object) {
        buf.setLength(0);
        value(object);
        return buf.toString();
    }

    public String write(long n) {
        return write(new Long(n));
    }

    public Object write(double d) {
        return write(new Double(d));
    }

    public String write(char c) {
        return write(new Character(c));
    }

    public String write(boolean b) {
        return write(Boolean.valueOf(b));
    }

    private void value(Object object) {
        if (object == null) add("null");
        else if (object instanceof JSONSerializable) {
            ((JSONSerializable) object).jsonSerialize(this);
        } else if (object instanceof Class) string(object);
        else if (object instanceof Boolean) bool(((Boolean) object).booleanValue());
        else if (object instanceof Number) add(object);
        else if (object instanceof String) string(object);
        else if (object instanceof Character) string(object);
        else if (object instanceof Map) map((Map<String, Object>) object);
        else if (object.getClass().isArray()) array(object);
        else if (object instanceof Collection) array(((Collection) object).iterator());
        else bean(object);
    }

    private void bean(Object object) {
        writeLimited(object.getClass(), object, null);
    }

    /**
     * Write only a certain subset of the object's properties and fields.
     * @param klass the class to look up properties etc in
     * @param object the object
     * @param properties explicit list of property/field names to include - may be null for "all"
     */
    public void writeLimited(Class klass, Object object, String[] properties) {
        Set<String> propertiesSet = null;
        if (properties != null) {
            propertiesSet = new HashSet<String>();
            for (String p: properties) {
                propertiesSet.add(p);
            }
        }

        add("{"); indentLevel += 2; newline();
        boolean needComma = false;

        BeanInfo info;
        try {
            info = Introspector.getBeanInfo(klass);
        } catch (IntrospectionException ie) {
            info = null;
        }

        if (info != null) {
            PropertyDescriptor[] props = info.getPropertyDescriptors();
            for (int i = 0; i < props.length; ++i) {
                PropertyDescriptor prop = props[i];
                String name = prop.getName();
                if (propertiesSet == null && name.equals("class")) {
                    // We usually don't want the class in there.
                    continue;
                }
                if (propertiesSet == null || propertiesSet.contains(name)) {
                    Method accessor = prop.getReadMethod();
                    if (accessor != null && !Modifier.isStatic(accessor.getModifiers())) {
                        try {
                            Object value = accessor.invoke(object, (Object[])null);
                            if (needComma) { add(','); newline(); }
                            needComma = true;
                            add(name, value);
                        } catch (Exception e) {
                            // Ignore it.
                        }
                    }
                }
            }
        }

        Field[] ff = object.getClass().getDeclaredFields();
        for (int i = 0; i < ff.length; ++i) {
            Field field = ff[i];
            int fieldMod = field.getModifiers();
            String name = field.getName();
            if (propertiesSet == null || propertiesSet.contains(name)) {
                if (!Modifier.isStatic(fieldMod)) {
                    try {
                        Object v = field.get(object);
                        if (needComma) { add(','); newline(); }
                        needComma = true;
                        add(name, v);
                    } catch (Exception e) {
                        // Ignore it.
                    }
                }
            }
        }

        indentLevel -= 2; newline(); add("}");
    }

    private void add(String name, Object value) {
        add('"');
        add(name);
        add("\":");
        value(value);
    }

    private void map(Map<String, Object> map) {
        add("{"); indentLevel += 2; newline();
        Iterator<String> it = map.keySet().iterator();
        while (it.hasNext()) {
            Object key = it.next();
            value(key);
            add(":");
            value(map.get(key));
            if (it.hasNext()) { add(","); newline(); }
        }
        indentLevel -= 2; newline(); add("}");
    }

    private void array(Iterator it) {
        add("[");
        while (it.hasNext()) {
            value(it.next());
            if (it.hasNext()) add(",");
        }
        add("]");
    }

    private void array(Object object) {
        add("[");
        int length = Array.getLength(object);
        for (int i = 0; i < length; ++i) {
            value(Array.get(object, i));
            if (i < length - 1) add(',');
        }
        add("]");
    }

    private void bool(boolean b) {
        add(b ? "true" : "false");
    }

    private void string(Object obj) {
        add('"');
        CharacterIterator it = new StringCharacterIterator(obj.toString());
        for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
            if (c == '"') add("\\\"");
            else if (c == '\\') add("\\\\");
            else if (c == '/') add("\\/");
            else if (c == '\b') add("\\b");
            else if (c == '\f') add("\\f");
            else if (c == '\n') add("\\n");
            else if (c == '\r') add("\\r");
            else if (c == '\t') add("\\t");
            else if (Character.isISOControl(c)) {
                unicode(c);
            } else {
                add(c);
            }
        }
        add('"');
    }

    private void add(Object obj) {
        buf.append(obj);
    }

    private void add(char c) {
        buf.append(c);
    }

    static char[] hex = "0123456789ABCDEF".toCharArray();

    private void unicode(char c) {
        add("\\u");
        int n = c;
        for (int i = 0; i < 4; ++i) {
            int digit = (n & 0xf000) >> 12;
            add(hex[digit]);
            n <<= 4;
        }
    }
}
