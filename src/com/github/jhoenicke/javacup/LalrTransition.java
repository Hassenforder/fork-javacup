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
	public final GrammarSymbol onSymbol;

	/** The state we transition to. */
	public final LalrState toState;

	/** Next transition in linked list of transitions out of a state */
	public final LalrTransition next;

	/**
	 * Full constructor.
	 * 
	 * @param on_sym symbol we are transitioning on.
	 * @param to_st  state we transition to.
	 * @param nxt    next transition in linked list.
	 */
	public LalrTransition(GrammarSymbol onSymbol, LalrState toState, LalrTransition next) {
		/* sanity checks */
		assert onSymbol != null : "Attempt to create transition on null symbol";
		assert toState != null : "Attempt to create transition to null state";

		/* initialize */
		this.onSymbol = onSymbol;
		this.toState = toState;
		this.next = next;
	}

	public String toString() {
		return "transition on " + onSymbol.getName() + " to state [" + toState.index() + "]";
	}

}
