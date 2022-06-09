
package com.github.jhoenicke.javacup;

/**
 * An LR item consisting of a production and the position of a marker (the
 * "dot") within the production. Typically item cores are written using a
 * production with an embedded "dot" to indicate their position. For example:
 * 
 * <pre>
 *     A ::= B * C d E
 * </pre>
 * 
 * This represents a point in a parse where the parser is trying to match the
 * given production, and has succeeded in matching everything before the "dot"
 * (and hence is expecting to see the symbols after the dot next). See
 * lalr_state for full details on the meaning and use of items.
 *
 * @see com.github.jhoenicke.javacup.LalrState
 * @author Scott Hudson
 */

public class LrItem implements Comparable<LrItem> {

	/** The production for the item. */
	private final Production production;

	/**
	 * The position of the "dot" -- this indicates the part of the production that
	 * the marker is before, so 0 indicates a dot at the beginning of the RHS.
	 */
	private final int dotPosition;

	/**
	 * The shifted item. This is generated when shift_item() is first called.
	 */
	private LrItem shifted;

	/**
	 * Full constructor. Is only called by shift_item() and other constructor.
	 * 
	 * @param prod production this item uses.
	 * @param pos  position of the "dot" within the item.
	 */
	private LrItem(Production production, int dotPosition) {
		assert production != null : "Attempt to create an lr_item_core with a null production";

		assert dotPosition >= 0 && dotPosition <= production.getRhsSize()
				: "Attempt to create an lr_item_core with a bad dot position";

		this.production = production;
		this.dotPosition = dotPosition;
	}

	/**
	 * Constructor for dot at start of right hand side. Is only called once for each
	 * production from production.item().
	 * 
	 * @param prod production this item uses.
	 */
	LrItem(Production production) {
		this(production, 0);
	}

	public Production getProduction() {
		return production;
	}

	public int getDotPosition() {
		return dotPosition;
	}

	/** Is the dot at the end of the production? */
	public final boolean isDotAtEnd() {
		return dotPosition >= production.getRhsSize();
	}

	/**
	 * Return the symbol after the dot. If there is no symbol after the dot we
	 * return null.
	 */
	public final GrammarSymbol getSymbolAfterDotPosition() {
		if (dotPosition < production.getRhsSize()) {
			return production.getRhsAt(dotPosition).getSymbol();
		}
		return null;
	}

	/**
	 * Determine if we have a dot before a non terminal, and if so which one (return
	 * null or the non terminal).
	 */
	public NonTerminal getNonTerminalAfterDotPosition() {

		/* get the symbol after the dot */
		GrammarSymbol symbol = getSymbolAfterDotPosition();

		/* if it exists and is a non terminal, return it */
		if (symbol instanceof NonTerminal)
			return (NonTerminal) symbol;
		else
			return null;
	}

	/**
	 * Get the lr_item_core that results from shifting the dot one position to the
	 * right.
	 */
	public LrItem getItemDotPositionShifted() {
		assert !isDotAtEnd() : "Attempt to shift past end of an lr_item";

		if (shifted == null)
			shifted = new LrItem(production, dotPosition + 1);
		return shifted;
	}

	/**
	 * Compare two items. They are compared by index of production_rule first. If
	 * productions are the same, they are compared by dot position.
	 */
	public int compareTo(LrItem item) {
		if (production != item.production)
			return production.index() - item.production.index();
		return dotPosition - item.dotPosition;
	}

	/**
	 * Calculate lookahead representing symbols that could appear after the symbol
	 * that the dot is currently in front of. Note: this routine must not be invoked
	 * before first sets and nullability has been calculated for all non terminals.
	 */
	public TerminalSet calculateLookahead(Grammar grammar) {
		/* start with an empty result */
		TerminalSet result = new TerminalSet(grammar);

		/* consider all nullable symbols after the one to the right of the dot */
		for (int pos = dotPosition; pos < production.getRhsSize(); pos++) {
			GrammarSymbol sym = production.getRhsAt(pos).getSymbol();

			/* if its a terminal add it in and we are done */
			if (!sym.isNonTerm()) {
				result.add((Terminal) sym);
				break;
			} else {
				NonTerminal nt = (NonTerminal) sym;
				/* otherwise add in first set of the non terminal */
				result.add(nt.getFirsts());

				/* if its nullable we continue adding, if not, we are done */
				if (!nt.isNullable())
					break;
			}
		}
		return result;
	}

	/**
	 * Determine if everything from the symbol one beyond the dot all the way to the
	 * end of the right hand side is nullable. This would indicate that the
	 * lookahead of this item must be included in the lookaheads of all items
	 * produced as a closure of this item. Note: this routine should not be invoked
	 * until after first sets and nullability have been calculated for all non
	 * terminals.
	 */
	public boolean isNullable() {
		/* walk down the rhs and bail if we get a non-nullable symbol */
		for (int pos = dotPosition; pos < production.getRhsSize(); pos++) {
			GrammarSymbol sym = production.getRhsAt(pos).getSymbol();

			/* if its a terminal we fail */
			if (!sym.isNonTerm())
				return false;

			/* if its not nullable we fail */
			if (!((NonTerminal) sym).isNullable())
				return false;
		}

		/* if we get here its all nullable */
		return true;
	}

	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append(production.lhs().getName());
		result.append(" ::= ");

		for (int i = 0; i < production.getRhsSize(); i++) {
			/* do we need the dot before this one? */
			if (i == dotPosition)
				result.append("(*) ");

			/* print the name of the part */
			result.append(production.getRhsAt(i).getSymbol().getName()).append(" ");
		}

		/* put the dot after if needed */
		if (dotPosition == production.getRhsSize())
			result.append("(*) ");

		return result.toString();
	}

}