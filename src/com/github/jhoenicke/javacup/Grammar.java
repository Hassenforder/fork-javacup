package com.github.jhoenicke.javacup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

/**
 * Represents the context-free grammar for which we build a parser. An object of
 * this class is created by the JavaCUP parser which reads in the user grammar.
 * A grammar is a collection of non-terminal and terminal symbols and
 * productions.
 * 
 * This class also contains the code to build the viable prefix recognizer,
 * which is the heart of the created parser.
 *
 * @author hoenicke
 *
 */
public class Grammar {
	
	private final ArrayList<Terminal> terminals;
	private final ArrayList<NonTerminal> nonterminals;
	private final ArrayList<Production> productions;
	private final ArrayList<Production> actions;

	private Production startProduction;

	/**
	 * Hash table to find states by their kernels (i.e, the original, unclosed, set
	 * of items -- which uniquely define the state). This table stores state objects
	 * using (a copy of) their kernel item sets as keys.
	 */
	private final Map<Collection<LrItem>, LalrState> kernelsToLalr = new HashMap<Collection<LrItem>, LalrState>();
	private final List<LalrState> lalrStates = new ArrayList<LalrState>();

	/** Number of conflict found while building tables. */
	private int conflictCount = 0;

	/* . . . . . . . . . . . . . . . . . . . . . . . . . */
	/* . . Internal Results of Generating the Parser . . */
	/* . . . . . . . . . . . . . . . . . . . . . . . . . */

	/** Resulting parse action table. */
	private ParseActionTable actionTable;

	/** Resulting reduce-goto table. */
	private ParseReduceTable reduceTable;

	private int nextAnonNonTerminal = 0;

	public Grammar() {
		terminals = new ArrayList<Terminal>();
		nonterminals = new ArrayList<NonTerminal>();
		productions = new ArrayList<Production>();
		actions = new ArrayList<Production>();

		terminals.add(Terminal.error);
		terminals.add(Terminal.EOF);
	}

	public ParseActionTable getActionTable() {
		return actionTable;
	}

	public ParseReduceTable getReduceTable() {
		return reduceTable;
	}

	public NonTerminal getNonterminalAt(int position) {
		return nonterminals.get(position);
	}

	public Terminal getTerminalAt(int position) {
		return terminals.get(position);
	}

	public Production getActionAt(int position) {
		return actions.get(position);
	}

	public Production getProduction(int position) {
		return productions.get(position);
	}

	public Production getStartProduction() {
		return startProduction;
	}

	public int getTerminalCount() {
		return terminals.size();
	}

	public int getNonterminalCount() {
		return nonterminals.size();
	}

	public int getProductionCount() {
		return productions.size();
	}

	public int gatActionCount() {
		return actions.size();
	}

	public Iterable<Terminal> terminals() {
		return terminals;
	}

	public Iterable<NonTerminal> non_terminals() {
		return nonterminals;
	}

	public Iterable<Production> productions() {
		return productions;
	}

	public Iterable<Production> actions() {
		return actions;
	}

	public int getConflictCount() {
		return conflictCount;
	}

	public Terminal addTerminal(String name, String type) {
		Terminal term = new Terminal(name, type, terminals.size());
		terminals.add(term);
		return term;
	}

	public NonTerminal addNonterminal(String name, String type) {
		NonTerminal nt = new NonTerminal(name, type, nonterminals.size());
		nonterminals.add(nt);
		return nt;
	}

	public NonTerminal getStarSymbol(GrammarSymbol sym) {
		if (sym.getStarSymbol() == null) {
			/* create plus symbol as * is defined via +. */
			getPlusSymbol(sym);
			String type = sym.getType() == null ? null : sym.getType() + "[]";
			sym.setStarSymbol(addNonterminal(sym.getName() + "$0_many", type));
		}
		return sym.getStarSymbol();
	}

