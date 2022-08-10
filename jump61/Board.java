package jump61;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Formatter;

import java.util.function.Consumer;

import static jump61.Side.*;

/** Represents the state of a Jump61 game.  Squares are indexed either by
 *  row and column (between 1 and size()), or by square number, numbering
 *  squares by rows, with squares in row 1 numbered from 0 to size()-1, in
 *  row 2 numbered from size() to 2*size() - 1, etc. (i.e., row-major order).
 *
 *  A Board may be given a notifier---a Consumer<Board> whose
 *  .accept method is called whenever the Board's contents are changed.
 *
 *  @author yuxinye
 */
class Board {

    /** An uninitialized Board.  Only for use by subtypes. */
    protected Board() {
        _notifier = NOP;
    }

    /** An N x N board in initial configuration. */
    Board(int N) {
        this();
        _size = N;
        _board = new Square[_size * _size];
        for (int i = 0; i < N * N; i++) {
            _board[i] = Square.INITIAL;
        }
    }

    /** A board whose initial contents are copied from BOARD0, but whose
     *  undo history is clear, and whose notifier does nothing. */
    Board(Board board0) {
        this(board0.size());
        copy(board0);
        _readonlyBoard = new ConstantBoard(this);
    }

    /** Returns a readonly version of this board. */
    Board readonlyBoard() {
        return _readonlyBoard;
    }

    /** (Re)initialize me to a cleared board with N es on a side. Clears
     *  the undo history and sets the number of moves to 0. */
    void clear(int N) {
        _board = new Square[N * N];
        _size = N;
        for (int i = 0; i < _board.length; i++) {
            _board[i] = Square.INITIAL;
        }
        _history.clear();
        _current = 0;
        announce();
    }

    /** Copy the contents of BOARD into me. */
    void copy(Board board) {
        _board = new Square[board.size() * board.size()];
        for (int i = 0; i < _board.length; i++) {
            _board[i] = board.get(i);
        }
        _history.clear();
        _current = 0;
    }

    /** Copy the contents of BOARD into me, without modifying my undo
     *  history. Assumes BOARD and I have the same size. */
    private void internalCopy(Board board) {
        assert size() == board.size();
        for (int i = 0; i < _board.length; i++) {
            _board[i] = board.get(i);
        }
    }

    /** Return the number of rows and of columns of THIS. */
    int size() {
        return _size;
    }

    /** Returns the contents of the square at row R, column C
     *  1 <= R, C <= size (). */
    Square get(int r, int c) {
        if (exists(r, c)) {
            return get(sqNum(r, c));
        }
        throw new GameException("Invalid number of columns and rows");
    }

    /** Returns the contents of square #N, numbering squares by rows, with
     *  squares in row 1 number 0 - size()-1, in row 2 numbered
     *  size() - 2*size() - 1, etc. */
    Square get(int n) {
        if (!exists(n)) {
            throw new GameException("Index out of bounds.");
        }
        return _board[n];
    }

    /** Returns the total number of spots on the board. */
    int numPieces() {
        int sum = 0;
        for (int i = 0; i < _board.length; i++) {
            sum = sum + this.get(i).getSpots();
        }
        return sum;
    }

    /** Returns the Side of the player who would be next to move.  If the
     *  game is won, this will return the loser (assuming legal position). */
    Side whoseMove() {
        return ((numPieces() + size()) & 1) == 0 ? RED : BLUE;
    }

    /** Return true iff row R and column C denotes a valid square. */
    final boolean exists(int r, int c) {
        return 1 <= r && r <= size() && 1 <= c && c <= size();
    }

    /** Return true iff S is a valid square number. */
    final boolean exists(int s) {
        int N = size();
        return 0 <= s && s < N * N;
    }

    /** Return the row number for square #N. */
    final int row(int n) {
        return n / size() + 1;
    }

    /** Return the column number for square #N. */
    final int col(int n) {
        return n % size() + 1;
    }

    /** Return the square number of row R, column C. */
    final int sqNum(int r, int c) {
        return (c - 1) + (r - 1) * size();
    }

    /** Return a string denoting move (ROW, COL)N. */
    String moveString(int row, int col) {
        return String.format("%d %d", row, col);
    }

    /** Return a string denoting move N. */
    String moveString(int n) {
        return String.format("%d %d", row(n), col(n));
    }

    /** Returns true iff it would currently be legal for PLAYER to add a spot
     to square at row R, column C. */
    boolean isLegal(Side player, int r, int c) {
        return isLegal(player, sqNum(r, c));
    }

