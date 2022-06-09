
package com.github.jhoenicke.javacup;

import java.util.ArrayList;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.Map.Entry;

/**
 * This class represents a state in the LALR viable prefix recognition machine.
 * A state consists of a mapping from LR items to a set of terminals
 * (lookaheads) and of transitions to other states under terminal and
 * non-terminal symbols. Each state represents a potential configuration of the
 * parser. If the item set of a state includes an item such as:
 * 
 * <pre>
 *    [A ::= B * C d E , {a,b,c}]
 * </pre>
 * 
 * this indicates that when the parser is in this state it is currently looking
 * for an A of the given form, has already seen the B, and would expect to see
 * an a, b, or c after this sequence is complete. Note that the parser is
 * normally looking for several things at once (represented by several items).
 * In our example above, the state would also include items such as:
 * 
 * <pre>
 *    [C ::= * X e Z, {d}]
 *    [X ::= * f, {e}]
 * </pre>
 * 
 * to indicate that it was currently looking for a C followed by a d (which
 * would be reduced into a C, matching the first symbol in our production
 * above), and the terminal f followed by e.
 *
 * <p>
 * At runtime, the parser uses a viable prefix recognition machine made up of
 * these states to parse. The parser has two operations, shift and reduce. In a
 * shift, it consumes one Symbol and makes a transition to a new state. This
 * corresponds to "moving the dot past" a terminal in one or more items in the
 * state (these new shifted items will then be found in the state at the end of
 * the transition). For a reduce operation, the parser is signifying that it is
 * recognizing the RHS of some production. To do this it first "backs up" by
 * popping a stack of previously saved states. It pops off the same number of
 * states as are found in the RHS of the production. This leaves the machine in
 * the same state is was in when the parser first attempted to find the RHS.
 * From this state it makes a transition based on the non-terminal on the LHS of
 * the production. This corresponds to placing the parse in a configuration
 * equivalent to having replaced all the symbols from the the input
 * corresponding to the RHS with the symbol on the LHS.
 * </p>
 *
 * @see com.github.jhoenicke.javacup.LrItem
 * @see com.github.jhoenicke.javacup.LalrTransition
 * @version last updated: 7/3/96
 * @author Frank Flannery
 * 
 */

public class LalrState {

	/** The item set for this state. */
	private Map<LrItem, Lookaheads> items;

	/** List of transitions out of this state. */
	private LalrTransition transitions = null;

	/** Index of this state in the parse tables */
	private int index;

	/**
	 * Constructor for building a state from a set of items.
	 * 
	 * @param kernel the set of items that makes up the kernel of this state.
	 * @param index  a unique index that is given to this state.
	 */
	public LalrState(Map<LrItem, TerminalSet> kernel, int index) {
		/* don't allow null or duplicate item sets */
		if (kernel == null)
			throw new AssertionError("Attempt to construct an LALR state from a null item set");

		/* assign a unique index */
		this.index = index;

		/* store the items */
		this.items = new TreeMap<LrItem, Lookaheads>();
		for (Entry<LrItem, TerminalSet> entry : kernel.entrySet())
			items.put(entry.getKey(), new Lookaheads(entry.getValue()));
	}

	/** The item set for this state. */
	public Map<LrItem, Lookaheads> getItems() {
		return items;
	}

	/** Index of this state in the parse tables */
	public int index() {
		return index;
	}

