package liquidrods;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Context {
    public static final Object NOT_FOUND = new Object();
    public static final Class[] NO_ARGS = new Class[]{};
    private static Map<Key, Accessor> accessorCache = new ConcurrentHashMap<Key, Accessor>();
    private static Map<String, List<String>> partsCache = new ConcurrentHashMap<String, List<String>>();
    private final Context parent;
    private final Object data;
    private final Object helper;

    public Context(Context parent, Object root) {
        this.parent = parent;
        this.data = root;
        this.helper = parent == null ? data : parent.helper;
    }

    private static List<String> parts(String key) {
        List<String> res = new ArrayList<String>();
        boolean inQuotes = false;
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '.') {
                if (inQuotes) {
                    part.append(c);
                } else {
                    if (part.length() > 0) {
                        res.add(part.toString());
                    }
                    part = new StringBuilder();
                }
            } else if (c == '\'') {
                if (inQuotes) {
                    res.add(part.toString());
                    inQuotes = false;
                    part = new StringBuilder();
                } else {
                    if (part.length() == 0) {
                        inQuotes = true;
                    } else {
                        throw new RuntimeException("Invalid expression " + key);
                    }
                }
            } else {
                part.append(c);
            }
        }
        if (part.length() > 0) {
            if (inQuotes) {
                throw new RuntimeException("Invalid expression " + key + ": unclosed quote");
            } else {
                res.add(part.toString());
            }
        }

        return res;
    }

    public Object resolve(String key) {
        if (".".equals(key) || "this".equals(key)) {
            return data;
        } else {
            List<String> parts = partsCache.get(key);
            if (parts == null) {
                parts = parts(key);
                partsCache.put(key, parts);
            }
            Object base = data;
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);
                Object res = i == 0 ? extend(part) : NOT_FOUND;
                if (res == NOT_FOUND) {
                    if (".".equals(part) || "this".equals(part)) {
                        //NOP, base=base
                    } else if (base == null) {
                        throw new NullPointerException("Trying to access the property " + part + " on a null object");
                    } else if (base instanceof Map) {
                        Map<String, Object> m = (Map<String, Object>) base;
                        if (m.containsKey(part)) {
                            base = m.get(part);
                        } else {
                            base = NOT_FOUND;
                        }
                    } else {
                        final Accessor accessor = accessorFor(base, part);
                        if (accessor != null) {
                            base = accessor.get(base, helper, this);
                        } else {
                            base = NOT_FOUND;
                        }
                    }

                    if (base == NOT_FOUND) {
                        if (parent != null) {
                            return parent.resolve(key);
                        } else {
                            return null;
                        }
                    }
                } else {
                    base = res;
                }
            }
            return base;
        }
    }

    protected Object extend(String key) {
        return NOT_FOUND;
    }


    private Accessor accessorFor(Object data, String prop) {
        Key key = new Key(prop, data.getClass(), helper == null ? null : helper.getClass());
        if (accessorCache.containsKey(key)) {
            return accessorCache.get(key);
        } else {
            Accessor accessor = null;
            Method getter = searchMethod("get" + capitalize(prop), data.getClass(), NO_ARGS, true);
            if (getter == null) {
                getter = searchMethod("is" + capitalize(prop), data.getClass(), NO_ARGS, true);
            }
            if (getter != null) {
                getter.setAccessible(true);
                accessor = new Accessor.MethodAccessor(getter);
            }

            Method method = searchMethod(prop, data.getClass(), NO_ARGS, true);
            if (method != null) {
                method.setAccessible(true);
                accessor = new Accessor.MethodAccessor(method);
            }

            try {
                Field field = data.getClass().getField(prop);
                field.setAccessible(true);
                accessor = new Accessor.FieldAccessor(field);
            } catch (NoSuchFieldException e) {
                //nop
            }

            if (accessor == null && helper != null) {
                Method helperMethod = searchMethod(prop, helper.getClass(), new Class[]{data.getClass()}, true);
                if (helperMethod != null) {
                    helperMethod.setAccessible(true);
                    accessor = new Accessor.HelperAccessor(helperMethod);
                }
            }

            if (accessor == null) {
                accessor = Accessor.NoAccessor.INSTANCE;
            }
            accessorCache.put(key, accessor);

            return accessor;
        }
    }

    private static String capitalize(String name) {
        return name.substring(0, 1).toUpperCase(Locale.ENGLISH) + name.substring(1);
    }

    private static Method searchMethod(String methodName, Class clazz, Class[] args, boolean notVoid) {
        for (Class base = clazz; base != null; base = base.getSuperclass()) {
            Method[] methods = base.getDeclaredMethods();
            for (Method method : methods) {
                if (methodName.equals(method.getName()) && method.getParameterTypes().length == args.length && Modifier.isPublic(
                        method.getModifiers())) {
                    boolean match = true;
                    for (int i = 0; i < args.length; i++) {
                        Class argClass = args[i];
                        if (!method.getParameterTypes()[i].isAssignableFrom(argClass)) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        if (notVoid) {
                            if (!method.getReturnType().equals(Void.TYPE) && !method.getReturnType().equals(Void.class)) {
                                return method;
                            }
                        } else {
                            return method;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static class Key implements Serializable {
        public final String name;
        public final Class<?> clazz;
        public final Class<?> helperClazz;

        private Key(String name, Class<?> clazz, Class<?> helperClazz) {
            this.name = name;
            this.clazz = clazz;
            this.helperClazz = helperClazz;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Key key = (Key) o;

            if (!clazz.equals(key.clazz)) return false;
            if (helperClazz != null ? !helperClazz.equals(key.helperClazz) : key.helperClazz != null) return false;
            if (!name.equals(key.name)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + clazz.hashCode();
            result = 31 * result + (helperClazz != null ? helperClazz.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return clazz + "#" + name;
        }
    }

    private interface Accessor {
        Object get(Object root, Object helper, Context context);

        public static class MethodAccessor implements Accessor {
            private final Method getter;

            public MethodAccessor(Method getter) {
                this.getter = getter;
            }

            @Override
            public Object get(Object root, Object helper, Context context) {
                try {
                    return getter.invoke(root);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public static class FieldAccessor implements Accessor {
            private final Field field;

            public FieldAccessor(Field field) {
                this.field = field;
            }

            @Override
            public Object get(Object root, Object helper, Context context) {
                try {
                    return field.get(root);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public static class HelperAccessor implements Accessor {
            private final Method method;

            public HelperAccessor(Method method) {
                this.method = method;
            }

            @Override
            public Object get(Object root, Object helper, Context context) {
                try {
                    return method.invoke(helper, root);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public static class CompoundAccessor implements Accessor {
            public final List<Accessor> accessors;
            public final List<String> parts;

            public CompoundAccessor(List<Accessor> accessors, List<String> parts) {
                this.accessors = accessors;
                this.parts = parts;
            }

            @Override
            public Object get(Object root, Object helper, Context context) {
                return NOT_FOUND;
            }
        }

        public static class NoAccessor implements Accessor {
            public static final NoAccessor INSTANCE = new NoAccessor();

            @Override
            public Object get(Object root, Object helper, Context context) {
                return NOT_FOUND;
            }
        }
    }
}
