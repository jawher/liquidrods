package liquidrods;

import java.io.*;
import java.util.*;

/**
 * The API's entry point. Used to parse templates and provides a default configuration
 *
 * @see Config
 * @see Template
 */
public class Liquidrods {

    private static Config defaultConfig = new Config();

    private Liquidrods() {
    }

    /**
     * Parse a template from its logical name (uses the {@link Config.TemplateLoader}) and configures it with a default configuration
     *
     * @param name the template logical name
     * @return a parsed, ready for use template
     */
    public static Template parse(String name) {
        return parse(defaultConfig.templateLoader().load(name));
    }

    /**
     * Parse a template from its logical name (uses the {@link Config.TemplateLoader}) and configures it with the supplied configuration
     *
     * @param name   the template logical name
     * @param config a custom configuration to be used by the returned template
     * @return a parsed, ready for use template
     */
    public static Template parse(String name, Config config) {
        return parse(config.templateLoader().load(name), config);
    }

    /**
     * Parses a template from a reader and configures it with a default configuration
     *
     * @param reader the template reader
     * @return a parsed, ready for use template
     */
    public static Template parse(Reader reader) {
        List<LiquidrodsNode> rootNodes = new LiquidrodsParser(reader, defaultConfig.handlers()).parse();
        return new Template(rootNodes, defaultConfig);
    }

    /**
     * Parses a template from a reader and configures it with the supplied configuration
     *
     * @param reader the template reader
     * @param config a custom configuration to be used by the returned template
     * @return a parsed, ready for use template
     */
    public static Template parse(Reader reader, Config config) {
        List<LiquidrodsNode> rootNodes = new LiquidrodsParser(reader, config.handlers()).parse();
        return new Template(rootNodes, config);
    }
}
