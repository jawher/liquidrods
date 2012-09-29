package liquidrods;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

/**
 * The API's entry point. Used to configure liquidrods (register custom tag handlers, template loader, escaper) and to parse templates.
 */
public class Liquidrods {
    /**
     * Used to load a template from its name. Implement to customize this process (e.g. to load templates from the DB) and call {@link Liquidrods#templateLoader(liquidrods.Liquidrods.TemplateLoader)}.
     */
    public interface TemplateLoader {
        /**
         * Load a template from its name
         *
         * @param name the template logical name
         * @return a reader, so that we don't have to mess with encodings
         */
        Reader load(String name);
    }

    private TemplateLoader templateLoader = new TemplateLoader() {
        @Override
        public Reader load(String name) {
            try {
                return new InputStreamReader(Liquidrods.class.getClassLoader().getResourceAsStream(name), "utf-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    };

    /**
     * Escape a value before writing it to the result. Implement to customize the escaping process (e.g. Json escaping) and call {@link Liquidrods#escaper(liquidrods.Liquidrods.Escaper)}.
     * Liquirods uses a HTML escaper by default, unless configured otherwise.
     */
    public interface Escaper {
        /**
         * escape a value
         *
         * @param value the value to be escaped
         * @return the escaped result
         */
        public String escape(String value);
    }


    private Escaper escaper = new Escaper() {
        @Override
        public String escape(String value) {
            return HtmlUtils.htmlEscape(value);
        }
    };

    private Map<String, BlockHandler> handlers = new HashMap<String, BlockHandler>();

    /**
     * Construct an instance with usable defaults: a html escaper, a classpath-based template loader and preconfigured default tag handlers (if/else and for)
     */
    public Liquidrods() {
        registerHandler("if", new IfBlock());
        registerHandler("for", new IterBlock());
        registerHandler("else", new ElseBlock());
        registerHandler("include", new BlockHandler() {
            @Override
            public boolean wantsCloseTag() {
                return false;
            }

            @Override
            public void render(LiquidrodsNode node, Context context, BlockHandler defaultBlockHandler, Writer out) throws IOException {
                // nop. Mommy Template will take care of me
            }
        });

    }

    /**
     * Register a handler for a custom tag, or override an existing one.
     *
     * @param name    the tag name
     * @param handler the handler used to drive the template parsing and rendering.
     * @return self, to enable chaining
     */
    public final Liquidrods registerHandler(String name, BlockHandler handler) {
        handlers.put(name, handler);
        return this;
    }


    /**
     * @return the configured template loader
     */
    public TemplateLoader templateLoader() {
        return templateLoader;
    }

    /**
     * Configure a custom template loader
     *
     * @param templateLoader
     * @return self, to enable chaining
     */
    public Liquidrods templateLoader(TemplateLoader templateLoader) {
        this.templateLoader = templateLoader;
        return this;
    }

    /**
     * @return the configured escaper
     */
    public Escaper escaper() {
        return this.escaper;
    }

    /**
     * Configure a custom escaper
     *
     * @param escaper
     * @return self, to enable chaining
     */
    public Liquidrods escaper(Escaper escaper) {
        this.escaper = escaper;
        return this;
    }

    /**
     * Parse a template from its logical name (uses the {@link TemplateLoader})
     *
     * @param name the template logical name
     * @return a parsed, ready for use template
     */
    public Template parse(String name) {
        return parse(templateLoader.load(name));
    }

    /**
     * Parses a template from a reader
     *
     * @param reader the template reader
     * @return a parsed, ready for use template
     */
    public Template parse(Reader reader) {
        List<LiquidrodsNode> rootNodes = new LiquidrodsParser(reader, handlers).parse();
        return new Template(rootNodes, this);
    }

    /* package */ BlockHandler defaultRenderer = new BlockHandler() {

        @Override
        public boolean wantsCloseTag() {
            return false;//doesn't matter
        }

        @Override
        public void render(LiquidrodsNode node, Context context, BlockHandler defaultBlockHandler, Writer out) throws IOException {
            if (node instanceof LiquidrodsNode.Text) {
                out.write(((LiquidrodsNode.Text) node).getValue());
            } else if (node instanceof LiquidrodsNode.Variable) {
                final LiquidrodsNode.Variable variable = (LiquidrodsNode.Variable) node;
                final Object value = context.resolve(variable.getName());
                if (value != null) {
                    final String str = String.valueOf(value);
                    if (variable.isRaw()) {
                        out.write(str);
                    } else {
                        out.write(escaper.escape(str));
                    }
                }
            } else {
                LiquidrodsNode.Block block = (LiquidrodsNode.Block) node;
                BlockHandler handler = handlers.get(block.getName());
                if (handler == null) {
                    throw new RuntimeException("No handler for block " + block.getName());
                } else {
                    handler.render(node, context, defaultBlockHandler, out);
                }
            }
        }
    };

    /**
     * The handler for the if tag. Takes a parameter that'll be evaluated using the current context to decide whether to render its body or not.
     * <p/>
     * If the parameter evaluates to a boolean, it is used as is for the test. Otherwise, null is considered as false and all other values as true.
     */
    public static class IfBlock implements BlockHandler {

        @Override
        public boolean wantsCloseTag() {
            return true;
        }

        @Override
        public void render(LiquidrodsNode node, Context context, BlockHandler defaultBlockHandler, Writer out) throws IOException {
            LiquidrodsNode.Block block = (LiquidrodsNode.Block) node;
            Object value = context.resolve(block.getArg());
            boolean doit = true;
            if (value == null) {
                doit = false;
            }
            if (value instanceof Boolean) {
                doit = (Boolean) value;
            }
            if (value instanceof Collection) {
                doit = !((Collection) value).isEmpty();
            }

            Context subContext = new Context(context, value);
            for (LiquidrodsNode child : block.getChildren()) {
                if (child instanceof LiquidrodsNode.Block && ("else".equals(((LiquidrodsNode.Block) child).getName()))) {
                    if (doit) {
                        return;
                    }
                    doit = !doit;
                } else if (doit) {
                    defaultBlockHandler.render(child, subContext, defaultBlockHandler, out);
                }
            }
        }
    }

    /**
     * The handler for the else tag. Does nothing on it's own: the logic is handled in the if tag handler {@link IfBlock}. Only useful to guide the parsing as the else tag doesn't have a body.
     */
    public static class ElseBlock implements BlockHandler {
        @Override
        public boolean wantsCloseTag() {
            return false;
        }

        @Override
        public void render(LiquidrodsNode node, Context context, BlockHandler defaultBlockHandler, Writer out) throws IOException {
            // nop. Daddy if will do just fine on his own.
        }
    }

    /**
     * The for tag handler. Takes a parameter representing the collection to iterate on.
     * <p/>
     * Here's how the parameter is handled:
     * <ul>
     * <li> with the following types the parameter is an instance of {@link Iterable}, its iterator is used</li>
     * <li> with the following types the parameter is an array, iterates over it's elements</li>
     * <li> with the following types the parameter is an instance of {@link Map}, iterate over it's entries {@link java.util.Map#entrySet()}</li>
     * <li> Otherwise, do a single iteration using the parameter's value</li>
     * </ul>
     * <p/>
     * The children are rendered using the default handler but with a child context wrapping the item being iterated on. Also, this context is extended with the following properties:
     *
     * <ul>
     *     <li><code>#</code>: 0-based index</li>
     *     <li><code>##</code>: 1-based index</li>
     *     <li><code>#first</code>: true for the first iteration, false otherwise</li>
     *     <li><code>#last</code>: true for the last iteration, false otherwise</li>
     * </ul>
     */
    public static class IterBlock implements BlockHandler {

        @Override
        public boolean wantsCloseTag() {
            return true;
        }

        @Override
        public void render(LiquidrodsNode node, Context context, BlockHandler defaultBlockHandler, Writer out) throws IOException {
            LiquidrodsNode.Block block = (LiquidrodsNode.Block) node;
            Object value = context.resolve(block.getArg());
            Iterator<?> coll;
            if (value == null) {
                return;
            } else if (value instanceof Iterable) {
                coll = ((Iterable) value).iterator();
            } else if (value.getClass().isArray()) {
                coll = new ArrayIterator(value);
            } else if (value instanceof Map) {
                coll = ((Map) value).entrySet().iterator();
            } else {
                coll = Arrays.asList(value).iterator();
            }
            int i = 0;
            while (coll.hasNext()) {
                Object o = coll.next();
                Context subContext = new IterContext(context, o, i, !coll.hasNext());
                for (LiquidrodsNode child : block.getChildren()) {
                    defaultBlockHandler.render(child, subContext, defaultBlockHandler, out);
                }
                i++;
            }
        }

        private static class IterContext extends Context {
            private final int index;
            private final boolean last;

            public IterContext(Context parent, Object root, int index, boolean last) {
                super(parent, root);
                this.index = index;
                this.last = last;
            }

            @Override
            protected Object extend(String key) {
                if (key.equals("#")) {
                    return index;
                } else if (key.equals("##")) {
                    return index + 1;
                } else if (key.equals("#first")) {
                    return index == 0;
                } else if (key.equals("#last")) {
                    return last;
                } else {
                    return NOT_FOUND;
                }
            }
        }

        private static class ArrayIterator implements Iterator<Object> {
            private final Object value;
            private final int length;
            private int index = 0;

            public ArrayIterator(Object value) {
                this.value = value;
                this.length = Array.getLength(value);
            }

            @Override
            public boolean hasNext() {
                return index < length;
            }

            @Override
            public Object next() {
                return Array.get(value, index++);
            }

            @Override
            public void remove() {

            }
        }
    }
}