	public NonTerminal getPlusSymbol(GrammarSymbol sym) {
		if (sym.getPlusSymbol() == null) {
			String type = sym.getType() == null ? null : sym.getType() + "[]";
			sym.setPlusSymbol(addNonterminal(sym.getName() + "$1_many", type));
		}
		return sym.getPlusSymbol();
	}

	public NonTerminal getOptSymbol(GrammarSymbol sym) {
		if (sym.getOptSymbol() == null) {
			sym.setOptSymbol(addNonterminal(sym.getName() + "$0_1", sym.getType()));
		}
		return sym.getOptSymbol();
	}

	/** set start non terminal symbol */
	public void setStartSymbol(NonTerminal start_nt) {
		/* build a special start production */
		SymbolPart[] rhs = new SymbolPart[2];
		ActionPart action = null;
		String result;
		if (start_nt.getType() != null) {
			rhs[0] = new SymbolPart(start_nt, "CUP$rhs");
			result = "CUP$rhs";
		} else {
			rhs[0] = new SymbolPart(start_nt);
			result = "null";
		}
		rhs[1] = new SymbolPart(Terminal.EOF);
		action = new ActionPart("RESULT = " + result + ";\n/* ACCEPT */\nparser.done_parsing();");

		NonTerminal start = new NonTerminal("$START", "Object", 0);
		nonterminals.add(start);

		startProduction = new Production(0, 0, start, rhs, -1, action, null);
		productions.add(startProduction);
		actions.add(startProduction);
		start.incrementUseCount();
	}

	private NonTerminal createAnonNonterminal(String type) {
		return addNonterminal("NT$" + nextAnonNonTerminal++, type);
	}

	/**
	 * Create a production. Takes a LHS non terminal, a list of RHS parts (including
	 * terminals, non terminals, and actions) and a precedence. We factor out
	 * embedded actions into separate action_production using temporary
	 * non-terminals. Adjacent actions are merge immediately.
	 * 
	 * <p>
	 * Factoring out of actions is accomplished by creating new "hidden" non
	 * terminals. For example if the production was originally:
	 * </p>
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
	public Production buildProduction(NonTerminal lhs, List<ProductionPart> rhsParts, Terminal precedence) {
		int i;

		/*
		 * if we have no start non-terminal declared and this is the first production,
		 * make its lhs nt the start_nt and build a special start production for it.
		 */
		if (startProduction == null) {
			setStartSymbol(lhs);
		}

		/* make sure start_production was created */
		assert startProduction != null;

		/* make sure we have a valid left-hand-side */
		assert lhs != null : "Attempt to construct a production with a null LHS";

		/* count use of lhs */
		lhs.incrementUseCount();
		if (precedence != null)
			precedence.incrementUseCount();

		/* merge adjacent actions (if any) */
		Iterator<ProductionPart> it = rhsParts.iterator();
		ActionPart prev_action = null;
		while (it.hasNext()) {
			ProductionPart part = it.next();
			if (part instanceof ActionPart) {
				if (prev_action != null) {
					prev_action.addCode(((ActionPart) part).getCode());
					it.remove();
				} else
					prev_action = (ActionPart) part;
			} else
				prev_action = null;
		}

		ActionPart action = null;
		/* strip off any trailing action */
		if (rhsParts.size() > 0 && rhsParts.get(rhsParts.size() - 1) instanceof ActionPart) {
			action = (ActionPart) rhsParts.remove(rhsParts.size() - 1);
		}

		/* allocate and copy over the right-hand-side */
		SymbolPart[] rhs = new SymbolPart[rhsParts.size()];
		/* count use of each rhs symbol */
		int last_act_loc = -1;
		for (i = 0; i < rhs.length; i++) {
			ProductionPart prod = rhsParts.get(i);
			if (prod instanceof ActionPart) {
				/* create a new non terminal for the action production */
				NonTerminal new_nt = createAnonNonterminal(lhs.getType());
				new_nt.incrementUseCount();
				rhs[i] = new SymbolPart(new_nt);
				last_act_loc = i;
			} else {
				rhs[i] = (SymbolPart) prod;
			}
		}
		int action_index = actions.size();

