package liquidrods;

import java.util.List;

/**
 * Represents a node in the template DOM. Not to be confused with the HTML/XML DOM as Liquidrods works with any textual input.
 */
public abstract class LiquidrodsNode {
    protected String filename;
    protected int row, col;

    /**
     * The filename this node appears in
     * @return The filename this node appears in
     */
    public String getFilename() {
        return filename;
    }

    /**
     * The row of this node (1-indexed)
     *
     * @return The row of this node (1-indexed)
     */
    public int getRow() {
        return row;
    }

    /**
     * The col of this node (0-indexed)
     *
     * @return The col of this node (0-indexed)
     */
    public int getCol() {
        return col;
    }

    /**
     * Represents a text chunk from the template.
     */
    public static class Text extends LiquidrodsNode {
        private String value;

        /**
         *
         * @param value the textual value
         * @param filename the file this node appears in
         * @param row the node row
         * @param col the node col
         */
        public Text(String value, String filename, int row, int col) {
            this.value = value;
            this.filename = filename;
            this.row = row;
            this.col = col;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Represents a variable in the template.
     */
    public static class Variable extends LiquidrodsNode {
        private String name;
        private boolean raw;

        /**
         * @param name the variable name
         * @param raw  whether the variable value is to be rendered as is (raw) or escaped
         * @param filename the file this node appears in
         * @param row the node row
         * @param col the node col
         */
        public Variable(String name, boolean raw, String filename, int row, int col) {
            this.name = name;
            this.raw = raw;
            this.filename = filename;
            this.row = row;
            this.col = col;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isRaw() {
            return raw;
        }

        public void setRaw(boolean raw) {
            this.raw = raw;
        }

        @Override
        public String toString() {
            return "{{" + name + "}}";
        }
    }

    /**
     * Represents a tag in the template.
     */
    public static class Block extends LiquidrodsNode {
        private String name;
        private String arg;
        private List<LiquidrodsNode> children;

        /**
         * @param name     the tag name
         * @param arg      an optional tag parameter
         * @param children the tag's body
         * @param filename the file this node appears in
         * @param row the node row
         * @param col the node col
         */
        public Block(String name, String arg, List<LiquidrodsNode> children, String filename, int row, int col) {
            this.name = name;
            this.arg = arg;
            this.children = children;
            this.filename = filename;
            this.row = row;
            this.col = col;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArg() {
            return arg;
        }

        public void setArg(String arg) {
            this.arg = arg;
        }

        public List<LiquidrodsNode> getChildren() {
            return children;
        }

        public void setChildren(List<LiquidrodsNode> children) {
            this.children = children;
        }

        @Override
        public String toString() {
            return "{%" + name + (arg == null ? "" : " " + arg) + "%}\n\t" + children + "\n{% end " + name + "%}";
        }
    }
}
