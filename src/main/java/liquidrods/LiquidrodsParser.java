package liquidrods;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The template files parser
 */
public class LiquidrodsParser {

    private final Map<String, BlockHandler> handlers;

    private static class Token {
        private enum Type {
            TEXT, OPEN_VAR, OPEN_TAG, CLOSE_VAR, CLOSE_TAG, OPEN_RAW_VAR, CLOSE_RAW_VAR, EOF
        }

        public final Type type;
        public final String value;
        public final int row;
        public final int col;

        private Token(Type type, String value, int row, int col) {
            this.type = type;
            this.value = value;
            this.row = row;
            this.col = col;
        }

        @Override
        public String toString() {
            return type + ": '" + value + "' @ " + row + ":" + col;
        }
    }

    private static final String OPEN_RAW_VAR_S = "{{{";
    private static final String OPEN_VAR_S = "{{";
    private static final String CLOSE_RAW_VAR_S = "}}}";
    private static final String CLOSE_VAR_S = "}}";
    private static final String OPEN_TAG_S = "{%";
    private static final String CLOSE_TAG_S = "%}";

    private static final Pattern DELIMITERS = Pattern.compile(
            Pattern.quote(OPEN_RAW_VAR_S) + "|" +
                    Pattern.quote(CLOSE_RAW_VAR_S) + "|" +
                    Pattern.quote(OPEN_VAR_S) + "|" +
                    Pattern.quote(CLOSE_VAR_S) + "|" +
                    Pattern.quote(OPEN_TAG_S) + "|" +
                    Pattern.quote(CLOSE_TAG_S));
    private BufferedReader reader;
    private String line;
    private Matcher lineMatcher;
    private Token eof = null;
    private int row = 1, col = 0;

    /**
     * @param reader   the reader to be parsed
     * @param handlers The tag handlers keyed by the tag they handle. Used in the parsing to handle body and bodyless tags
     */
    public LiquidrodsParser(Reader reader, Map<String, BlockHandler> handlers) {
        this.handlers = handlers;
        this.reader = new BufferedReader(reader);
    }

