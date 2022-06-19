package com.github.jhoenicke.javacup;

/**
 * This class represents a production in the grammar. It contains a LHS non
 * terminal, and an array of RHS symbols. As various transformations are done on
 * the RHS of the production, it may shrink. As a result a separate length is
 * always maintained to indicate how much of the RHS array is still valid.
 * <p>
 * 
 * I addition to construction and manipulation operations, productions provide
 * methods for factoring out actions (see remove_embedded_actions()), for
 * computing the nullability of the production (i.e., can it derive the empty
 * string, see check_nullable()), and operations for computing its first set
 * (i.e., the set of terminals that could appear at the beginning of some string
 * derived from the production, see check_first_set()).
 * 
 * @see com.github.jhoenicke.javacup.ProductionPart
 * @see com.github.jhoenicke.javacup.SymbolPart
 * @see com.github.jhoenicke.javacup.ActionPart
 * @version last updated: 7/3/96
 * @author Frank Flannery
 */

public class Production {

	/** The left hand side non-terminal. */
	private final NonTerminal lhs;

	/** The precedence of the rule */
	private int rhsPrecedence = -1;
	private int rhsAssociation = -1;

	/** A collection of parts for the right hand side. */
	private final SymbolPart rhs[];

	/**
	 * An action_part containing code for the action to be performed when we reduce
	 * with this production.
	 */
	private final ActionPart action;

	/** Index number of the production. */
	private final int index, actionIndex;

	/** initial lr item corresponding to the production. */
	private LrItem lrItem;

	/** Is the nullability of the production known or unknown? */
	private boolean nullableKnown = false;

	/** Nullability of the production (can it derive the empty string). */
	private boolean nullable = false;

	/**
	 * Index of the result of the previous intermediate action on the
	 *         stack relative to top, -1 if no previous action
	 */
	private int indexOfIntermediateResult;

	/**
	 * Full constructor. This constructor accepts a LHS non terminal, an array of
	 * RHS parts (including terminals, non terminals, and actions), and a string for
	 * a final reduce action. It does several manipulations in the process of
	 * creating a production object. After some validity checking it translates
	 * labels that appear in actions into code for accessing objects on the runtime
	 * parse stack. It them merges adjacent actions if they appear and moves any
	 * trailing action into the final reduce actions string. Next it removes any
	 * embedded actions by factoring them out with new action productions. Finally
	 * it assigns a unique index to the production.
	 * <p>
	 * 
	 * Factoring out of actions is accomplished by creating new "hidden" non
	 * terminals. For example if the production was originally:
	 * 
	 * <pre>
	 *    A ::= B {action} C D
	 * </pre>
	 * 
	 * then it is factored into two productions:
	 * 
	 * <pre>
	 *    A ::= B X C D
	 *    X ::= {action}
	 * </pre>
	 * 
	 * (where X is a unique new non terminal). This has the effect of placing all
	 * actions at the end where they can be handled as part of a reduce by the
	 * parser.
	 */
	public Production(int index, int actionIndex, NonTerminal lhsSymbol, SymbolPart rhs[], int last_act_loc,
			ActionPart action, Terminal precedence) {
		if (precedence != null) {
			rhsPrecedence = precedence.getLevel();
			rhsAssociation = precedence.getAssociativity();
		}
		this.lhs = lhsSymbol;
		this.rhs = rhs;
		this.action = action;
		this.index = index;
		this.actionIndex = actionIndex;
		for (int i = 0; i < rhs.length; i++) {
			GrammarSymbol rhs_sym = rhs[i].getSymbol();
			if (rhs_sym != null)
				rhs_sym.incrementUseCount();
			if (precedence == null && rhs_sym instanceof Terminal) {
				Terminal term = (Terminal) rhs_sym;
				if (term.getLevel() != Assoc.NOPREC) {
					if (rhsPrecedence == Assoc.NOPREC) {
						rhsPrecedence = term.getLevel();
						rhsAssociation = term.getAssociativity();
					} else if (term.getLevel() != rhsPrecedence) {
						ErrorManager.getManager()
								.emit_error("Production " + this + " has more than one precedence symbol");
					}
				}
			}
		}
		indexOfIntermediateResult = last_act_loc;
		/* put us in the production list of the lhs non terminal */
		lhsSymbol.addProduction(this);
	}

	public NonTerminal getLhs() {
		return lhs;
	}

	public int getPrecedence() {
		return rhsPrecedence;
	}

	public int getAssociativity() {
		return rhsAssociation;
	}

	public ActionPart getAction() {
		return action;
	}

	public int getIndex() {
		return index;
	}

	public int getActionIndex() {
		return actionIndex;
	}

	public int getIndexOfIntermediateResult() {
		return indexOfIntermediateResult;
	}

	/** Access to the collection of parts for the right hand side. */
	public SymbolPart getRhsAt(int indx) {
		return rhs[indx];
	}

	/** How much of the right hand side array we are presently using. */
	public int getRhsSize() {
		return rhs.length;
	}

	/** How much of the right hand side array we are presently using. */
	public int getRhsStackDepth() {
		return rhs.length;
	}

	/** Index number of the production. */
	public LrItem getItem() {
		if (lrItem == null)
			lrItem = new LrItem(this);
		return lrItem;
	}

	/**
	 * Check to see if the production (now) appears to be nullable. A production is
	 * nullable if its RHS could derive the empty string. This results when the RHS
	 * is empty or contains only non terminals which themselves are nullable.
	 */
	public boolean check_nullable() {
		/* if we already know bail out early */
		if (nullableKnown)
			return nullable;

		/* if we have a zero size RHS we are directly nullable */
		if (getRhsSize() == 0) {
			/* stash and return the result */
			return setNullable(true);
		}

		/* otherwise we need to test all of our parts */
		for (int pos = 0; pos < getRhsSize(); pos++) {
			/* only look at non-actions */
			GrammarSymbol sym = rhs[pos].getSymbol();

			/* if its a terminal we are definitely not nullable */
			if (!sym.isNonTerm())
				return setNullable(false);
			/* its a non-term, is it marked nullable */
			else if (!((NonTerminal) sym).isNullable())
				/* this one not (yet) nullable, so we aren't */
				return false;
		}

		/* if we make it here all parts are nullable */
		return setNullable(true);
	}

	/** set (and return) nullability */
	private boolean setNullable(boolean v) {
		nullableKnown = true;
		nullable = v;
		return v;
	}

	public boolean isProxy(){
		return rhs.length == 1 && getAction() == null;
	}

	/**
	 * Return the first set based on current NT firsts. This assumes that
	 * nullability has already been computed for all non terminals and productions.
	 */
	public TerminalSet getFirsts(Grammar grammar) {
		return getItem().calculateLookahead(grammar);
	}

	public String toString() {
		StringBuilder result = new StringBuilder();

		result.append(getLhs().getName()).append(" ::= ");
		for (int i = 0; i < getRhsSize(); i++) {
			GrammarSymbol s = getRhsAt(i).getSymbol();
			// MH 07/07/2022 the_symbol can be null if a terminal is not declared
			if (s == null)
				result.append("***UNDECLARED***");
			else
				result.append(s.getName());
			result.append(" ");
		}

		return result.toString();
	}

}