	/**
	 * Compute the closure of the set using the LALR closure rules. Basically for
	 * every item of the form:
	 * 
	 * <pre>
	 *    [L ::= a *N alpha, l]
	 * </pre>
	 * 
	 * (where N is a a non terminal and alpha is a string of symbols) make sure
	 * there are also items of the form:
	 * 
	 * <pre>
	 *    [N ::= *beta, first(alpha l)]
	 * </pre>
	 * 
	 * corresponding to each production of N. Items with identical cores but
	 * differing lookahead sets are merged by creating a new item with the same core
	 * and the union of the lookahead sets (the LA in LALR stands for "lookahead
	 * merged" and this is where the merger is). This routine assumes that
	 * nullability and first sets have been computed for all productions before it
	 * is called.
	 */
	public void computeClosure(Grammar grammar) {
		TerminalSet newLookaheads;
		boolean needPropagation;

		/* each current element needs to be considered */
		Stack<LrItem> consider = new Stack<LrItem>();
		consider.addAll(items.keySet());

		/* repeat this until there is nothing else to consider */
		while (consider.size() > 0) {
			/* get one item to consider */
			LrItem item = consider.pop();

			/* do we have a dot before a non terminal */
			NonTerminal nt = item.getNonTerminalAfterDotPosition();
			if (nt != null) {
				LrItem nextitem = item.getItemDotPositionShifted();
				/* create the lookahead set based on first symbol after dot */
				newLookaheads = nextitem.calculateLookahead(grammar);

				/* are we going to need to propagate our lookahead to new item */
				needPropagation = nextitem.isNullable();
				if (needPropagation)
					newLookaheads.add(items.get(item));

				/* create items for each production of that non term */
				for (Production prod : nt.getProductions()) {
					/* create new item with dot at start and that lookahead */
					LrItem newItem = prod.getItem();
					Lookaheads newLa;
					if (items.containsKey(newItem)) {
						newLa = items.get(newItem);
						newLa.add(newLookaheads);
					} else {
						newLa = new Lookaheads(newLookaheads);
						items.put(newItem, newLa);
						/* that may need further closure, consider it also */
						consider.push(newItem);
					}

					/* if propagation is needed link to that item */
					if (needPropagation)
						items.get(item).addListener(newLa);
				}
			}
		}
	}

	public void compute_successors(Grammar grammar) {
		/* gather up all the symbols that appear before dots */
		TreeMap<GrammarSymbol, ArrayList<LrItem>> outgoing = new TreeMap<GrammarSymbol, ArrayList<LrItem>>();
		for (LrItem itm : items.keySet()) {
			/* add the symbol after the dot (if any) to our collection */
			GrammarSymbol sym = itm.getSymbolAfterDotPosition();
			if (sym != null) {
				if (!outgoing.containsKey(sym))
					outgoing.put(sym, new ArrayList<LrItem>());
				outgoing.get(sym).add(itm);
			}
		}

		/* now create a transition out for each individual symbol */
		for (GrammarSymbol out : outgoing.keySet()) {
			/*
			 * gather up shifted versions of all the items that have this symbol before the
			 * dot
			 */
			TreeMap<LrItem, TerminalSet> new_items = new TreeMap<LrItem, TerminalSet>();

			/* find proxy symbols on the way */
			ArrayList<GrammarSymbol> proxySymbols = new ArrayList<GrammarSymbol>();
			proxySymbols.add(out);
			for (int i = 0; i < proxySymbols.size(); i++) {
				GrammarSymbol symbol = proxySymbols.get(i);
				for (LrItem item : outgoing.get(symbol)) {
					/* add to the kernel of the new state */
					if (item.getProduction().isProxy()) {
						GrammarSymbol proxy = item.getProduction().lhs();
						if (!proxySymbols.contains(proxy)) {
							proxySymbols.add(proxy);
						}
					} else {
						new_items.put(item.getItemDotPositionShifted(), getItems().get(item));
					}
				}
			}

			/* create/get successor state */
			LalrState newstate = grammar.getLalrState(new_items);
			for (GrammarSymbol symbol : proxySymbols) {
				for (LrItem item : outgoing.get(symbol)) {
					/* ... remember that item has propagate link to it */
					if (!item.getProduction().isProxy()) {
						getItems().get(item).addListener(newstate.getItems().get(item.getItemDotPositionShifted()));
					}
				}
			}

			/* add a transition from current state to that state */
			transitions = new LalrTransition(out, newstate, transitions);
		}
	}

	/**
	 * Propagate lookahead sets out of this state. This recursively propagates to
	 * all items that have propagation links from some item in this state.
	 */
	public void propagateLookaheads(Map<LrItem, TerminalSet> new_kernel) {
		/*
		 * Add the new lookaheads to the existing ones. This will propagate the
		 * lookaheads to all dependent items.
		 */
		for (Entry<LrItem, TerminalSet> entry : new_kernel.entrySet()) {
			items.get(entry.getKey()).add(entry.getValue());
		}
	}

