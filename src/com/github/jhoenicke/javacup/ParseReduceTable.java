
package com.github.jhoenicke.javacup;

import java.util.BitSet;
import java.util.TreeSet;

/**
 * This class represents the complete "reduce-goto" table of the parser. It has
 * one row for each state in the parse machines, and a column for each terminal
 * symbol. Each entry contains a state number to shift to as the last step of a
 * reduce.
 *
 * @author Scott Hudson, Jochen Hoenicke
 */
public class ParseReduceTable {

	/** Actual parse_reduce matrix, indexed by state and non-terminal. */
	private LalrState[][] table;

	/** How many rows/states in the machine/table. */
	private int stateCount;
	private int nonterminalCount;

	/**
	 * Simple constructor. Note: all terminals, non-terminals, and productions must
	 * already have been entered, and the viable prefix recognizer should have been
	 * constructed before this is called.
	 */
	public ParseReduceTable(Grammar grammar) {
		/* determine how many states we are working with */
		stateCount = grammar.getLalrStates().size();
		nonterminalCount = grammar.getNonterminalCount();

		/* allocate the array and fill it in with empty rows */
		table = new LalrState[stateCount][nonterminalCount];
	}

	public LalrState[][] getTable() {
		return table;
	}

	public int getStateCount() {
		return stateCount;
	}

	/**
	 * Compress the reduce table into it's runtime form. This returns an array
	 * red_tab, such that
	 * 
	 * <pre>
	 * red_tab[red_tab[state] + nonterm] = table[state][nonterm].index()
	 * </pre>
	 * 
	 * for all non-null table entries.
	 */
	public short[] compress() {
		BitSet used = new BitSet();
		TreeSet<CombRow> rows = new TreeSet<CombRow>();
		for (int i = 0; i < stateCount; i++) {
			int len = 0;
			for (int j = 0; j < nonterminalCount; j++)
				if (table[i][j] != null)
					len++;
			if (len == 0)
				continue;

			used.set(i);
			int[] rowidx = new int[len];
			len = 0;
			for (int j = 0; j < nonterminalCount; j++)
				if (table[i][j] != null)
					rowidx[len++] = j;
			CombRow row = new CombRow(i, rowidx);
			rows.add(row);
		}

		for (CombRow row : rows) {
			row.fitInComb(used);
		}
		int maxbase = used.size();
		while (!used.get(maxbase - 1))
			maxbase--;

		short[] compressed = new short[maxbase];
		/* initialize compressed table with 1 (shortest UTF-8 encoding) */
		for (int i = 0; i < maxbase; i++)
			compressed[i] = (short) 1;

		for (CombRow row : rows) {
			int base = row.base;
			compressed[row.index] = (short) base;
			for (int j = 0; j < row.comb.length; j++) {
				compressed[base + row.comb[j]] = (short) table[row.index][row.comb[j]].getIndex();
			}
		}
		return compressed;
	}

	public String toString() {
		StringBuilder result = new StringBuilder();
		LalrState goto_st;
		int cnt;

		result.append("-------- REDUCE_TABLE --------\n");
		for (int row = 0; row < getStateCount(); row++) {
			result.append("From state #").append(row).append("\n");
			cnt = 0;
			for (int col = 0; col < nonterminalCount; col++) {
				/* pull out the table entry */
				goto_st = table[row][col];

				/* if it has action in it, print it */
				if (goto_st != null) {
					result.append(" [non term ").append(col).append("->");
					result.append("state ").append(goto_st.getIndex()).append("]");

					/* end the line after the 3rd one */
					cnt++;
					if (cnt == 3) {
						result.append("\n");
						cnt = 0;
					}
				}
			}
			/* finish the line if we haven't just done that */
			if (cnt != 0)
				result.append("\n");
		}
		result.append("-----------------------------");

		return result.toString();
	}

}
