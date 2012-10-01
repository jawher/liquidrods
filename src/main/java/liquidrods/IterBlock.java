package liquidrods;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

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
 * <p/>
 * <ul>
 * <li><code>#</code>: 0-based index</li>
 * <li><code>##</code>: 1-based index</li>
 * <li><code>#first</code>: true for the first iteration, false otherwise</li>
 * <li><code>#last</code>: true for the last iteration, false otherwise</li>
 * </ul>
 */
public class IterBlock implements BlockHandler {

    @Override
    public boolean wantsCloseTag() {
        return true;
    }

    @Override
    public void render(LiquidrodsNode.Block block, Context context, Config config, Writer out) throws IOException {
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
                config.defaultRenderer().render(child, subContext, config, out);
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