    /** Returns true iff it would currently be legal for PLAYER to add a spot
     *  to square #N. */
    boolean isLegal(Side player, int n) {
        if (isLegal(player)) {
            if (_board[n].getSide().opposite() != player && exists(n)) {
                return true;
            }
        }
        return false;
    }

    /** Returns true iff PLAYER is allowed to move at this point. */
    boolean isLegal(Side player) {
        if (getWinner() == null && player == whoseMove()) {
            return true;
        }
        return false;
    }

    /** Returns the winner of the current position, if the game is over,
     *  and otherwise null. */
    final Side getWinner() {
        if (numOfSide(RED) == _board.length) {
            return RED;
        } else if (numOfSide(BLUE) == _board.length) {
            return BLUE;
        } else {
            return null;
        }
    }

    /** Return the number of squares of given SIDE. */
    int numOfSide(Side side) {
        int sum = 0;
        for (int i = 0; i < _board.length; i++) {
            if (_board[i].getSide().equals(side)) {
                sum++;
            }
        }
        return sum;
    }

    /** Add a spot from PLAYER at row R, column C.  Assumes
     *  isLegal(PLAYER, R, C). */
    void addSpot(Side player, int r, int c) {
        if (!isLegal(player, r, c)) {
            throw new GameException("Illegal to add a spot.");
        }
        markUndo();
        simpleAdd(player, r, c, 1);
        if (get(r, c).getSpots() > neighbors(r, c)) {
            jump(sqNum(r, c));
        }
        announce();
    }

    /** Add a spot from PLAYER at square #N.  Assumes isLegal(PLAYER, N). */
    void addSpot(Side player, int n) {
        if (!isLegal(player, n)) {
            throw new GameException("Illegal to add a spot.");
        }
        markUndo();
        simpleAdd(player, n, 1);
        if (get(n).getSpots() > neighbors(n)) {
            jump(n);
        }
        announce();
    }

    /** Set the square at row R, column C to NUM spots (0 <= NUM), and give
     *  it color PLAYER if NUM > 0 (otherwise, white). */
    void set(int r, int c, int num, Side player) {
        internalSet(r, c, num, player);
        announce();
    }

    /** Set the square at row R, column C to NUM spots (0 <= NUM), and give
     *  it color PLAYER if NUM > 0 (otherwise, white).  Does not announce
     *  changes. */
    private void internalSet(int r, int c, int num, Side player) {
        internalSet(sqNum(r, c), num, player);
    }

    /** Set the square #N to NUM spots (0 <= NUM), and give it color PLAYER
     *  if NUM > 0 (otherwise, white). Does not announce changes. */
    private void internalSet(int n, int num, Side player) {
        if (num > 0) {
            _board[n] = Square.square(player, num);
        } else {
            _board[n] =  Square.square(WHITE, num);
        }
    }

    /** Undo the effects of one move (that is, one addSpot command).  One
     *  can only undo back to the last point at which the undo history
     *  was cleared, or the construction of this Board. */
    void undo() {
        if (_current > 0) {
            _current -= 1;
            this.internalCopy(_history.get(_current));
        }
    }

    /** Record the beginning of a move in the undo history. */
    private void markUndo() {
        Board copyOfBoard = new Board(size());
        copyOfBoard.copy(this);
        _history.add(copyOfBoard);
        _current++;
    }

    /** Add DELTASPOTS spots of side PLAYER to row R, column C,
     *  updating counts of numbers of squares of each color. */
    private void simpleAdd(Side player, int r, int c, int deltaSpots) {
        internalSet(r, c, deltaSpots + get(r, c).getSpots(), player);
    }

    /** Add DELTASPOTS spots of color PLAYER to square #N,
     *  updating counts of numbers of squares of each color. */
    private void simpleAdd(Side player, int n, int deltaSpots) {
        internalSet(n, deltaSpots + get(n).getSpots(), player);
    }

    /** Used in jump to keep track of squares needing processing.  Allocated
     *  here to cut down on allocations. */
    private final ArrayDeque<Integer> _workQueue = new ArrayDeque<>();

