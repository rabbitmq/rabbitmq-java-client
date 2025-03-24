package com.rabbitmq.utility;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Set;

public class SafeObjectInputStream extends ObjectInputStream {
    protected static final Set<String> DEFAULT_NO_DESERIALIZE_CLASS_NAMES = Set.of(
            "org.apache.commons.collections.functors.InvokerTransformer",
            "org.apache.commons.collections.functors.InstantiateTransformer",
            "org.apache.commons.collections4.functors.InvokerTransformer",
            "org.apache.commons.collections4.functors.InstantiateTransformer",
            "org.codehaus.groovy.runtime.ConvertedClosure",
            "org.codehaus.groovy.runtime.MethodClosure",
            "org.springframework.beans.factory.ObjectFactory",
            "com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl",
            "org.apache.xalan.xsltc.trax.TemplatesImpl"
    );

    public SafeObjectInputStream(InputStream in) throws IOException {
        super(in);
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        String name = desc.getName();

        if (isBlocked(name)) {
            throw new SecurityException("Illegal type to deserialize: prevented for security reasons");
        }

        return super.resolveClass(desc);
    }

    private boolean isBlocked(String name) {
        for (String list : DEFAULT_NO_DESERIALIZE_CLASS_NAMES) {
            if (name.endsWith(list)) {
                return true;
            }
        }

        return false;
    }
}
