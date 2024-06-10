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

	/** Special terminal for end of input. */
	public static Terminal EOF = new Terminal("EOF", 1);

	/** special terminal used for error recovery */
	public static Terminal error = new Terminal("error", 0);

	/** special terminal used for a terminal out of the defined alphabet */
	public static Terminal outOfAlphabet = new Terminal("OOA", 2);

	private int precedence;
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
	public Terminal(String name, String type, int associativity, int precedence, int index) {
		super(name, type, index);
		this.precedence = precedence;
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

	public int getPrecedence() {
		return precedence;
	}

	public int getAssociativity() {
		return associativity;
	}

	public void setPrecedence(int associativity, int level) {
		this.associativity = associativity;
		this.precedence = level;
	}

	/** Report this symbol as not being a non-terminal. */
	public boolean isNonTerm() {
		return false;
	}

	public String toString() {
		return super.toString() + "[" + getIndex() + "]";
	}

}