    /** Do all jumping on this board, assuming that initially, S is the only
     *  square that might be over-full. */
    private void jump(int S) {
        _workQueue.clear();
        set(row(S), col(S), 1, get(S).getSide());
        for (int i = 0; i < neighborList(S).size(); i++) {
            setColor(neighborList(S).get(i), get(S).getSide());
            simpleAdd(get(S).getSide(), neighborList(S).get(i), 1);
            _workQueue.add(neighborList(S).get(i));
        }
        while (getWinner() == null && !_workQueue.isEmpty()) {
            int check = _workQueue.pop();
            if (get(check).getSpots() > neighbors(check)) {
                set(row(check), col(check), 1, get(check).getSide());
                for (int i = 0; i < neighborList(check).size(); i++) {
                    setColor(check, get(check).getSide());
                    simpleAdd(get(check).getSide(),
                            neighborList(check).get(i), 1);
                    _workQueue.add(neighborList(check).get(i));
                }
            }
        }
    }

    /** Set color of a square in the board.
     * N is square position
     * COLOR is player side
     */
    private void setColor(int n, Side color) {
        _board[n] = Square.square(color, get(n).getSpots());
    }

    /** Add neighbors to a list.
     * N is the position
     * @return NEIGHBORS
     * */
    private ArrayList<Integer> neighborList(int n) {
        ArrayList<Integer> neighbors = new ArrayList<>();
        if (row(n) != 1) {
            neighbors.add(n - size());
        }
        if (row(n) != size()) {
            neighbors.add(n + size());
        }
        if (col(n) != 1) {
            neighbors.add(n - 1);
        }
        if (col(n) != size()) {
            neighbors.add(n + 1);
        }
        return neighbors;
    }

    /** Returns my dumped representation. */
    @Override
    public String toString() {
        Formatter out = new Formatter();
        out.format("===%n");
        for (int i = 1; i <= size(); i++) {
            out.format("    ");
            for (int j = 1; j <= size(); j++) {
                if (get(i, j).equals(Square.INITIAL)) {
                    out.format("%d%c ", 1, '-');
                } else if (get(i, j).getSide().equals(RED)) {
                    out.format("%d%c ", get(i, j).getSpots(), 'r');
                } else if (get(i, j).getSide().equals(BLUE)) {
                    out.format("%d%c ", get(i, j).getSpots(), 'b');
                }
            }
            out.format("%n");
        }
        out.format("===");
        if (getWinner() != null) {
            if (getWinner().equals(RED)) {
                out.format("%s wins.", RED);
            } else if (getWinner().equals(BLUE)) {
                out.format("%s wins.", BLUE);
            }
        }

        return out.toString();
    }

    /** Returns an external rendition of me, suitable for human-readable
     *  textual display, with row and column numbers.  This is distinct
     *  from the dumped representation (returned by toString). */
    public String toDisplayString() {
        String[] lines = toString().trim().split("\\R");
        Formatter out = new Formatter();
        for (int i = 1; i + 1 < lines.length; i += 1) {
            out.format("%2d %s%n", i, lines[i].trim());
        }
        out.format("  ");
        for (int i = 1; i <= size(); i += 1) {
            out.format("%3d", i);
        }
        return out.toString();
    }

    /** Returns the number of neighbors of the square at row R, column C. */
    int neighbors(int r, int c) {
        int size = size();
        int n;
        n = 0;
        if (r > 1) {
            n += 1;
        }
        if (c > 1) {
            n += 1;
        }
        if (r < size) {
            n += 1;
        }
        if (c < size) {
            n += 1;
        }
        return n;
    }

    /** Returns the number of neighbors of square #N. */
    int neighbors(int n) {
        return neighbors(row(n), col(n));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Board)) {
            return false;
        } else {
            Board B = (Board) obj;
            if (B.size() != this.size()) {
                return false;
            }
            for (int i = 0; i < _board.length; i++) {
                if (B.get(i).getSide() != this.get(i).getSide()
                        || B.get(i).getSpots() != this.get(i).getSpots()) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public int hashCode() {
        return numPieces();
    }

    /** Set my notifier to NOTIFY. */
    public void setNotifier(Consumer<Board> notify) {
        _notifier = notify;
        announce();
    }

    /** Take any action that has been set for a change in my state. */
    private void announce() {
        _notifier.accept(this);
    }

    /** A notifier that does nothing. */
    private static final Consumer<Board> NOP = (s) -> { };

    /** A read-only version of this Board. */
    private ConstantBoard _readonlyBoard;

    /** Use _notifier.accept(B) to announce changes to this board. */
    private Consumer<Board> _notifier;

    /** A 1-D array representing the board. */
    private Square[] _board;

    /** Size of the board(number of row/col). */
    private int _size;

    /** Current position in _history. */
    private int _current;

    /** A list storing previous board states. */
    private ArrayList<Board> _history = new ArrayList<>();
}
