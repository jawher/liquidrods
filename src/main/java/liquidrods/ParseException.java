package liquidrods;

public class ParseException extends RuntimeException {
    private final String message;
    private String filename;
    private String line;
    private int row, col;

    public ParseException(String message, String filename, String line, int row, int col) {
        this.message = message;
        this.filename = filename;
        this.line = line;
        this.row = row;
        this.col = col;
    }

    public ParseException(String message, String filename, LiquidrodsParser.Token where) {
        this.message = message;
        this.filename = filename;
        this.line = where.line;
        this.row = where.row;
        this.col = where.col;
    }

    @Override
    public String getMessage() {
        StringBuilder res = new StringBuilder(message);
        res.append(" in ").append(filename).append(" @ ").append(row).append(":").append(col);
        if (line != null) {
            res.append("\n").append(line).append("\n");
            for (int i = 0; i < col; i++) {
                res.append(" ");
            }
            res.append("^");
        }
        return res.toString();
    }


}
