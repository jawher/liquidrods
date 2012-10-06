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

    private final String filename;
    private final Map<String, BlockHandler> handlers;

    public static class Token {
        private enum Type {
            TEXT, OPEN_VAR, OPEN_TAG, CLOSE_VAR, CLOSE_TAG, OPEN_RAW_VAR, CLOSE_RAW_VAR, EOF
        }

        public final Type type;
        public final String value;
        public final String line;
        public final int row;
        public final int col;

        private Token(Type type, String value, String line, int row, int col) {
            this.type = type;
            this.value = value;
            this.line = line;
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
    private int row = 0, col = 0;

    /**
     * @param reader   the reader to be parsed
     * @param handlers The tag handlers keyed by the tag they handle. Used in the parsing to handle body and bodyless tags
     */
    public LiquidrodsParser(Reader reader, String filename, Map<String, BlockHandler> handlers) {
        this.filename = filename;
        this.handlers = handlers;
        this.reader = new BufferedReader(reader);
    }

    private void nextLine() {
        try {
            line = reader.readLine();
            if (row > 0 && line != null) {
                line = "\n" + line;
            }
            row++;
            col = 0;
            if (line == null) {
                eof = new Token(Token.Type.EOF, "$", null, row, col);
            } else {
                lineMatcher = DELIMITERS.matcher(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Token nextToken() {
        StringBuilder text = new StringBuilder();
        String textFirstLine = null;
        int textCol = -1, textRow = -1;
        while (true) {
            if (eof != null) {
                return eof;
            }

            if (line == null || col >= line.length()) {
                nextLine();
                if (eof != null) {
                    if (text.length() > 0) {
                        return new Token(Token.Type.TEXT, text.toString(), textFirstLine, textRow, textCol);
                    } else {
                        return eof;
                    }
                }
            }

            if (lineMatcher.find(col)) {
                String caught = lineMatcher.group();
                if (lineMatcher.start() > col || text.length() > 0) {
                    text.append(line.substring(col, lineMatcher.start()));
                    final Token res = new Token(Token.Type.TEXT, text.toString(), line, row, col);
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
                        throw new ParseException("Bug !", filename, line, row, col);
                    }
                    Token res = new Token(type, caught, line, row, col);
                    col = lineMatcher.end();
                    return res;
                }
            } else {
                if (text.length() == 0) {
                    textFirstLine = line;
                    textCol = col;
                    textRow = row;
                }
                text.append(line.substring(col));

                line = null;
            }
        }
    }

    private Token current;

    private static class SectionData {
        public final String name;
        public final String arg;
        public final Token posToken;

        private SectionData(String name, String arg, Token posToken) {
            this.name = name;
            this.arg = arg;
            this.posToken = posToken;
        }
    }

    private List<SectionData> sectionsStack = new ArrayList<SectionData>();

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
            throw new ParseException("Was expecting EOF but got " + current, filename, current.line, row, col);
        }
        return rootNodes;
    }

    private List<LiquidrodsNode> start() {
        List<LiquidrodsNode> nodes = new ArrayList<LiquidrodsNode>();
        while (true) {
            advance();
            if (is(Token.Type.TEXT)) {
                nodes.add(new LiquidrodsNode.Text(current.value, filename, row, col));
            } else if (is(Token.Type.OPEN_RAW_VAR)) {
                Token posToken = current;
                advance();
                if (is(Token.Type.TEXT)) {
                    String name = current.value.trim();
                    if (name.isEmpty()) {
                        throw new ParseException("Variable name cannot be empty", filename, current);
                    }
                    nodes.add(new LiquidrodsNode.Variable(name, true, filename, posToken.row, posToken.col));
                } else {
                    throw new ParseException("Was expecting a variable name but got " + current, filename, current);
                }
                advance();
                if (!is(Token.Type.CLOSE_RAW_VAR)) {
                    throw new ParseException("Was expecting close brackets '}}}' but got " + current, filename, current);
                }
            } else if (is(Token.Type.OPEN_VAR)) {
                Token posToken = current;
                advance();

                if (is(Token.Type.TEXT)) {
                    nodes.add(new LiquidrodsNode.Variable(current.value.trim(), false, filename, posToken.row, posToken.col));
                } else {
                    throw new ParseException("Was expecting a variable name but got " + current, filename, current);
                }
                advance();
                if (!is(Token.Type.CLOSE_VAR)) {
                    throw new ParseException("Was expecting close brackets '}}' but got " + current, filename, current);
                }
            } else if (is(Token.Type.OPEN_TAG)) {
                Token posToken = current;
                String name;
                String arg = null;
                advance();
                if (is(Token.Type.TEXT)) {

                    String[] names = current.value.trim().split("\\s+");
                    if (names.length != 1 && names.length != 2) {
                        throw new ParseException("tags must have a name and at most *one* optional arg", filename, current);
                    }
                    name = names[0].trim();
                    if (name.isEmpty()) {
                        throw new ParseException("A tag name must be specified", filename, posToken.line, row, col);
                    }
                    if (names.length > 1 && !names[1].trim().isEmpty()) {
                        arg = names[1].trim();
                    }

                } else {
                    throw new ParseException("Was expecting a tag name but got " + current, filename, current);
                }

                advance();
                if (!is(Token.Type.CLOSE_TAG)) {
                    throw new ParseException("Was expecting '%}' but got " + current, filename, current);
                }

                if ("end".equals(name)) {
                    if (sectionsStack.isEmpty()) {
                        throw new ParseException("Unexpected end tag found: no currently open tags", filename, posToken);
                    } else {
                        SectionData section = popSection();
                        if (arg != null && !arg.equals(section.name)) {
                            throw new ParseException("Unbalanced close tag found: tries to close the '" + arg + "' tag whereas the currently open tag is named '" + section.name + "' (defined @ " + section.posToken.row + ":" + section.posToken.col + ")", filename, posToken);
                        }
                        return Arrays.<LiquidrodsNode>asList(new LiquidrodsNode.Block(section.name, section.arg, nodes, filename, section.posToken.row, section.posToken.col));
                    }
                } else {
                    BlockHandler handler = handlers.get(name);
                    if (handler != null && !handler.wantsCloseTag()) {
                        nodes.add(new LiquidrodsNode.Block(name, arg, Collections.<LiquidrodsNode>emptyList(), filename, posToken.row, posToken.col));
                    } else {
                        pushSection(new SectionData(name, arg, posToken));
                        nodes.addAll(start());
                    }
                }
            } else {
                if (!sectionsStack.isEmpty()) {
                    SectionData section = popSection();
                    throw new ParseException("Unexpected end of file: was expecting end tag for the tag '" + section.name + "' opened @ (" + section.posToken.row + ", " + section.posToken.col + ")", filename, current.line, row, col);
                }
                return nodes;
            }
        }
    }

    private void pushSection(SectionData section) {
        sectionsStack.add(section);
    }

    private SectionData popSection() {
        SectionData res = sectionsStack.get(sectionsStack.size() - 1);
        sectionsStack.remove(sectionsStack.size() - 1);
        return res;
    }
}
