package com.github.jhoenicke.javacup;

/**
 * This class represents a part of a production which is a symbol (terminal or
 * non terminal). This simply maintains a reference to the symbol in question.
 *
 * @see com.github.jhoenicke.javacup.Production
 * @version last updated: 11/25/95
 * @author Scott Hudson
 */
public class SymbolPart extends ProductionPart {

	/** The symbol that this part is made up of. */
	private final GrammarSymbol symbol;

	/**
	 * Optional label for referring to the part within an action (null for no
	 * label).
	 */
	private final String label;

	/**
	 * Full constructor.
	 * 
	 * @param symbol the symbol that this part is made up of.
	 * @param label an optional label string for the part.
	 */
	public SymbolPart(GrammarSymbol symbol, String label) {
		assert symbol != null : "Attempt to construct a symbol_part with a null symbol";
		this.symbol = symbol;
		this.label = label;
	}

	/**
	 * Constructor with no label.
	 * 
	 * @param sym the symbol that this part is made up of.
	 */
	public SymbolPart(GrammarSymbol symbol) {
		this(symbol, null);
	}

	public GrammarSymbol getSymbol() {
		return symbol;
	}

	public String getLabel() {
		return label;
	}

	public String toString() {
		if (label == null)
			return symbol.getName();
		else
			return symbol.getName() + ":" + label;
	}

}
