package com.github.jhoenicke.javacup;

/**
 * This class represents a terminal symbol in the grammar. Each terminal has a
 * textual name, an index, and a string which indicates the type of object it
 * will be implemented with at runtime (i.e. the class of object that will be
 * returned by the scanner and pushed on the parse stack to represent it).
 *
 * @version last updated: 7/3/96
 * @author Frank Flannery
 */
public class Terminal extends GrammarSymbol {

	private int level;
	private int associativity;

	/**
	 * Full constructor.
	 * 
	 * @param name the name of the terminal.
	 * @param type the type of the terminal.
	 * @param associativity the associativity of the terminal.
	 * @param level the level of the priority for the terminal.
	 * @param index the index of the terminal.
	 */
	public Terminal(String name, String type, int associativity, int level, int index) {
		super(name, type, index);
		this.level = level;
		this.associativity = associativity;
	}

	/**
	 * Constructor for terminal without precedence
     *
	 * @param name the name of the terminal.
	 * @param type the type of the terminal.
	 * @param index the index of the terminal.
	 */

	public Terminal(String name, String type, int index) {
		this(name, type, Assoc.NOPREC, -1, index);
	}

	/**
	 * Constructor with type less terminal.
	 * 
	 * @param name the name of the terminal.
	 * @param index the index of the terminal.
	 */
	public Terminal(String name, int index) {
		this(name, null, index);
	}

	/** Special terminal for end of input. */
	public static Terminal EOF = new Terminal("EOF", 1);

	/** special terminal used for error recovery */
	public static Terminal error = new Terminal("error", 0);

	/** Report this symbol as not being a non-terminal. */
	public boolean isNonTerm() {
		return false;
	}

	/** get the precedence level of a terminal */
	public int getLevel() {
		return level;
	}

	public int getAssociativity() {
		return associativity;
	}

	/** set the precedence of a terminal */
	public void setPrecedence(int associativity, int level) {
		this.associativity = associativity;
		this.level = level;
	}

	public String toString() {
		return super.toString() + "[" + getIndex() + "]";
	}

}
