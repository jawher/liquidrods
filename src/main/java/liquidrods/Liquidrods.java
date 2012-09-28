package liquidrods;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

public class Liquidrods {
    public interface TemplateLoader {
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

    public interface Escaper {
        public String escape(String value);
    }


    private Escaper escaper = new Escaper() {
        @Override
        public String escape(String value) {
            return HtmlUtils.htmlEscape(value);
        }
    };

    private Map<String, BlockHandler> handlers = new HashMap<String, BlockHandler>();

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

    public final Liquidrods registerHandler(String name, BlockHandler handler) {
        handlers.put(name, handler);
        return this;
    }


    public TemplateLoader getTemplateLoader() {
        return templateLoader;
    }

    public Liquidrods templateLoader(TemplateLoader templateLoader) {
        this.templateLoader = templateLoader;
        return this;
    }

    public Escaper escaper() {
        return this.escaper;
    }

    public Liquidrods escaper(Escaper escaper) {
        this.escaper = escaper;
        return this;
    }

    public Template parse(String name) {
        return parse(templateLoader.load(name));
    }

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
