package liquidrods;


import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class ContextTest {

    @Test
    public void testResolveSelf() {
        Object model = new Object() {
            public int x = 5;
        };
        Context context = new Context(null, model);

        assertEquals(model, context.resolve("."));
    }

    @Test
    public void testResolveField() {
        Object model = new Object() {
            public int x = 5;
        };
        Context context = new Context(null, model);

        assertEquals(5, context.resolve("x"));
    }

    @Test
    public void testResolveGetter() {
        Object model = new Object() {
            public String getX() {
                return "a";
            }
        };
        Context context = new Context(null, model);

        assertEquals("a", context.resolve("x"));
    }

    @Test
    public void testResolveMethod() {
        Object model = new Object() {
            public String x() {
                return "b";
            }
        };
        Context context = new Context(null, model);

        assertEquals("b", context.resolve("x"));
    }

    @Test
    public void testResolveMap() {
        Object model = Collections.singletonMap("x", 42.3);
        Context context = new Context(null, model);

        assertEquals(42.3, context.resolve("x"));
    }

    @Test
    public void testResolveCompound() {
        final Object model3 = new Object() {
            public String getZ() {
                return "finally !";
            }
        };

        final Object model2 = new Object() {
            public Object y() {
                return model3;
            }
        };

        Object model1 = new Object() {
            public Object x = model2;
        };


        Object model0 = Collections.singletonMap("a", model1);

        Context context = new Context(null, model0);

        assertEquals("finally !", context.resolve("a.x.y.z"));
    }

    @Test
    public void testResolveQuotedKey() {
        Object model = Collections.singletonMap("x.y", "42");
        Context context = new Context(null, model);

        assertEquals("42", context.resolve("'x.y'"));
    }

    @Test
    public void testResolveCompoundQuotedKeys() {
        final Object model2 = Collections.singletonMap("y.z", true);
        Object model1 = new Object() {
            public Object x = model2;
        };
        Object model0 = Collections.singletonMap("a.b", model1);

        Context context = new Context(null, model0);

        assertEquals(true, context.resolve("'a.b'.x.'y.z'"));
    }

    @Test
    public void testDelegatesToParent() {
        Context parent = new Context(null, Collections.singletonMap("y.z", true));

        Context context = new Context(parent, Collections.singletonMap("a.b", 42));

        assertEquals(true, context.resolve("'y.z'"));
    }

    @Test
    public void testSaneCache() {
        Context context = new Context(null, Collections.singletonMap("a", 42));

        assertEquals(42, context.resolve("a"));

        context = new Context(null, Collections.singletonMap("a", 84));

        assertEquals(84, context.resolve("a"));
    }

    @Test
    public void testHelper() {
        Context parent = new Context(null, new Object() {
            public String y(Integer i) {
                return "_" + i + "_";
            }
        });

        Context context = new Context(parent, Collections.singletonMap("x", 42));

        assertEquals("_42_", context.resolve("x.y"));
    }

    @Test
    public void testSaneCacheForHelpers() {
        Context parent = new Context(null, new Object() {
            public String y(Integer i) {
                return "_" + i + "_";
            }
        });

        Context context = new Context(parent, Collections.singletonMap("x", 42));

        assertEquals("_42_", context.resolve("x.y"));

        Context parent2 = new Context(null, new Object() {
            public String y(Integer i) {
                return "|" + i + "|";
            }
        });

        Context context2 = new Context(parent2, Collections.singletonMap("x", 42));

        assertEquals("|42|", context2.resolve("x.y"));
    }

    @Test
    public void testExtend() {
        Context context = new Context(null, Collections.singletonMap("x", 42)) {
            @Override
            protected Object extend(String key) {
                return "#".equals(key) ? "J" : NOT_FOUND;
            }
        };

        assertEquals("J", context.resolve("#"));
    }

    @Test
    public void testExtendWithHelper() {
        Context parent = new Context(null, new Object() {
            public Object inc(Integer x) {
                return x + 1;
            }
        });
        Context context = new Context(parent, Collections.emptyMap()) {
            @Override
            protected Object extend(String key) {
                return "#".equals(key) ? 42 : NOT_FOUND;
            }
        };

        assertEquals(43, context.resolve("#.inc"));
    }
}
