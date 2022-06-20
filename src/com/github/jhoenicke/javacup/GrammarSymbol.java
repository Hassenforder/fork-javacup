package com.github.jhoenicke.javacup;

/**
 * This abstract class serves as the base class for grammar symbols (i.e., both
 * terminals and non-terminals). Each symbol has a name string, and a string
 * giving the type of object that the symbol will be represented by on the
 * runtime parse stack. In addition, each symbol maintains a use count in order
 * to detect symbols that are declared but never used, and an index number that
 * indicates where it appears in parse tables (index numbers are unique within
 * terminals or non terminals, but not across both).
 *
 * @see com.github.jhoenicke.javacup.Terminal
 * @see com.github.jhoenicke.javacup.NonTerminal
 * @version last updated: 7/3/96
 * @author Frank Flannery
 */
public abstract class GrammarSymbol implements Comparable<GrammarSymbol> {

	/** String for the human readable name of the symbol. */
	private String name;

	/** String for the type of object used for the symbol on the parse stack. */
	private String type;

	/**
	 * Index of this symbol (terminal or non terminal) in the parse tables. Note:
	 * indexes are unique among terminals and unique among non terminals, however, a
	 * terminal may have the same index as a non-terminal, etc.
	 */
	private int index;

	/** Count of how many times the symbol appears in productions. */
	private int useCount;

	/**
	 * EBNF symbols * + ?
	 */
	private NonTerminal starSymbol, plusSymbol, optSymbol;

	/**
	 * Full constructor.
	 * 
	 * @param name    the name of the symbol.
	 * @param type    a string with the type name.
	 * @param index the index of the symbol.
	 */
	public GrammarSymbol(String name, String type, int index) {
		/* sanity check */
		if (name == null)
			name = "";

		this.name = name;
		this.type = type;
		this.index = index;
		this.useCount = 0;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public int getUseCount() {
		return useCount;
	}

	public int getIndex() {
		return index;
	}

	public NonTerminal getStarSymbol() {
		return starSymbol;
	}

	public NonTerminal getPlusSymbol() {
		return plusSymbol;
	}

	public NonTerminal getOptSymbol() {
		return optSymbol;
	}

	public void setStarSymbol(NonTerminal starSymbol) {
		this.starSymbol = starSymbol;
	}

	public void setPlusSymbol(NonTerminal plusSymbol) {
		this.plusSymbol = plusSymbol;
	}

	public void setOptSymbol(NonTerminal optSymbol) {
		this.optSymbol = optSymbol;
	}

	/** Increment the use count. */
	public void incrementUseCount() {
		useCount++;
	}

	/**
	 * Indicate if this is a non-terminal. Here in the base class we don't know, so
	 * this is abstract.
	 */
	public abstract boolean isNonTerm();
	
	/**
	 * comparator between two GrammarSymbols
	 * < 0   Terminal
	 * > 0   NonTerminal
	 * == 0  Same
	 */
	public int compareTo(GrammarSymbol other) {
		/* non terminals are larger than terminals */
		if (isNonTerm() != other.isNonTerm())
			return isNonTerm() ? 1 : -1;
		/* Otherwise compare by index */
		return getIndex() - other.getIndex();
	}

	public String toString() {
		StringBuilder tmp = new StringBuilder();
		tmp.append(getName());
		if (getType() != null) {
			tmp.append("<");
			tmp.append(getType());
			tmp.append(">");			
		}
		return tmp.toString();
	}

}
