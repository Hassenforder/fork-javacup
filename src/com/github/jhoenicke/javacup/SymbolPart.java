package com.github.jhoenicke.javacup;

/** This class represents a part of a production which is a symbol (terminal
 *  or non terminal).  This simply maintains a reference to the symbol in 
 *  question.
 *
 * @see     com.github.jhoenicke.javacup.Production
 * @version last updated: 11/25/95
 * @author  Scott Hudson
 */
public class SymbolPart extends ProductionPart {

  /** The symbol that this part is made up of. */
  public final GrammarSymbol the_symbol;
  
  /** Optional label for referring to the part within an action (null for 
   *  no label). 
   */
  public final String label;

  /*-----------------------------------------------------------*/
  /*--- Constructor(s) ----------------------------------------*/
  /*-----------------------------------------------------------*/

  /** Full constructor. 
   * @param sym the symbol that this part is made up of.
   * @param lab an optional label string for the part.
   */
  public SymbolPart(GrammarSymbol sym, String lab)
    {
      assert sym != null:
	  "Attempt to construct a symbol_part with a null symbol";
      the_symbol = sym;
      label = lab;
    }

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Constructor with no label. 
   * @param sym the symbol that this part is made up of.
   */
  public SymbolPart(GrammarSymbol sym)
    {
      this(sym, null);
    }

  /*. . . . . . . . . . . . . . . . . . . . . . . . . . . . . .*/

  /** Convert to a string. */
  public String toString()
    {
      if (label == null)
	return the_symbol.name();
      else
	return the_symbol.name() + ":" + label;
    }

  /*-----------------------------------------------------------*/

}