	/**
	 * Fill in the parse table entries for this state. There are two parse tables
	 * that encode the viable prefix recognition machine, an action table and a
	 * reduce-goto table. The rows in each table correspond to states of the
	 * machine. The columns of the action table are indexed by terminal symbols and
	 * correspond to either transitions out of the state (shift entries) or
	 * reductions from the state to some previous state saved on the stack (reduce
	 * entries). All entries in the action table that are not shifts or reduces,
	 * represent errors. The reduce-goto table is indexed by non terminals and
	 * represents transitions out of a state on that non-terminal.
	 * <p>
	 * Conflicts occur if more than one action needs to go in one entry of the
	 * action table (this cannot happen with the reduce-goto table). Conflicts are
	 * resolved by always shifting for shift/reduce conflicts and choosing the
	 * lowest numbered production (hence the one that appeared first in the
	 * specification) in reduce/reduce conflicts. All conflicts are reported and if
	 * more conflicts are detected than were declared by the user, code generation
	 * is aborted.
	 *
	 * @param actionTable    the action table to put entries in.
	 * @param reduceTable the reduce-goto table to put entries in.
	 */
	public void buildTableEntries(Grammar grammar, ParseActionTable actionTable, ParseReduceTable reduceTable,
			boolean compactReduces) {
		int act;
		GrammarSymbol sym;

		int default_lasize = 0;
		int default_action = ParseActionTable.ERROR;
		boolean default_prodisempty = false;

		/* pull out our rows from the tables */
		int[] our_act_row = actionTable.getTable()[index()];
		Production[] productions = new Production[grammar.getTerminalCount() + 1];
		LalrState[] our_red_row = reduceTable.getTable()[index()];

		/* consider each item in our state */
		for (Entry<LrItem, Lookaheads> itm : getItems().entrySet()) {
			/* if its completed (dot at end) then reduce under the lookahead */
			if (itm.getKey().isDotAtEnd()) {
				boolean conflict = false;
				act = ParseActionTable.createActionCode(ParseActionTable.REDUCE, itm.getKey().getProduction().getActionIndex());
				int lasize = 0;

				/* consider each lookahead symbol */
				for (int t = 0; t < grammar.getTerminalCount(); t++) {
					/* skip over the ones not in the lookahead */
					if (!itm.getValue().contains(t))
						continue;
					lasize++;

					/* if we don't already have an action put this one in */
					if (our_act_row[t] == ParseActionTable.ERROR) {
						our_act_row[t] = act;
						productions[t] = itm.getKey().getProduction();
					} else {
						/* we now have a reduce/reduce conflict */
						/* take the other one; it came earlier */
						conflict = true;
					}
				}

				/*
				 * if there was a conflict with a different production, report it now. We can't
				 * do it in the above loop since it would call report for every terminal symbol
				 * on which the conflict is.
				 */
				if (conflict) {
					for (Entry<LrItem, Lookaheads> compare : getItems().entrySet()) {
						/* the compare item must be in a before this item in the entrySet */
						if (itm.getKey() == compare.getKey())
							break;

						/* is it a reduce */
						if (compare.getKey().isDotAtEnd()) {
							if (compare.getValue().intersects(itm.getValue()))
								/* report a reduce/reduce conflict */
								grammar.reportReduceReduceConflict(this, compare, itm);
						}
					}
				}

				/*
				 * if we compact reduce tables make this action the default action if it has the
				 * most lookahead symbols
				 */
				if (compactReduces && lasize > default_lasize) {
					Production prod = itm.getKey().getProduction();
					/* don't make it default if it doesn't save a rule */
					if (prod.getRhsSize() != 0 || lasize > 1) {
						default_prodisempty = prod.getRhsSize() == 0;
						default_lasize = lasize;
						default_action = act;
					}
				}
			}
		}

		/* consider each outgoing transition */
		for (LalrTransition trans = transitions; trans != null; trans = trans.next) {
			/* if its on an terminal add a shift entry */
			sym = trans.onSymbol;
			int idx = sym.getIndex();
			if (!sym.isNonTerm()) {
				act = ParseActionTable.createActionCode(ParseActionTable.SHIFT, trans.toState.index());
				/* if we don't already have an action put this one in */
				if (our_act_row[idx] == ParseActionTable.ERROR) {
					our_act_row[sym.getIndex()] = act;
				} else {
					/* this is a shift_reduce conflict */
					Production p = productions[idx];

					/* check if precedence can fix it */
					if (!fixWithPrecedence(p, (Terminal) sym, our_act_row, act)) {
						/* shift always wins */
						our_act_row[idx] = act;
						grammar.reportShiftReduceConflict(this, p, sym);
					}
				}
			} else {
				/* for non terminals add an entry to the reduce-goto table */
				our_red_row[idx] = trans.toState;
			}
		}

		/*
		 * Check if there is already an action for the error symbol. This must be the
		 * default action.
		 */
		act = our_act_row[Terminal.error.getIndex()];
		if (act != ParseActionTable.ERROR) {
			default_action = ParseActionTable.isReduce(act) ? act : ParseActionTable.ERROR;
			default_prodisempty = false;
		}
		our_act_row[grammar.getTerminalCount()] = default_action;
		if (default_action != ParseActionTable.ERROR) {
			for (int i = 0; i < grammar.getTerminalCount(); i++) {
				/*
				 * map everything to default action, except the error transition if
				 * default_action reduces an empty production. The latter may otherwise lead to
				 * infinite loops.
				 */
				if (our_act_row[i] == ParseActionTable.ERROR && (i != Terminal.error.getIndex() || !default_prodisempty))
					our_act_row[i] = default_action;
			}
		}
	}

