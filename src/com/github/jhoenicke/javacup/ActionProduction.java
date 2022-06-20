
package com.github.jhoenicke.javacup;

/**
 * A specialized version of a production used when we split an existing
 * production in order to remove an embedded action. Here we keep a bit of extra
 * bookkeeping so that we know where we came from.
 * 
 * @version last updated: 11/25/95
 * @author Scott Hudson
 */

public class ActionProduction extends Production {

	/** index of the action in the rhs of a production */
	private int indexOfAction;

	/** The production we were taken out of. */
	private Production baseProduction;

	/**
	 * Constructor.
	 * 
	 * @param index         the unique index of this production.
	 * @param base          the production we are being factored out of.
	 * @param lhsSymbol       the LHS symbol for this production.
	 * @param action        the action_part for this production.
	 * @param indexOfAction the index in the rhs() of the base production.
	 * @param lastActionLoc  the index of the previous intermediate action in base.
	 *                      -1 if no previous action.
	 */
	public ActionProduction(int index, int actionIndex, Production base, NonTerminal lhsSymbol, ActionPart action,
			int indexOfAction, int lastActionLoc) {
		super(index, actionIndex, lhsSymbol, new SymbolPart[0], lastActionLoc, action, null);
		baseProduction = base;
		this.indexOfAction = indexOfAction;
	}

	public Production getBaseProduction() {
		return baseProduction;
	}
	
	/** indexOfAction but more readable */
	public int getRhsStackDepth() {
		return indexOfAction;
	}

}
