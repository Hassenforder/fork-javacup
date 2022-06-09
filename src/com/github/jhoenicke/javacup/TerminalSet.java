
package com.github.jhoenicke.javacup;

/**
 * A set of terminals implemented as a bitset.
 * 
 * @version last updated: 2008-11-08
 * @author Scott Hudson, Jochen Hoenicke
 */
public class TerminalSet {
	private final static int LOG_BITS_PER_UNIT = 6;
	private final static int BITS_PER_UNIT = 64;
	private long[] elements;
	private Grammar grammar;

	/*-----------------------------------------------------------*/
	/*--- Constructor(s) ----------------------------------------*/
	/*-----------------------------------------------------------*/

	/** Constructor for an empty set. */
	public TerminalSet(Grammar grammar) {
		/* allocate the bitset at what is probably the right size */
		this.grammar = grammar;
		this.elements = new long[((grammar.getTerminalCount() - 1) >>> LOG_BITS_PER_UNIT) + 1];
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/**
	 * Constructor for cloning from another set.
	 * 
	 * @param other the set we are cloning from.
	 */
	public TerminalSet(TerminalSet other) {
		this(other.grammar);
		elements = other.elements.clone();
	}

	/*-----------------------------------------------------------*/
	/*--- General Methods ----------------------------------------*/
	/*-----------------------------------------------------------*/

	/** Determine if the set is empty. */
	public boolean empty() {
		for (int i = 0; i < elements.length; i++)
			if (elements[i] != 0)
				return false;
		return true;
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/**
	 * Determine if the set contains a particular terminal.
	 * 
	 * @param sym the terminal symbol we are looking for.
	 */
	public boolean contains(Terminal sym) {
		return contains(sym.getIndex());
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/**
	 * Given its index determine if the set contains a particular terminal.
	 * 
	 * @param indx the index of the terminal in question.
	 */
	public boolean contains(int indx) {
		int idx = indx >> LOG_BITS_PER_UNIT;
		long mask = (1L << (indx & (BITS_PER_UNIT - 1)));
		return (elements[idx] & mask) != 0;
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/**
	 * Determine if this set is an (improper) subset of another.
	 * 
	 * @param other the set we are testing against.
	 */
	public boolean is_subset_of(TerminalSet other) {
		assert (other.elements.length == elements.length);
		for (int i = 0; i < elements.length; i++)
			if ((elements[i] & ~other.elements[i]) != 0)
				return false;
		return true;
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/**
	 * Add a single terminal to the set.
	 * 
	 * @param sym the terminal being added.
	 * @return true if this changes the set.
	 */
	public boolean add(Terminal sym) {
		int indx = sym.getIndex();
		int idx = indx >> LOG_BITS_PER_UNIT;
		long mask = (1L << (indx & (BITS_PER_UNIT - 1)));
		boolean result = (elements[idx] & mask) == 0;
		elements[idx] |= mask;
		return result;
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/**
	 * Remove a terminal if it is in the set.
	 * 
	 * @param sym the terminal being removed.
	 */
	public void remove(Terminal sym) {
		int indx = sym.getIndex();
		int idx = indx >> LOG_BITS_PER_UNIT;
		long mask = (1L << (indx & (BITS_PER_UNIT - 1)));
		elements[idx] &= ~mask;
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/**
	 * Add (union) in a complete set.
	 * 
	 * @param other the set being added.
	 * @return true if this changes the set.
	 */
	public boolean add(TerminalSet other) {
		assert (other.elements.length == elements.length);
		boolean changed = false;
		for (int i = 0; i < elements.length; i++) {
			if ((~elements[i] & other.elements[i]) != 0)
				changed = true;
			elements[i] |= other.elements[i];
		}
		return changed;
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/**
	 * Determine if this set intersects another.
	 * 
	 * @param other the other set in question.
	 */
	public boolean intersects(TerminalSet other) {
		assert (other.elements.length == elements.length);
		for (int i = 0; i < elements.length; i++) {
			if ((elements[i] & other.elements[i]) != 0)
				return true;
		}
		return false;
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/** Equality comparison. */
	public boolean equals(TerminalSet other) {
		assert (other.elements.length == elements.length);
		for (int i = 0; i < elements.length; i++) {
			if (elements[i] != other.elements[i])
				return false;
		}
		return true;
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/** Generic equality comparison. */
	public boolean equals(Object other) {
		if (!(other instanceof TerminalSet))
			return false;
		else
			return equals((TerminalSet) other);
	}

	@Override
	public int hashCode() {
		int hash = 0;
		for (int i = 0; i < elements.length; i++)
			hash = 13 * hash + 157 * (int) (elements[i] >> 16) + (int) elements[i];
		return hash;
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/** Convert to string. */
	public String toString() {
		StringBuilder result = new StringBuilder("{");
		String comma = "";
		for (int t = 0; t < grammar.getTerminalCount(); t++) {
			if (contains(t)) {
				result.append(comma).append(grammar.getTerminalAt(t));
				comma = ", ";
			}
		}
		result.append("}");
		return result.toString();
	}

	/*-----------------------------------------------------------*/

}
