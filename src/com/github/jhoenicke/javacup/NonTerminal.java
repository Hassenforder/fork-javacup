package com.github.jhoenicke.javacup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class represents a non-terminal symbol in the grammar. Each non terminal
 * has a textual name, an index, and a string which indicates the type of object
 * it will be implemented with at runtime (i.e. the class of object that will be
 * pushed on the parse stack to represent it).
 *
 * @version last updated: 11/25/95
 * @author Scott Hudson
 */

public class NonTerminal extends GrammarSymbol {

	/** Table of all productions with this non terminal on the LHS. */
	private List<Production> productions = null;

	/** Nullability of this non terminal. */
	private boolean nullable;

	/** First set for this non-terminal. */
	private TerminalSet firsts;

	/**
	 * Full constructor.
	 * 
	 * @param name the name of the non terminal.
	 * @param type the type string for the non terminal.
	 * @param index the index for the non terminal.
	 */
	public NonTerminal(String name, String type, int index) {
		super(name, type, index);
	}

	/**
	 * Constructor for typeless nonterminal.
	 * 
	 * @param name the name of the non terminal.
	 * @param index the index for the non terminal.
	 */
	public NonTerminal(String name, int index) {
		this(name, null, index);
	}

	public boolean isNullable() {
		return nullable;
	}

	public TerminalSet getFirsts() {
		return firsts;
	}

	public void setFirsts(TerminalSet firsts) {
		this.firsts = firsts;
	}

	/** Lazy access to productions with this non terminal on the LHS. */
	public Collection<Production> getProductions() {
		if (productions == null) productions = new ArrayList<Production>();
		return productions;
	}

	/** Total number of productions with this non terminal on the LHS. */
	public int productionCount() {
		return getProductions().size();
	}

	/** Add a production to our set of productions. */
	public void addProduction(Production production) {
		/* catch improper productions */
		assert (production != null && production.getLhs() == this)
				: "Attempt to add invalid production to non terminal production table";
		/* add it to the table, keyed with itself */
		getProductions().add(production);
	}

	public boolean isNonTerm() {
		return true;
	}

	/**
	 * Test to see if this non terminal currently looks nullable.
	 * 
	 * @return true if nullable status changed.
	 */
	public boolean checkNullable() {
		/* only look at things that aren't already marked nullable */
		if (nullable)
			return false;

		/* look and see if any of the productions now look nullable */
		for (Production prod : getProductions()) {
			/* if the production can go to empty, we are nullable */
			if (prod.checkNullable()) {
				nullable = true;
				return true;
			}
		}

		/* none of the productions can go to empty, so we are not nullable */
		return false;
	}

	public String toString() {
		return super.toString() + "[" + getIndex() + "]" + (isNullable() ? "*" : "");
	}

}