		/* proxy productions are optimized away; they need no action */
		if (rhs.length == 1 && action == null)
			action_index = -1;

		/* check if there is a production with exactly the same action and reuse it. */
		for (Production prod : lhs.getProductions()) {
			if ((action == null ? prod.getAction() == null
					: prod.getAction() != null && action.getCode().equals(prod.getAction().getCode()))
					&& prod.getRhsSize() == rhs.length) {
				if (productionsMatch(prod, rhs)) {
					action_index = prod.getActionIndex();
					break;
				}
			}
		}

		/* put the production in the production list of the lhs non terminal */
		Production prod = new Production(productions.size(), action_index, lhs, rhs, last_act_loc, action, precedence);
		productions.add(prod);
		if (action_index == actions.size()) {
			actions.add(prod);
		}
		last_act_loc = -1;
		for (i = 0; i < rhs.length; i++) {
			ProductionPart part = rhsParts.get(i);
			if (part instanceof ActionPart) {
				Production actprod = new ActionProduction(productions.size(), actions.size(), prod,
						(NonTerminal) rhs[i].getSymbol(), (ActionPart) part, i, last_act_loc);
				productions.add(actprod);
				actions.add(actprod);
				last_act_loc = i;
			}
		}
		return prod;
	}

	private boolean productionsMatch(Production prod, SymbolPart[] rhs) {
		for (int idx = 0; idx < rhs.length; idx++) {
			if (rhs[idx].getLabel() == null) {
				if (prod.getRhsAt(idx).getLabel() != null)
					return false;
			} else {
				if (!rhs[idx].getLabel().equals(prod.getRhsAt(idx).getLabel()))
					return false;
				if (rhs[idx].getSymbol().getType() == null ? prod.getRhsAt(idx).getSymbol().getType() != null
						: !rhs[idx].getSymbol().getType().equals(prod.getRhsAt(idx).getSymbol().getType()))
					return false;
			}
		}
		return true;
	}

	public LalrState getLalrState(Map<LrItem, TerminalSet> kernel) {
		Collection<LrItem> key = kernel.keySet();
		LalrState state = kernelsToLalr.get(key);
		if (state != null) {
			state.propagateLookaheads(kernel);
		} else {
			state = new LalrState(kernel, lalrStates.size());
			lalrStates.add(state);
			kernelsToLalr.put(key, state);
		}
		return state;
	}

	/** Collection of all states. */
	public Collection<LalrState> getLalrStates() {
		return lalrStates;
	}

	/** Compute nullability of all non-terminals. */
	public void computeNullability() {
		boolean change = true;

		/* repeat this process until there is no change */
		while (change) {
			/* look for a new change */
			change = false;

			/* consider each non-terminal */
			for (NonTerminal nt : nonterminals) {
				/* check nullable and set change flag */
				change |= nt.checkNullable();
			}
		}
	}

	/**
	 * Compute first sets for all non-terminals. This assumes nullability has
	 * already computed.
	 */
	public void computeFirsts() {
		boolean change = true;

		/* initialize first sets */
		for (NonTerminal nt : nonterminals)
			nt.setFirsts(new TerminalSet(this));

		/* repeat this process until we have no change */
		while (change) {
			/* look for a new change */
			change = false;

			/* consider each non-terminal */
			for (NonTerminal nt : nonterminals) {
				/* consider every production of that non terminal */
				for (Production prod : nt.getProductions()) {
					/* get the updated first of that production */
					TerminalSet prod_first = prod.getFirsts(this);

					/* if this going to add anything, add it */
					change |= nt.getFirsts().add(prod_first);
				}
			}
		}
	}

	/**
	 * Build an LALR viable prefix recognition machine given a start production.
	 * This method operates by first building a start state from the start
	 * production (based on a single item with the dot at the beginning and EOF as
	 * expected lookahead). Then for each state it attempts to extend the machine by
	 * creating transitions out of the state to new or existing states. When
	 * considering extension from a state we make a transition on each symbol that
	 * appears before the dot in some item. For example, if we have the items:
	 * 
	 * <pre>
	 *    [A ::= a b * X c, {d,e}]
	 *    [B ::= a b * X d, {a,b}]
	 * </pre>
	 * 
	 * in some state, then we would be making a transition under X to a new state.
	 * This new state would be formed by a "kernel" of items corresponding to moving
	 * the dot past the X. In this case:
	 * 
	 * <pre>
	 *    [A ::= a b X * c, {d,e}]
	 *    [B ::= a b X * Y, {a,b}]
	 * </pre>
	 * 
	 * The full state would then be formed by "closing" this kernel set of items so
	 * that it included items that represented productions of things the parser was
	 * now looking for. In this case we would items corresponding to productions of
	 * Y, since various forms of Y are expected next when in this state (see
	 * lalr_item_set.compute_closure() for details on closure).
	 * <p>
	 *
	 * The process of building the viable prefix recognizer terminates when no new
	 * states can be added. However, in order to build a smaller number of states
	 * (i.e., corresponding to LALR rather than canonical LR) the state building
	 * process does not maintain full loookaheads in all items. Consequently, after
	 * the machine is built, we go back and propagate lookaheads through the
	 * constructed machine using a call to propagate_all_lookaheads(). This makes
	 * use of propagation links constructed during the closure and transition
	 * process.
	 *
	 * @see com.github.jhoenicke.javacup.lalr_item_set#compute_closure
	 * @see com.github.jhoenicke.javacup.LalrState#propagate_all_lookaheads
	 */

	public LalrState buildMachine() {
		/* sanity check */
		assert startProduction != null : "Attempt to build viable prefix recognizer using a null production";

		/* build item with dot at front of start production and EOF lookahead */
		TreeMap<LrItem, TerminalSet> start_items = new TreeMap<LrItem, TerminalSet>();
		TerminalSet lookahead = new TerminalSet(this);
		lookahead.add(Terminal.EOF);
		LrItem core = startProduction.getItem();
		start_items.put(core, lookahead);
		LalrState start_state = getLalrState(start_items);

		/*
		 * continue looking at new states until we have no more work to do. Note that
		 * the lalr_states are continually expanded.
		 */
		for (int i = 0; i < lalrStates.size(); i++) {
			/* remove a state from the work set */
			LalrState st = lalrStates.get(i);
			st.computeClosure(this);
			st.compute_successors(this);
		}

		return start_state;
	}

	/**
	 * Produce a warning message for one reduce/reduce conflict.
	 *
	 * @param itm1 first item in conflict.
	 * @param itm2 second item in conflict.
	 */
	public void reportReduceReduceConflict(LalrState state, Entry<LrItem, Lookaheads> itm1, Entry<LrItem, Lookaheads> itm2) {
		StringBuilder message = new StringBuilder();
		message.append("*** Reduce/Reduce conflict found in state #").append(state.getIndex()).append("\n")
				.append("  between ").append(itm1.getKey().toString()).append("\n").append("  and     ")
				.append(itm2.getKey().toString()).append("\n").append("  under symbols: {");
		String comma = "";
		for (int t = 0; t < getTerminalCount(); t++) {
			if (itm1.getValue().contains(t) && itm2.getValue().contains(t)) {
				message.append(comma).append(getTerminalAt(t).getName());
				comma = ", ";
			}
		}
		message.append("}\n  Resolved in favor of the first production.\n");

		/* count the conflict */
		conflictCount++;
		ErrorManager.getManager().emit_error(message.toString());
	}

	/**
	 * Produce a warning message for one shift/reduce conflict.
	 *
	 * @param p            the production that is not reduced.
	 * @param conflict_sym the index of the symbol conflict occurs under.
	 */
	public void reportShiftReduceConflict(LalrState state, Production p, GrammarSymbol conflict_sym) {
		/* emit top part of message including the reduce item */
		StringBuilder message = new StringBuilder();
		message.append("*** Shift/Reduce conflict found in state #").append(state.getIndex()).append("\n");
		message.append("  between ").append(p).append("(*)\n");

		/* find and report on all items that shift under our conflict symbol */
		for (LrItem itm : state.getItems().keySet()) {
			/* only look if its not the same item and not a reduce */
			if (!itm.isDotAtEnd() && itm.getSymbolAfterDotPosition().equals(conflict_sym)) {
				/* yes, report on it */
				message.append("  and     ").append(itm).append("\n");
			}
		}
		message.append("  under symbol ").append(conflict_sym).append("\n");
		message.append("  Resolved in favor of shifting.\n");

		/* count the conflict */
		conflictCount++;
		ErrorManager.getManager().emit_warning(message.toString());
	}

	public void buildTables(boolean compactReduces) {
		actionTable = new ParseActionTable(this);
		reduceTable = new ParseReduceTable(this);
		for (LalrState lst : getLalrStates()) {
			lst.buildTableEntries(this, actionTable, reduceTable, compactReduces);
		}
	}

	public void checkTables() {
		boolean[] used_productions = new boolean[productions.size()];
		/* tabulate reductions -- look at every table entry */
		for (int row = 0; row < getLalrStates().size(); row++) {
			for (int col = 0; col < getTerminalCount(); col++) {
				/* look at the action entry to see if its a reduce */
				int act = actionTable.getTable()[row][col];
				if (ParseActionTable.isReduce(act)) {
					/* tell production that we used it */
					used_productions[ParseActionTable.getIndex(act)] = true;
				}
			}
		}

		/* now go across every production and make sure we hit it */
		for (Production prod : actions()) {
			/* if we didn't hit it give a warning */
			if (!used_productions[prod.getActionIndex()]) {
				/* give a warning if they haven't been turned off */
				ErrorManager.getManager().emit_warning("*** Production \"" + prod.toString() + "\" never reduced");
			}
		}
	}

	public void addStarProduction(NonTerminal lhs, NonTerminal sym_star, GrammarSymbol sym) {
		ArrayList<ProductionPart> rhs = new ArrayList<ProductionPart>(2);
		rhs.add(new SymbolPart(sym_star));
		rhs.add(new SymbolPart(sym));
		if (sym.getType() != null)
			rhs.add(new ActionPart("CUP$STAR2"));
		buildProduction(lhs, rhs, null);
	}

	public void replaceWildcardRules(GrammarSymbol sym) {
		ArrayList<ProductionPart> rhs;
		if (sym.getOptSymbol() != null) {
			rhs = new ArrayList<ProductionPart>(1);
			if (sym.getType() != null)
				rhs.add(new ActionPart("RESULT=null;"));
			buildProduction(sym.getOptSymbol(), rhs, null);

			rhs = new ArrayList<ProductionPart>(1);
			rhs.add(new SymbolPart(sym));
			buildProduction(sym.getOptSymbol(), rhs, null);
		}

		if (sym.getStarSymbol() != null) {
			assert sym.getPlusSymbol() != null;
			rhs = new ArrayList<ProductionPart>(1);
			if (sym.getType() != null)
				rhs.add(new ActionPart("CUP$STAR0"));
			buildProduction(sym.getStarSymbol(), rhs, null);

			rhs = new ArrayList<ProductionPart>(1);
			rhs.add(new SymbolPart(sym.getPlusSymbol()));
			buildProduction(sym.getStarSymbol(), rhs, null);
		}

		if (sym.getPlusSymbol() != null) {
			rhs = new ArrayList<ProductionPart>(1);
			rhs.add(new SymbolPart(sym));
			if (sym.getType() != null)
				rhs.add(new ActionPart("CUP$STAR1"));
			buildProduction(sym.getPlusSymbol(), rhs, null);

			addStarProduction(sym.getPlusSymbol(), sym.getPlusSymbol(), sym);
		}

	}

	public void replaceWildcardRules() {
		for (GrammarSymbol sym : terminals())
			replaceWildcardRules(sym);
		for (GrammarSymbol sym : non_terminals())
			replaceWildcardRules(sym);
	}
}
