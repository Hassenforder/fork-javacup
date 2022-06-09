package com.github.jhoenicke.javacup;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * A lookaheads object represents a set of terminal symbols that are used as
 * follower set for a production, to determine under which lookahead symbols
 * that production can be reduced. Since the lookahead symbols can be directly
 * inherited to other productions, we allow adding listeners to this set, that
 * will be updated whenever this set gets new lookahead symbols.
 * 
 * @author hoenicke
 *
 */
public class Lookaheads extends TerminalSet {
	
	private List<Lookaheads> listeners = null;

	public Lookaheads(TerminalSet t) {
		super(t);
	}

	public List<Lookaheads> getListeners() {
		if (listeners == null) listeners = new ArrayList<>();
		return listeners;
	}

	/**
	 * Add a listener object for propagations. Whenever this object changes by
	 * adding new lookaheads, all propagation listeners will also be updated.
	 * 
	 * @param child the lookaheads object that is dependent on this.
	 */
	public void addListener(Lookaheads child) {
		getListeners().add(child);
	}

	private boolean addWithoutPropagation(TerminalSet new_lookaheads) {
		return super.add(new_lookaheads);
	}

	/**
	 * Adds new lookaheads. This will also propagate the lookaheads to all objects
	 * added by add_propagation().
	 * 
	 * @param newLookaheads A set of new lookahead symbols.
	 */
	public boolean add(TerminalSet newLookaheads) {
		if (!super.add(newLookaheads))
			return false;

		Stack<Lookaheads> work = new Stack<>();
		work.addAll(getListeners());
		while (!work.isEmpty()) {
			Lookaheads la = work.pop();
			if (la.addWithoutPropagation(newLookaheads)) {
				work.addAll(la.getListeners());
			}
		}
		return true;
	}
}
