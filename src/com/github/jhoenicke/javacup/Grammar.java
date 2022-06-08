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
	private final ArrayList<Terminal> _terminals;
	private final ArrayList<NonTerminal> _nonterminals;
	private final ArrayList<Production> _productions;
	private final ArrayList<Production> _actions;

	private Production _start_production;

	/**
	 * Hash table to find states by their kernels (i.e, the original, unclosed, set
	 * of items -- which uniquely define the state). This table stores state objects
	 * using (a copy of) their kernel item sets as keys.
	 */
	private final HashMap<Collection<LrItem>, LalrState> _kernels_to_lalr = new HashMap<Collection<LrItem>, LalrState>();
	private final ArrayList<LalrState> _lalr_states = new ArrayList<LalrState>();

	/** Number of conflict found while building tables. */
	private int _num_conflicts = 0;

	/* . . . . . . . . . . . . . . . . . . . . . . . . . */
	/* . . Internal Results of Generating the Parser . . */
	/* . . . . . . . . . . . . . . . . . . . . . . . . . */

	/** Resulting parse action table. */
	public ParseActionTable action_table;

	/** Resulting reduce-goto table. */
	public ParseReduceTable reduce_table;

	public Grammar() {
		_terminals = new ArrayList<Terminal>();
		_nonterminals = new ArrayList<NonTerminal>();
		_productions = new ArrayList<Production>();
		_actions = new ArrayList<Production>();

		_terminals.add(Terminal.error);
		_terminals.add(Terminal.EOF);
	}

	public NonTerminal get_nonterminal(int i) {
		return _nonterminals.get(i);
	}

	public Terminal get_terminal(int i) {
		return _terminals.get(i);
	}

	public Production get_action(int i) {
		return _actions.get(i);
	}

	public Production get_production(int i) {
		return _productions.get(i);
	}

	public Production start_production() {
		return _start_production;
	}

	public int num_terminals() {
		return _terminals.size();
	}

	public int num_non_terminals() {
		return _nonterminals.size();
	}

	public int num_productions() {
		return _productions.size();
	}

	public int num_actions() {
		return _actions.size();
	}

	public Iterable<Terminal> terminals() {
		return _terminals;
	}

	public Iterable<NonTerminal> non_terminals() {
		return _nonterminals;
	}

	public Iterable<Production> productions() {
		return _productions;
	}

	public Iterable<Production> actions() {
		return _actions;
	}

	public int num_conflicts() {
		return _num_conflicts;
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	public Terminal add_terminal(String name, String type) {
		Terminal term = new Terminal(name, type, _terminals.size());
		_terminals.add(term);
		return term;
	}

	public NonTerminal add_non_terminal(String name, String type) {
		NonTerminal nt = new NonTerminal(name, type, _nonterminals.size());
		_nonterminals.add(nt);
		return nt;
	}

	public NonTerminal star_symbol(GrammarSymbol sym) {
		if (sym._star_symbol == null) {
			/* create plus symbol as * is defined via +. */
			plus_symbol(sym);
			String type = sym._stack_type == null ? null : sym._stack_type + "[]";
			sym._star_symbol = add_non_terminal(sym._name + "*", type);
		}
		return sym._star_symbol;
	}

	public NonTerminal plus_symbol(GrammarSymbol sym) {
		if (sym._plus_symbol == null) {
			String type = sym._stack_type == null ? null : sym._stack_type + "[]";
			sym._plus_symbol = add_non_terminal(sym._name + "+", type);
		}
		return sym._plus_symbol;
	}

	public NonTerminal opt_symbol(GrammarSymbol sym) {
		if (sym._opt_symbol == null) {
			sym._opt_symbol = add_non_terminal(sym._name + "?", sym._stack_type);
		}
		return sym._opt_symbol;
	}

	/** set start non terminal symbol */
	public void set_start_symbol(NonTerminal start_nt) {
		/* build a special start production */
		SymbolPart[] rhs = new SymbolPart[2];
		ActionPart action = null;
		String result;
		if (start_nt.stack_type() != null) {
			rhs[0] = new SymbolPart(start_nt, "CUP$rhs");
			result = "CUP$rhs";
		} else {
			rhs[0] = new SymbolPart(start_nt);
			result = "null";
		}
		rhs[1] = new SymbolPart(Terminal.EOF);
		action = new ActionPart("RESULT = " + result + ";\n/* ACCEPT */\nparser.done_parsing();");

		NonTerminal start = new NonTerminal("$START", "Object", 0);
		_nonterminals.add(start);

		_start_production = new Production(0, 0, start, rhs, -1, action, null);
		_productions.add(_start_production);
		_actions.add(_start_production);
		start.note_use();
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	private int next_nt = 0;

	private NonTerminal create_anon_nonterm(String type) {
		return add_non_terminal("NT$" + next_nt++, type);
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
	public Production build_production(NonTerminal lhs, List<ProductionPart> rhs_parts, Terminal precedence) {
		int i;

		/*
		 * if we have no start non-terminal declared and this is the first production,
		 * make its lhs nt the start_nt and build a special start production for it.
		 */
		if (_start_production == null) {
			set_start_symbol(lhs);
		}

		/* make sure start_production was created */
		assert _start_production != null;

		/* make sure we have a valid left-hand-side */
		assert lhs != null : "Attempt to construct a production with a null LHS";

		/* count use of lhs */
		lhs.note_use();
		if (precedence != null)
			precedence.note_use();

		/* merge adjacent actions (if any) */
		Iterator<ProductionPart> it = rhs_parts.iterator();
		ActionPart prev_action = null;
		while (it.hasNext()) {
			ProductionPart part = it.next();
			if (part instanceof ActionPart) {
				if (prev_action != null) {
					prev_action.add_code_string(((ActionPart) part).code_string());
					it.remove();
				} else
					prev_action = (ActionPart) part;
			} else
				prev_action = null;
		}

		ActionPart action = null;
		/* strip off any trailing action */
		if (rhs_parts.size() > 0 && rhs_parts.get(rhs_parts.size() - 1) instanceof ActionPart) {
			action = (ActionPart) rhs_parts.remove(rhs_parts.size() - 1);
		}

		/* allocate and copy over the right-hand-side */
		SymbolPart[] rhs = new SymbolPart[rhs_parts.size()];
		/* count use of each rhs symbol */
		int last_act_loc = -1;
		for (i = 0; i < rhs.length; i++) {
			ProductionPart prod = rhs_parts.get(i);
			if (prod instanceof ActionPart) {
				/* create a new non terminal for the action production */
				NonTerminal new_nt = create_anon_nonterm(lhs.stack_type());
				new_nt.note_use();
				rhs[i] = new SymbolPart(new_nt);
				last_act_loc = i;
			} else {
				rhs[i] = (SymbolPart) prod;
			}
		}
		int action_index = _actions.size();

		/* proxy productions are optimized away; they need no action */
		if (rhs.length == 1 && action == null)
			action_index = -1;

		/* check if there is a production with exactly the same action and reuse it. */
		for (Production prod : lhs.productions()) {
			if ((action == null ? prod.action() == null
					: prod.action() != null && action.code_string().equals(prod.action().code_string()))
					&& prod.rhs_length() == rhs.length) {
				if (productions_match(prod, rhs)) {
					action_index = prod.action_index();
					break;
				}
			}
		}

		/* put the production in the production list of the lhs non terminal */
		Production prod = new Production(_productions.size(), action_index, lhs, rhs, last_act_loc, action, precedence);
		_productions.add(prod);
		if (action_index == _actions.size()) {
			_actions.add(prod);
		}
		last_act_loc = -1;
		for (i = 0; i < rhs.length; i++) {
			ProductionPart part = rhs_parts.get(i);
			if (part instanceof ActionPart) {
				Production actprod = new ActionProduction(_productions.size(), _actions.size(), prod,
						(NonTerminal) rhs[i].the_symbol, (ActionPart) part, i, last_act_loc);
				_productions.add(actprod);
				_actions.add(actprod);
				last_act_loc = i;
			}
		}
		return prod;
	}

	private boolean productions_match(Production prod, SymbolPart[] rhs) {
		for (int idx = 0; idx < rhs.length; idx++) {
			if (rhs[idx].label == null) {
				if (prod.rhs(idx).label != null)
					return false;
			} else {
				if (!rhs[idx].label.equals(prod.rhs(idx).label))
					return false;
				if (rhs[idx].the_symbol.stack_type() == null ? prod.rhs(idx).the_symbol.stack_type() != null
						: !rhs[idx].the_symbol.stack_type().equals(prod.rhs(idx).the_symbol.stack_type()))
					return false;
			}
		}
		return true;
	}

	public LalrState get_lalr_state(Map<LrItem, TerminalSet> kernel) {
		Collection<LrItem> key = kernel.keySet();
		LalrState state = _kernels_to_lalr.get(key);
		if (state != null) {
			state.propagate_lookaheads(kernel);
		} else {
			state = new LalrState(kernel, _lalr_states.size());
			_lalr_states.add(state);
			_kernels_to_lalr.put(key, state);
		}
		return state;
	}

	/*-----------------------------------------------------------*/
	/*--- (Access to) Static (Class) Variables ------------------*/
	/*-----------------------------------------------------------*/

	/** Collection of all states. */
	public Collection<LalrState> lalr_states() {
		return _lalr_states;
	}

	/** Compute nullability of all non-terminals. */
	public void compute_nullability() {
		boolean change = true;

		/* repeat this process until there is no change */
		while (change) {
			/* look for a new change */
			change = false;

			/* consider each non-terminal */
			for (NonTerminal nt : _nonterminals) {
				/* check nullable and set change flag */
				change |= nt.check_nullable();
			}
		}
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/**
	 * Compute first sets for all non-terminals. This assumes nullability has
	 * already computed.
	 */
	public void compute_first_sets() {
		boolean change = true;

		/* initialize first sets */
		for (NonTerminal nt : _nonterminals)
			nt._first_set = new TerminalSet(this);

		/* repeat this process until we have no change */
		while (change) {
			/* look for a new change */
			change = false;

			/* consider each non-terminal */
			for (NonTerminal nt : _nonterminals) {
				/* consider every production of that non terminal */
				for (Production prod : nt.productions()) {
					/* get the updated first of that production */
					TerminalSet prod_first = prod.first_set(this);

					/* if this going to add anything, add it */
					change |= nt._first_set.add(prod_first);
				}
			}
		}
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

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

	public LalrState build_machine() {
		/* sanity check */
		assert _start_production != null : "Attempt to build viable prefix recognizer using a null production";

		/* build item with dot at front of start production and EOF lookahead */
		TreeMap<LrItem, TerminalSet> start_items = new TreeMap<LrItem, TerminalSet>();
		TerminalSet lookahead = new TerminalSet(this);
		lookahead.add(Terminal.EOF);
		LrItem core = _start_production.item();
		start_items.put(core, lookahead);
		LalrState start_state = get_lalr_state(start_items);

		/*
		 * continue looking at new states until we have no more work to do. Note that
		 * the lalr_states are continually expanded.
		 */
		for (int i = 0; i < _lalr_states.size(); i++) {
			/* remove a state from the work set */
			LalrState st = _lalr_states.get(i);
			;
			st.compute_closure(this);
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
	public void report_reduce_reduce(LalrState state, Entry<LrItem, Lookaheads> itm1, Entry<LrItem, Lookaheads> itm2) {
		StringBuilder message = new StringBuilder();
		message.append("*** Reduce/Reduce conflict found in state #").append(state.index()).append("\n")
				.append("  between ").append(itm1.getKey().toString()).append("\n").append("  and     ")
				.append(itm2.getKey().toString()).append("\n").append("  under symbols: {");
		String comma = "";
		for (int t = 0; t < num_terminals(); t++) {
			if (itm1.getValue().contains(t) && itm2.getValue().contains(t)) {
				message.append(comma).append(get_terminal(t).name());
				comma = ", ";
			}
		}
		message.append("}\n  Resolved in favor of the first production.\n");

		/* count the conflict */
		_num_conflicts++;
		ErrorManager.getManager().emit_error(message.toString());
	}

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/**
	 * Produce a warning message for one shift/reduce conflict.
	 *
	 * @param p            the production that is not reduced.
	 * @param conflict_sym the index of the symbol conflict occurs under.
	 */
	public void report_shift_reduce(LalrState state, Production p, GrammarSymbol conflict_sym) {
		/* emit top part of message including the reduce item */
		StringBuilder message = new StringBuilder();
		message.append("*** Shift/Reduce conflict found in state #").append(state.index()).append("\n");
		message.append("  between ").append(p).append("(*)\n");

		/* find and report on all items that shift under our conflict symbol */
		for (LrItem itm : state.items().keySet()) {
			/* only look if its not the same item and not a reduce */
			if (!itm.dot_at_end() && itm.symbol_after_dot().equals(conflict_sym)) {
				/* yes, report on it */
				message.append("  and     ").append(itm).append("\n");
			}
		}
		message.append("  under symbol ").append(conflict_sym).append("\n");
		message.append("  Resolved in favor of shifting.\n");

		/* count the conflict */
		_num_conflicts++;
		ErrorManager.getManager().emit_warning(message.toString());
	}

	public void build_tables(boolean compact_reduces) {
		action_table = new ParseActionTable(this);
		reduce_table = new ParseReduceTable(this);
		for (LalrState lst : lalr_states()) {
			lst.build_table_entries(this, action_table, reduce_table, compact_reduces);
		}
	}

	public void check_tables() {
		boolean[] used_productions = new boolean[_productions.size()];
		/* tabulate reductions -- look at every table entry */
		for (int row = 0; row < lalr_states().size(); row++) {
			for (int col = 0; col < num_terminals(); col++) {
				/* look at the action entry to see if its a reduce */
				int act = action_table.table[row][col];
				if (ParseActionTable.isReduce(act)) {
					/* tell production that we used it */
					used_productions[ParseActionTable.index(act)] = true;
				}
			}
		}

		/* now go across every production and make sure we hit it */
		for (Production prod : actions()) {
			/* if we didn't hit it give a warning */
			if (!used_productions[prod.action_index()]) {
				/* give a warning if they haven't been turned off */
				ErrorManager.getManager().emit_warning("*** Production \"" + prod.toString() + "\" never reduced");
			}
		}
	}

	public ParseActionTable action_table() {
		return action_table;
	}

	public ParseReduceTable reduce_table() {
		return reduce_table;
	}

	public void add_star_production(NonTerminal lhs, NonTerminal sym_star, GrammarSymbol sym) {
		ArrayList<ProductionPart> rhs = new ArrayList<ProductionPart>(2);
		rhs.add(new SymbolPart(sym_star));
		rhs.add(new SymbolPart(sym));
		if (sym.stack_type() != null)
			rhs.add(new ActionPart("CUP$STAR2"));
		build_production(lhs, rhs, null);
	}

	public void add_wildcard_rules(GrammarSymbol sym) {
		ArrayList<ProductionPart> rhs;
		if (sym._opt_symbol != null) {
			rhs = new ArrayList<ProductionPart>(1);
			if (sym.stack_type() != null)
				rhs.add(new ActionPart("RESULT=null;"));
			build_production(sym._opt_symbol, rhs, null);

			rhs = new ArrayList<ProductionPart>(1);
			rhs.add(new SymbolPart(sym));
			build_production(sym._opt_symbol, rhs, null);
		}

		if (sym._star_symbol != null) {
			assert sym._plus_symbol != null;
			rhs = new ArrayList<ProductionPart>(1);
			if (sym.stack_type() != null)
				rhs.add(new ActionPart("CUP$STAR0"));
			build_production(sym._star_symbol, rhs, null);

			rhs = new ArrayList<ProductionPart>(1);
			rhs.add(new SymbolPart(sym._plus_symbol));
			build_production(sym._star_symbol, rhs, null);
		}

		if (sym._plus_symbol != null) {
			rhs = new ArrayList<ProductionPart>(1);
			rhs.add(new SymbolPart(sym));
			if (sym.stack_type() != null)
				rhs.add(new ActionPart("CUP$STAR1"));
			build_production(sym._plus_symbol, rhs, null);

			add_star_production(sym._plus_symbol, sym._plus_symbol, sym);
		}

	}

	public void add_wildcard_rules() {
		for (GrammarSymbol sym : terminals())
			add_wildcard_rules(sym);
		for (GrammarSymbol sym : non_terminals())
			add_wildcard_rules(sym);
	}
}
