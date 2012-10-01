package liquidrods;

import java.io.IOException;
import java.io.Writer;

/**
 * Implement this interface to provide a new tag. The implementation class should be registered with {@link Config#registerHandler(String, BlockHandler)}.
 */
public interface BlockHandler {
    /**
     * Whether this tag has a body (and hence requires a close tag or not)
     *
     * @return true or false
     */
    boolean wantsCloseTag();

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
