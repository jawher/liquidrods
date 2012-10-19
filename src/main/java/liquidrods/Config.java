package liquidrods;


import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralises various configuration items, like what escaper and template loader to use, the default renderer and registered handlers
 */
public class Config {
    /**
     * Used to load a template from its name. Implement to customize this process (e.g. to load templates from the DB) and call {@link Config#templateLoader(Config.TemplateLoader)}.
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

    /**
     * Escape a value before writing it to the result. Implement to customize the escaping process (e.g. Json escaping) and call {@link Config#escaper(Config.Escaper)}.
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

    public interface Renderer {
        /**
         * This method gets called when the tag is to be rendered
         *
         * @param node    the node in the template to be rendered. In cas of tag, it should be cast to {@link LiquidrodsNode.Block} to gain access to its argument (if provided) and children (i.e. body)
         * @param context the model context associated with this node. Should be used to resolve property selectors.
         * @param config  the configuration to use
         * @param out     where to render this tag
         * @throws IOException so that you don't have to handle this exception when you use the writer
         */
        void render(LiquidrodsNode node, Context context, Config config, Writer out) throws IOException;
    }

    private TemplateLoader templateLoader = new TemplateLoader() {
        @Override
        public Reader load(String name) {
            try {
                return new InputStreamReader(Config.class.getClassLoader().getResourceAsStream(name), "utf-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    };

    private Escaper escaper = new Escaper() {
        @Override
        public String escape(String value) {
            return HtmlUtils.htmlEscape(value);
        }
    };

    private Map<String, BlockHandler> handlers = new HashMap<String, BlockHandler>();

    private void registerDefaultHandlers() {
        registerHandler("if", new IfBlock());
        registerHandler("ifnot", new IfBlock().inverted());
        registerHandler("for", new IterBlock());
        registerHandler("else", new IfBlock.ElseBlock());
        registerHandler("include", new BlockHandler() {
            @Override
            public boolean wantsCloseTag() {
                return false;
            }

            @Override
            public void render(LiquidrodsNode.Block block, Context context, Config config, Writer out) throws IOException {
                // nop. Mommy Template will take care of me
            }
        });
        registerHandler("extends", new BlockHandler() {
            @Override
            public boolean wantsCloseTag() {
                return false;
            }

            @Override
            public void render(LiquidrodsNode.Block block, Context context, Config config, Writer out) throws IOException {
                // nop. Mommy Template will take care of me
            }
        });

        registerHandler("block", new BlockHandler() {
            @Override
            public boolean wantsCloseTag() {
                return true;
            }

            @Override
            public void render(LiquidrodsNode.Block block, Context context, Config config, Writer out) throws IOException {
                for (LiquidrodsNode child : block.getChildren()) {
                    config.defaultRenderer().render(child, context, config, out);
                }
            }
        });

        registerHandler("super", new BlockHandler() {
            @Override
            public boolean wantsCloseTag() {
                return false;
            }

            @Override
            public void render(LiquidrodsNode.Block block, Context context, Config config, Writer out) throws IOException {
                // nop. Mommy Template will take care of me
            }
        });
    }

    private Renderer defaultRenderer = new Renderer() {

        @Override
        public void render(LiquidrodsNode node, Context context, Config config, Writer out) throws IOException {
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
                        out.write(config.escaper.escape(str));
                    }
                }
            } else {
                LiquidrodsNode.Block block = (LiquidrodsNode.Block) node;
                BlockHandler handler = handlers.get(block.getName());
                if (handler == null) {
                    throw new RuntimeException("No handler for block " + block.getName());
                } else {
                    handler.render(block, context, config, out);
                }
            }
        }
    };


    /**
     * Create a configuration by copying another
     *
     * @param config the configuration to clone
     */
    public Config(Config config) {
        this.templateLoader = config.templateLoader;
        this.escaper = config.escaper;
        this.handlers = new HashMap<String, BlockHandler>(config.handlers);
        this.defaultRenderer = config.defaultRenderer;
    }

    /**
     * Construct an instance with usable defaults: a html escaper, a classpath-based template loader and preconfigured default tag handlers (if/else and for)
     */
    public Config() {
        registerDefaultHandlers();
    }

    /**
     * @return the registered handlers
     */
    public Map<String, BlockHandler> handlers() {
        return handlers;
    }

    /**
     * Configure the handlers
     *
     * @param handlers the handlers to use
     * @return self, to enable chaining
     */
    public Config handlers(Map<String, BlockHandler> handlers) {
        this.handlers = handlers;
        return this;
    }

    /**
     * Register a handler for a custom tag, or override an existing one.
     *
     * @param name    the tag name
     * @param handler the handler used to drive the template parsing and rendering.
     * @return self, to enable chaining
     */
    public final Config registerHandler(String name, BlockHandler handler) {
        handlers.put(name, handler);
        return this;
    }

    /**
     * @return the configured template loader
     */
    public Config.TemplateLoader templateLoader() {
        return templateLoader;
    }

    /**
     * Configure a custom template loader
     *
     * @param templateLoader
     * @return self, to enable chaining
     */
    public Config templateLoader(Config.TemplateLoader templateLoader) {
        this.templateLoader = templateLoader;
        return this;
    }

    /**
     * @return the configured escaper
     */
    public Config.Escaper escaper() {
        return escaper;
    }

    /**
     * Configure a custom escaper
     *
     * @param escaper
     * @return self, to enable chaining
     */
    public Config escaper(Config.Escaper escaper) {
        this.escaper = escaper;
        return this;
    }

    /**
     * @return the default renderer, to be used by tag handlers to render other tags
     */
    public Renderer defaultRenderer() {
        return defaultRenderer;
    }

    /**
     * Configures a custom tag renderer
     *
     * @param defaultRenderer the default renderer to use
     * @return self, to enable chaining
     */
    public Config defaultRenderer(Renderer defaultRenderer) {
        this.defaultRenderer = defaultRenderer;
        return this;
    }
}
