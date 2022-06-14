package com.github.jhoenicke.javacup.runtime;

/**
 * Creates the Symbols interface, which CUP uses as default
 *
 * @version last updated 12-03-2022
 * @author Michel Hassenforder
 * @version last updated 27-03-2006
 * @author Michael Petter
 */

/* *************************************************
  Interface SymbolFactory
  
  interface for creating new symbols  
  You can also use this interface for your own callback hooks
  Declare Your own factory methods for creation of Objects in Your scanner!
 ***************************************************/
public interface SymbolFactory {

    /**
     * Construction with left/right propagation switched on
     */
    public Symbol newSymbol(Enum<?> token, Symbol left, Symbol right, Object value);
    public Symbol newSymbol(Enum<?> token, Symbol left, Symbol right);
    /**
     * Construction with left/right propagation switched off
     */
    public Symbol newSymbol(Enum<?> token, Object value);
    public Symbol newSymbol(Enum<?> token);
    /**
     * Construction of start symbol
     */
    public Symbol startSymbol();

    /**
     * Construction of end symbol
     */
    public Symbol endSymbol();

    /**
     * Construction of error symbol
     */
    public Symbol errorSymbol(Symbol left, Symbol right);
    
    @Deprecated
	Symbol newSymbol(String name, int id, Symbol left, Symbol right, Object value);
    @Deprecated
	Symbol newSymbol(String name, int id, Symbol left, Symbol right);
    @Deprecated
	Symbol newSymbol(String name, int id);
    @Deprecated
	Symbol newSymbol(String name, int id, Object value);
    @Deprecated
	Symbol startSymbol(String name, int id, int state);

}
