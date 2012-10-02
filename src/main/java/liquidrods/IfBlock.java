package liquidrods;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

/**
 * The handler for the if tag. Takes a parameter that'll be evaluated using the current context to decide whether to render its body or not.
 * <p/>
 * If the parameter evaluates to a boolean, it is used as is for the test. Otherwise, null is considered as false and all other values as true.
 * <p/>
 * This implementation can be inverted (to implement if not or unless) by calling {@link liquidrods.IfBlock#inverted()}
 */
public class IfBlock implements BlockHandler {

    private boolean invert = false;

    /**
     * Inverts this instance to become ifnot or unless
     *
     * @return self, for chaining
     */
    public IfBlock inverted() {
        this.invert = true;
        return this;
    }

    @Override
    public boolean wantsCloseTag() {
        return true;
    }

    @Override
    public void render(LiquidrodsNode.Block block, Context context, Config config, Writer out) throws IOException {
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

        if (invert) {
            doit = !doit;
        }
        for (LiquidrodsNode child : block.getChildren()) {
            if (child instanceof LiquidrodsNode.Block && ("else".equals(((LiquidrodsNode.Block) child).getName()))) {
                if (doit) {
                    return;
                }
                doit = !doit;
            } else if (doit) {
                config.defaultRenderer().render(child, context, config, out);
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
        public void render(LiquidrodsNode.Block block, Context context, Config config, Writer out) throws IOException {
            // nop. Daddy if will do just fine on his own.
        }
    }
}