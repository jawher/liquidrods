package liquidrods;

import java.io.IOException;
import java.io.Writer;

public interface BlockHandler {
    boolean wantsCloseTag();
    void render(LiquidrodsNode node, Context context, BlockHandler defaultBlockHandler, Writer out) throws IOException;
}
