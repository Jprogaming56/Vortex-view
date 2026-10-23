package com.personal.vortexview;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Talks to AiT purely through reflection, so this add-on needs no AiT jar at build time
 * and keeps compiling if AiT's internals shift. Every method here only touches public
 * AiT API (the same calls AiT's own monitor screen and sequence handler make).
 */
public final class AitBridge {
    private static final Map<String, Method> CACHE = new HashMap<>();

    /** A class loader that is known to see AiT (remembered when the client first touches an AiT object). */
    private static volatile ClassLoader aitLoader;

    private AitBridge() {
    }

    public static void rememberLoader(Object aitObject) {
        if (aitObject != null) aitLoader = aitObject.getClass().getClassLoader();
    }

    /** Looks an AiT class up through every loader that might see it. */
    public static Class<?> findClass(String name) throws ClassNotFoundException {
        ClassLoader[] loaders = {
                aitLoader,
                AitBridge.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        ClassNotFoundException last = null;
        for (ClassLoader l : loaders) {
            if (l == null) continue;
            try {
                return Class.forName(name, true, l);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }
        throw last == null ? new ClassNotFoundException(name) : last;
    }

    /** Calls an instance method found by name and argument types. */
    public static Object call(Object target, String name, Object... args) throws Exception {
        if (target == null) throw new IllegalStateException("null target for " + name);
        Method m = find(target.getClass(), name, args);
        return m.invoke(target, args);
    }

    /** Tries each name in turn (used where the runtime name may be an SRG name). */
    public static Object callAny(Object target, String[] names, Object... args) throws Exception {
        Exception last = null;
        for (String n : names) {
            try {
                return call(target, n, args);
            } catch (NoSuchMethodException e) {
                last = e;
            }
        }
        throw last == null ? new NoSuchMethodException(String.join("/", names)) : last;
    }

    /** Calls a static method of a class that is loaded by the same loader as {@code loaderSource}. */
    public static Object callStatic(Object loaderSource, String className, String name, Object... args) throws Exception {
        Class<?> cls;
        try {
            cls = Class.forName(className, true, loaderSource.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            cls = findClass(className);
        }
        Method m = find(cls, name, args);
        return m.invoke(null, args);
    }

    /** Safe variant: returns null instead of throwing. */
    public static Object tryCall(Object target, String name, Object... args) {
        try {
            return call(target, name, args);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method find(Class<?> cls, String name, Object[] args) throws NoSuchMethodException {
        StringBuilder key = new StringBuilder(cls.getName()).append('#').append(name);
        for (Object a : args) key.append(',').append(a == null ? "null" : a.getClass().getName());
        String k = key.toString();

        synchronized (CACHE) {
            Method cached = CACHE.get(k);
            if (cached != null) return cached;
        }

        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] p = m.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < p.length && ok; i++) ok = accepts(p[i], args[i]);
            if (!ok) continue;
            m = publicVersion(cls, m);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            synchronized (CACHE) {
                CACHE.put(k, m);
            }
            return m;
        }
        throw new NoSuchMethodException(cls.getName() + "#" + name + " with " + args.length + " args");
    }

    /** If the method lives in a non-public class, find the same method on a public parent type. */
    private static Method publicVersion(Class<?> cls, Method m) {
        if (Modifier.isPublic(m.getDeclaringClass().getModifiers())) return m;
        java.util.ArrayDeque<Class<?>> queue = new java.util.ArrayDeque<>();
        queue.add(cls);
        while (!queue.isEmpty()) {
            Class<?> c = queue.poll();
            if (Modifier.isPublic(c.getModifiers())) {
                try {
                    return c.getMethod(m.getName(), m.getParameterTypes());
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (c.getSuperclass() != null) queue.add(c.getSuperclass());
            queue.addAll(java.util.Arrays.asList(c.getInterfaces()));
        }
        return m;
    }

    private static boolean accepts(Class<?> param, Object arg) {
        if (arg == null) return !param.isPrimitive();
        if (param.isPrimitive()) {
            if (param == int.class) return arg instanceof Integer;
            if (param == float.class) return arg instanceof Float;
            if (param == boolean.class) return arg instanceof Boolean;
            if (param == double.class) return arg instanceof Double;
            if (param == long.class) return arg instanceof Long;
            return false;
        }
        return param.isInstance(arg);
    }
}