    private void nextLine() {
        try {
            line = reader.readLine();
            if (row > 1 && line != null) {
                line = "\n" + line;
            }
            row++;
            col = 0;
            if (line == null) {
                eof = new Token(Token.Type.EOF, "$", row, col);
            } else {
                lineMatcher = DELIMITERS.matcher(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Token nextToken() {
        StringBuilder text = new StringBuilder();
        while (true) {
            if (eof != null) {
                return eof;
            }

            if (line == null || col >= line.length()) {
                nextLine();
                if (eof != null) {
                    if (text.length() > 0) {
                        return new Token(Token.Type.TEXT, text.toString(), col, row);//FIXME: col, row
                    } else {
                        return eof;
                    }
                }
            }

            if (lineMatcher.find(col)) {
                String caught = lineMatcher.group();
                if (lineMatcher.start() > col || text.length() > 0) {
                    text.append(line.substring(col, lineMatcher.start()));
                    final Token res = new Token(Token.Type.TEXT, text.toString(), col, row);
                    col = lineMatcher.start();
                    return res;
                } else {
                    Token.Type type;
                    if (caught.equals(OPEN_RAW_VAR_S)) {
                        type = Token.Type.OPEN_RAW_VAR;
                    } else if (caught.equals(CLOSE_RAW_VAR_S)) {
                        type = Token.Type.CLOSE_RAW_VAR;
                    } else if (caught.equals(OPEN_VAR_S)) {
                        type = Token.Type.OPEN_VAR;
                    } else if (caught.equals(CLOSE_VAR_S)) {
                        type = Token.Type.CLOSE_VAR;
                    } else if (caught.equals(OPEN_TAG_S)) {
                        type = Token.Type.OPEN_TAG;
                    } else if (caught.equals(CLOSE_TAG_S)) {
                        type = Token.Type.CLOSE_TAG;
                    } else {
                        throw new RuntimeException("bug !");
                    }
                    Token res = new Token(type, caught, row, col);
                    col = lineMatcher.end();
                    return res;
                }
            } else {
                text.append(line.substring(col));
                line = null;
            }
        }
    }

    private Token current;
    private List<String[]> sectionsStack = new ArrayList<String[]>();

    private void advance() {
        current = nextToken();
    }

    private boolean is(Token.Type type) {
        return current.type == type;
    }

    /**
     * Parses the template into a list of root nodes
     *
     * @return the template DOM
     */
    public List<LiquidrodsNode> parse() {
        final List<LiquidrodsNode> rootNodes = start();
        if (!is(Token.Type.EOF)) {
            throw new RuntimeException("Was expecting EOF but got " + current);
        }
        return rootNodes;
    }

    private List<LiquidrodsNode> start() {
        List<LiquidrodsNode> nodes = new ArrayList<LiquidrodsNode>();
        while (true) {
            advance();
            if (is(Token.Type.TEXT)) {
                nodes.add(new LiquidrodsNode.Text(current.value));
            } else if (is(Token.Type.OPEN_RAW_VAR)) {
                advance();
                if (is(Token.Type.TEXT)) {
                    nodes.add(new LiquidrodsNode.Variable(current.value.trim(), true));
                } else {
                    throw new RuntimeException("Was expecting a variable id but got " + current);
                }
                advance();
                if (!is(Token.Type.CLOSE_RAW_VAR)) {
                    throw new RuntimeException("Was expecting a close pair but got " + current);
                }
            } else if (is(Token.Type.OPEN_VAR)) {
                advance();
                if (is(Token.Type.TEXT)) {
                    nodes.add(new LiquidrodsNode.Variable(current.value.trim(), false));
                } else {
                    throw new RuntimeException("Was expecting a variable id but got " + current);
                }
                advance();
                if (!is(Token.Type.CLOSE_VAR)) {
                    throw new RuntimeException("Was expecting a close pair but got " + current);
                }
            } else if (is(Token.Type.OPEN_TAG)) {
                String name;
                String arg = null;
                advance();
                if (is(Token.Type.TEXT)) {
                    String[] names = current.value.trim().split("\\s+");
                    if (names.length != 1 && names.length != 2) {
                        throw new RuntimeException("tags can have a name and an optional arg in ''" + current);
                    }
                    name = names[0];
                    if (names.length > 1) {
                        arg = names[1];
                    }
                } else {
                    throw new RuntimeException("Was expecting a section name but got " + current);
                }

                advance();
                if (!is(Token.Type.CLOSE_TAG)) {
                    throw new RuntimeException("Was expecting %} but got " + current);
                }

                if ("end".equals(name)) {
                    if (sectionsStack.isEmpty()) {
                        throw new RuntimeException("Unexpected end tag found: no matching open tag " + current);
                    } else {
                        String[] openSectionData = popSectionName();
                        String openSectionName = openSectionData[0];
                        String openSectionArg = openSectionData[1];
                        if (arg != null && !arg.equals(openSectionName)) {
                            throw new RuntimeException("Unbalanced close tag found " + arg + " (was expecting close tag for " + openSectionName + ")");
                        }
                        return Arrays.<LiquidrodsNode>asList(new LiquidrodsNode.Block(openSectionName, openSectionArg, nodes));
                    }
                } else {
                    BlockHandler handler = handlers.get(name);
                    if (handler != null && !handler.wantsCloseTag()) {
                        nodes.add(new LiquidrodsNode.Block(name, arg, Collections.<LiquidrodsNode>emptyList()));
                    } else {
                        pushSectionName(name, arg);
                        nodes.addAll(start());
                    }
                }
            } else {
                if (!sectionsStack.isEmpty()) {
                    throw new RuntimeException("Unexpected end of file: was expecting end tag for {% " + popSectionName()[0]+" %}");
                }
                return nodes;
            }
        }
    }

    private void pushSectionName(String name, String arg) {
        sectionsStack.add(new String[]{name, arg});
    }

    private String[] popSectionName() {
        String[] res = sectionsStack.get(sectionsStack.size() - 1);
        sectionsStack.remove(sectionsStack.size() - 1);
        return res;
    }

}
