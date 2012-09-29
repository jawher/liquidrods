package liquidrods;

import java.util.List;

/**
 * Represents a node in the template DOM. Not to be confused with the HTML/XML DOM as Liquidrods works with any textual input.
 */
public abstract class LiquidrodsNode {

    /**
     * Represents a text chunk from the template.
     */
    public static class Text extends LiquidrodsNode {
        private String value;

        public Text(String value) {
            this.value = value;
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
         */
        public Variable(String name, boolean raw) {
            this.name = name;
            this.raw = raw;
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
         */
        public Block(String name, String arg, List<LiquidrodsNode> children) {
            this.name = name;
            this.arg = arg;
            this.children = children;
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