	/**
	 * Procedure that attempts to fix a shift/reduce error by using precedences.
	 * --frankf 6/26/96
	 * 
	 * if a production (also called rule) and the lookahead terminal have a
	 * precedence, then the table can be fixed. if the rule has greater precedence
	 * than the terminal, a reduce by that rule is inserted in the table. If the
	 * terminal has a higher precedence, it is shifted. if they have equal
	 * precedence, then the associativity of the precedence is used to determine
	 * what to put in the table: if the precedence is left associative, the action
	 * is to reduce. if the precedence is right associative, the action is to shift.
	 * if the precedence is non associative, then it is a shift/reduce error.
	 *
	 * @param p                the production
	 * @param term             the lookahead terminal
	 * @param tableRow		   a row of the action table
	 * @param shiftAction      the rule in conflict with the table entry
	 */
	private boolean fixWithPrecedence(Production p, Terminal term, int[] tableRow, int shiftAction) {
		/*
		 * if both production and terminal have a precedence number, it can be fixed
		 */
		if (p.getLevel() > Assoc.NOPREC && term.getLevel() > Assoc.NOPREC) {

			int compare = term.getLevel() - p.getLevel();
			if (compare == 0)
				compare = term.getAssociativity() - Assoc.NONASSOC;

			/* if production precedes terminal, keep reduce in table */
			if (compare < 0)
				return true;

			/* if terminal precedes rule, put shift in table */
			else if (compare > 0) {
				tableRow[term.getIndex()] = shiftAction;
				return true;
			}
		}

		/*
		 * otherwise, neither the rule nor the terminal has a precedence, so it can't be
		 * fixed.
		 */
		return false;
	}

	public String toString() {
		StringBuilder result = new StringBuilder();
		LalrTransition tr;

		/* dump the item set */
		result.append("lalr_state [").append(index()).append("]: {\n");
		for (Entry<LrItem, Lookaheads> itm : getItems().entrySet()) {
			/* print the kernel first */
			if (itm.getKey().getDotPosition() == 0)
				continue;
			result.append("  [").append(itm.getKey()).append(", ");
			result.append(itm.getValue()).append("]\n");
		}
		for (Entry<LrItem, Lookaheads> itm : getItems().entrySet()) {
			/* do not print the kernel */
			if (itm.getKey().getDotPosition() != 0)
				continue;
			result.append("  [").append(itm.getKey()).append(", ");
			result.append(itm.getValue()).append("]\n");
		}
		result.append("}\n");

		/* dump the transitions */
		for (tr = transitions; tr != null; tr = tr.next) {
			result.append(tr).append("\n");
		}

		return result.toString();
	}

}
