
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

	/**
	 * Constructor.
	 * 
	 * @param index         the unique index of this production.
	 * @param base          the production we are being factored out of.
	 * @param lhs_sym       the LHS symbol for this production.
	 * @param action        the action_part for this production.
	 * @param indexOfAction the index in the rhs() of the base production.
	 * @param last_act_loc  the index of the previous intermediate action in base.
	 *                      -1 if no previous action.
	 */
	public ActionProduction(int index, int action_index, Production base, NonTerminal lhs_sym, ActionPart action,
			int indexOfAction, int last_act_loc) {
		super(index, action_index, lhs_sym, new SymbolPart[0], last_act_loc, action, null);
		_base_production = base;
		this.indexOfAction = indexOfAction;
	}

	private int indexOfAction;

	public int rhs_stackdepth() {
		return indexOfAction;
	}
	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/** The production we were taken out of. */
	private Production _base_production;

	/** The production we were taken out of. */
	public Production base_production() {
		return _base_production;
	}
}
