package liquidrods;


import org.junit.Test;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiquidrodsTest {
    private String render(Config config, String template, Object model) {
        StringWriter writer = new StringWriter();
        Liquidrods.parse(new StringReader(template), config).render(model, writer);
        return writer.getBuffer().toString();
    }

    private String render(String template, Object model) {
        return render(new Config(), template, model);
    }

    @Test
    public void testText() {
        final Object model = Collections.emptyMap();

        String template = "text";
        assertEquals("text", render(template, model));
    }

    @Test
    public void testRespectsTextLayout() {
        final Object model = Collections.emptyMap();

        String template = "text\n\ttext\n  text\n ";
        assertEquals(template, render(template, model));
    }


    @Test
    public void testVariable() {
        final Object model2 = Collections.singletonMap("y.z", "42");
        Object model1 = new Object() {
            public Object x = model2;
        };
        Object model0 = Collections.singletonMap("a.b", model1);

        String template = "{{'a.b'.x.'y.z'}}";
        assertEquals("42", render(template, model0));
    }

    @Test
    public void testDefaultHtmlEscaping() {
        final Object model = Collections.singletonMap("s", "<tag attr=\"value\">text</tag>");

        String template = "{{s}}";
        assertEquals("&lt;tag attr=&quot;value&quot;&gt;text&lt;/tag&gt;", render(template, model));
    }

    @Test
    public void testDefaultUnescapedHtml() {
        final Object model = Collections.singletonMap("s", "<tag attr=\"value\">text</tag>");

        String template = "{{{s}}}";
        assertEquals("<tag attr=\"value\">text</tag>", render(template, model));
    }

    @Test
    public void testUsesSuppliedEscaper() {
        final Object model = Collections.singletonMap("s", "stuff");

        String template = "{{s}} - {{{s}}}";
        assertEquals("escaped - stuff", render(new Config().escaper(new Config.Escaper() {
            @Override
            public String escape(String value) {
                return "escaped";
            }
        }), template, model));
    }

    @Test
    public void testUsesSuppliedTemplateLoader() {
        final Object model = Collections.emptyMap();

        assertEquals("name", render(new Config().templateLoader(new Config.TemplateLoader() {
            @Override
            public Reader load(String name) {
                return new StringReader(name);
            }
        }), "name", model));
    }

    @Test
    public void testIfTagRendersBodyWithTrue() {
        final Object model = Collections.singletonMap("x", true);

        String template = "{% if x %}x{% end %}";
        assertEquals("x", render(template, model));
    }

    @Test
    public void testIfTagRendersBodyWithString() {
        final Object model = Collections.singletonMap("x", "x");

        String template = "{% if x %}x{% end %}";
        assertEquals("x", render(template, model));
    }

    @Test
    public void testIfTagHidesBodyWithFalse() {
        final Object model = Collections.singletonMap("x", false);

        String template = "{% if x %}x{% end %}";
        assertEquals("", render(template, model));
    }

    @Test
    public void testIfTagHidesBodyWithNull() {
        final Object model = Collections.emptyMap();

        String template = "{% if x %}x{% end %}";
        assertEquals("", render(template, model));
    }

    @Test
    public void testElseTag() {
        final Object model = Collections.singletonMap("x", false);

        String template = "{% if x %}x{% else %}y{% end %}";
        assertEquals("y", render(template, model));
    }

    @Test
    public void testForTagIteratesOverLists() {
        final Object model = Collections.singletonMap("xs", Arrays.asList("a", "b"));

        String template = "{% for xs %}{{.}}{% end %}";
        assertEquals("ab", render(template, model));
    }

    @Test
    public void testForTagIteratesOverArrays() {
        final Object model = Collections.singletonMap("xs", new int[]{1, 2});

        String template = "{% for xs %}{{.}}{% end %}";
        assertEquals("12", render(template, model));
    }

    @Test
    public void testForTagIteratesOverMaps() {
        Map<String, Integer> map = new TreeMap<String, Integer>();
        map.put("a", 1);
        map.put("b", 2);
        final Object model = Collections.singletonMap("xs", map);

        String template = "{% for xs %}{{'.'.key}}:{{this.value}}{% end %}";
        assertEquals("a:1b:2", render(template, model));
    }

    @Test
    public void testForTagRendersBodyOnceWithNonCollection() {
        final Object model = Collections.singletonMap("xs", 42);
        String template = "{% for xs %}{{.}}{% end %}";
        assertEquals("42", render(template, model));
    }

    @Test
    public void testForTagHidesBodyWithNull() {
        final Object model = Collections.emptyMap();
        String template = "{% for xs %}x{% end %}";
        assertEquals("", render(template, model));
    }


    @Test
    public void testInclusion() {
        final Object model = Collections.emptyMap();
        String template = "before|{% include f.inc %}|after";
        Config lr = new Config().templateLoader(new Config.TemplateLoader() {
            @Override
            public Reader load(String name) {
                return new StringReader(name);
            }
        });
        assertEquals("before|f.inc|after", render(lr, template, model));
    }

    @Test
    public void testInheritance() {
        final Object model = Collections.emptyMap();
        final String parentTemplate = "parentBefore|{% placeholder a %}junk{% end %}|parent|{% placeholder b %}junk{% end %}|parentAfter";
        String childTemplate = "thresh|{% extends parent.inc %}{% define a %}aReplacement{% end %}{% define b %}bReplacement{% end %}{% end %}|thresh";
        Config lr = new Config().templateLoader(new Config.TemplateLoader() {
            @Override
            public Reader load(String name) {
                return new StringReader(parentTemplate);
            }
        });
        assertEquals("parentBefore|aReplacement|parent|bReplacement|parentAfter", render(lr, childTemplate, model));
    }

    @Test
    public void testInheritanceKeepsUndefinedPlaceholders() {
        final Object model = Collections.emptyMap();
        final String parentTemplate = "parentBefore|{% placeholder a %}junk{% end %}|parent|{% placeholder b %}keep{% end %}|parentAfter";
        String childTemplate = "thresh|{% extends parent.inc %}{% define a %}aReplacement{% end %}{% end %}|thresh";
        Config lr = new Config().templateLoader(new Config.TemplateLoader() {
            @Override
            public Reader load(String name) {
                return new StringReader(parentTemplate);
            }
        });
        assertEquals("parentBefore|aReplacement|parent|keep|parentAfter", render(lr, childTemplate, model));
    }

    @Test
    public void testCustomHandlerWithNoCloseableTag() {
        final Object model = Collections.singletonMap("x", "y");
        String template = "thresh|{% custom x %}|thresh";
        Config lr = new Config().registerHandler("custom", new BlockHandler() {
            @Override
            public boolean wantsCloseTag() {
                return false;
            }

            @Override
            public void render(LiquidrodsNode node, Context context, Config config, Writer out) throws IOException {
                LiquidrodsNode.Block b = (LiquidrodsNode.Block) node;
                out.write(b.getName());
                out.write(":");
                out.write(String.valueOf(context.resolve(b.getArg())));
            }
        });
        assertEquals("thresh|custom:y|thresh", render(lr, template, model));
    }

    @Test
    public void testCustomHandlerWithCloseableTag() {
        final Map<String, Object> model = new HashMap<String, Object>();
        model.put("x", "y");
        model.put("a", "b");
        String template = "thresh|{% custom x %}text{{a}}{% end %}|thresh";
        Config lr = new Config().registerHandler("custom", new BlockHandler() {
            @Override
            public boolean wantsCloseTag() {
                return true;
            }

            @Override
            public void render(LiquidrodsNode node, Context context, Config config, Writer out) throws IOException {
                LiquidrodsNode.Block b = (LiquidrodsNode.Block) node;
                out.write(b.getName());
                out.write(":");
                out.write(String.valueOf(context.resolve(b.getArg())));
                for (LiquidrodsNode child : b.getChildren()) {
                    config.defaultRenderer().render(child, context, config, out);
                }
            }
        });
        assertEquals("thresh|custom:ytextb|thresh", render(lr, template, model));
    }

    @Test
    public void testCustomHandlerWithCloseableTagAndExtendContext() {
        final Map<String, Object> model = new HashMap<String, Object>();
        model.put("x", "y");
        model.put("a", "b");
        String template = "thresh|{% custom x %}{{ex}}text{{a}}{% end %}|thresh";
        Config lr = new Config().registerHandler("custom", new BlockHandler() {
            @Override
            public boolean wantsCloseTag() {
                return true;
            }

            @Override
            public void render(LiquidrodsNode node, Context context, Config config, Writer out) throws IOException {
                LiquidrodsNode.Block b = (LiquidrodsNode.Block) node;
                out.write(b.getName());
                out.write(":");
                out.write(String.valueOf(context.resolve(b.getArg())));
                Context ex = new Context(context, model) {
                    @Override
                    protected Object extend(String key) {
                        if("ex".equals(key)) {
                            return "_";
                        } else {
                            return NOT_FOUND;
                        }
                    }
                };
                for (LiquidrodsNode child : b.getChildren()) {
                    config.defaultRenderer().render(child, ex, config, out);
                }
            }
        });
        assertEquals("thresh|custom:y_textb|thresh", render(lr, template, model));
    }

}
