package com.github.jhoenicke.javacup;

/**
 * This class represents a transition in an LALR viable prefix recognition
 * machine. Transitions can be under terminals or non-terminals. They are
 * internally linked together into singly linked lists containing all the
 * transitions out of a single state via the next field.
 * 
 * @see com.github.jhoenicke.javacup.LalrState
 * @version last updated: 11/25/95
 * @author Scott Hudson
 * 
 */
public class LalrTransition {

	/** The symbol we make the transition on. */
	public final GrammarSymbol on_symbol;

	/** The state we transition to. */
	public final LalrState to_state;

	/** Next transition in linked list of transitions out of a state */
	public final LalrTransition next;

	/*-----------------------------------------------------------*/
	/*--- Constructor(s) ----------------------------------------*/
	/*-----------------------------------------------------------*/

	/**
	 * Full constructor.
	 * 
	 * @param on_sym symbol we are transitioning on.
	 * @param to_st  state we transition to.
	 * @param nxt    next transition in linked list.
	 */
	public LalrTransition(GrammarSymbol on_sym, LalrState to_st, LalrTransition nxt) {
		/* sanity checks */
		assert on_sym != null : "Attempt to create transition on null symbol";
		assert to_st != null : "Attempt to create transition to null state";

		/* initialize */
		on_symbol = on_sym;
		to_state = to_st;
		next = nxt;
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/** Convert to a string. */
	public String toString() {
		return "transition on " + on_symbol.name() + " to state [" + to_state.index() + "]";
	}
}
