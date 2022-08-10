package jump61;

import ucb.gui2.TopLevel;
import ucb.gui2.LayoutSpec;

import java.util.concurrent.ArrayBlockingQueue;

/** The GUI controller for jump61.  To require minimal change to textual
 *  interface, we adopt the strategy of converting GUI input (mouse clicks)
 *  into textual commands that are sent to the Game object through a
 *  a Writer.  The Game object need never know where its input is coming from.
 *  A Display is an Observer of Games and Boards so that it is notified when
 *  either changes.
 *  @author yuxinye
 */
class Display extends TopLevel implements View, CommandSource, Reporter {

    /** A new window with given TITLE displaying GAME, and using COMMANDWRITER
     *  to send commands to the current game. */
    Display(String title) {
        super(title, true);

        addMenuButton("Game->Quit", this::quit);
        addMenuButton("Game->New Game", this::newGame);
        addMenuRadioButton("Options->Players->Red AI",
                "Red", false,  this::setRedAI);
        addMenuRadioButton("Options->Players->Red Manual",
                "Red", true, this::setRedManual);
        addMenuRadioButton("Options->Players->Blue AI",
                "Blue", true,  this::setBlueAI);
        addMenuRadioButton("Options->Players->Blue Manual",
                "Blue", false, this::setBlueManual);
        addMenuButton("Options->Set Seed",  this::setSeed);
        addMenuButton("Options->Resize", this::resize);

        _boardWidget = new BoardWidget(_commandQueue);
        add(_boardWidget, new LayoutSpec("y", 1, "width", 2));
        display(true);
    }

    /** Response to "Quit" button click. */
    void quit(String dummy) {
        System.exit(0);
    }

    /** Response to "New Game" button click. */
    void newGame(String dummy) {
        _commandQueue.offer("new");
    }
    /** Response to "Red AI" button click. */
    void setRedAI(String dummy) {
        _commandQueue.offer("auto red");
    }
    /** Response to "Red manual" button click. */
    void setRedManual(String dummy) {
        _commandQueue.offer("manual red");
    }
    /** Response to "Blue AI" button click. */
    void setBlueAI(String dummy) {
        _commandQueue.offer("auto blue");
    }
    /** Response to "Blue manual" button click. */
    void setBlueManual(String dummy) {
        _commandQueue.offer("manual blue");
    }

    /** Response to "seed" button click. */
    void setSeed(String dummy) {
        String text =
                getTextInput("Enter seed value",
                        "Seed",  "plain", "");
        if (text != null) {
            _commandQueue.offer("seed " + text);
        }
    }
    /** Response to "resize" button click. */
    void resize(String dummy) {
        String text =
                getTextInput("Enter number of rows and columns (2-10)",
                        "Size",  "plain", "");
        if (text != null) {
            if (text.equals("2")) {
                _commandQueue.offer("size 2");
            } else if (text.equals("3")) {
                _commandQueue.offer("size 3");
            } else if (text.equals("4")) {
                _commandQueue.offer("size 4");
            } else if (text.equals("5")) {
                _commandQueue.offer("size 5");
            } else if (text.equals("6")) {
                _commandQueue.offer("size 6");
            } else if (text.equals("7")) {
                _commandQueue.offer("size 7");
            } else if (text.equals("8")) {
                _commandQueue.offer("size 8");
            } else if (text.equals("9")) {
                _commandQueue.offer("size 9");
            } else if (text.equals("10")) {
                _commandQueue.offer("size 10");
            } else {
                showMessage("Inputs must be one integer 2-10 "
                                + "without extra space/characters.",
                        "Error", "error");
            }
        }
    }

    @Override
    public void update(Board board) {
        _boardWidget.update(board);
        pack();
        _boardWidget.repaint(BOARD_UPDATE_INTERVAL);
    }

    @Override
    public String getCommand(String ignored) {
        try {
            return _commandQueue.take();
        } catch (InterruptedException excp) {
            throw new Error("unexpected interrupt");
        }
    }

    @Override
    public void announceWin(Side side) {
        showMessage(String.format("%s wins!", side.toCapitalizedString()),
                    "Game Over", "information");
    }

    @Override
    public void announceMove(int row, int col) {
    }

    @Override
    public void msg(String format, Object... args) {
        showMessage(String.format(format, args), "", "information");
    }

    @Override
    public void err(String format, Object... args) {
        showMessage(String.format(format, args), "Error", "error");
    }

    /** Time interval in msec to wait after a board update. */
    static final long BOARD_UPDATE_INTERVAL = 50;

    /** The widget that displays the actual playing board. */
    private BoardWidget _boardWidget;

    /** Queue for commands going to the controlling Game. */
    private final ArrayBlockingQueue<String> _commandQueue =
        new ArrayBlockingQueue<>(5);
}
