package com.github.jhoenicke.javacup;

import java.io.PrintWriter;
import java.util.Date;

/**
 * This class handles emitting generated code for the resulting parser. The
 * various parse tables must be constructed, etc. before calling any routines in
 * this class.
 * <p>
 *
 * Three classes are produced by this code:
 * <dl>
 * <dt>symbol constant class
 * <dd>this contains constant declarations for each terminal (and optionally
 * each non-terminal).
 * <dt>action class
 * <dd>this non-public class contains code to invoke all the user actions that
 * were embedded in the parser specification.
 * <dt>parser class
 * <dd>the specialized parser class consisting primarily of some user supplied
 * general and initialization code, and the parse tables.
 * </dl>
 * <p>
 *
 * Three parse tables are created as part of the parser class:
 * <dl>
 * <dt>production table
 * <dd>lists the LHS non terminal number, and the length of the RHS of each
 * production.
 * <dt>action table
 * <dd>for each state of the parse machine, gives the action to be taken (shift,
 * reduce, or error) under each lookahead symbol.<br>
 * <dt>reduce-goto table
 * <dd>when a reduce on a given production is taken, the parse stack is popped
 * back a number of elements corresponding to the RHS of the production. This
 * reveals a prior state, which we transition out of under the LHS non terminal
 * symbol for the production (as if we had seen the LHS symbol rather than all
 * the symbols matching the RHS). This table is indexed by non terminal numbers
 * and indicates how to make these transitions.
 * </dl>
 * <p>
 * 
 * In addition to the method interface, this class maintains a series of public
 * global variables and flags indicating how misc. parts of the code and other
 * output is to be produced, and counting things such as number of conflicts
 * detected (see the source code and public variables below for more details).
 * <p>
 *
 * This class is "static" (contains only data and methods).
 * <p>
 *
 * @see com.github.jhoenicke.javacup.main
 * @version last update: 11/25/95
 * @author Scott Hudson
 */

/*
 * Major externally callable routines here include: symbols - emit the symbol
 * constant class parser - emit the parser class
 * 
 * In addition the following major internal routines are provided: emit_package
 * - emit a package declaration emit_action_code - emit the class containing the
 * user's actions emit_production_table - emit declaration and init for the
 * production table do_action_table - emit declaration and init for the action
 * table do_reduce_table - emit declaration and init for the reduce-goto table
 * 
 * Finally, this class uses a number of public instance variables to communicate
 * optional parameters and flags used to control how code is generated, as well
 * as to report counts of various things (such as number of conflicts detected).
 * These include:
 * 
 * prefix - a prefix string used to prefix names that would otherwise "pollute"
 * someone else's name space. package_name - name of the package emitted code is
 * placed in (or null for an unnamed package. symbol_const_class_name - name of
 * the class containing symbol constants. parser_class_name - name of the class
 * for the resulting parser. action_code - user supplied declarations and other
 * code to be placed in action class. parser_code - user supplied declarations
 * and other code to be placed in parser class. init_code - user supplied code
 * to be executed as the parser is being initialized. scan_code - user supplied
 * code to get the next Symbol. after_reduce_code - user code that will run
 * after every reduce. start_production - the start production for the grammar.
 * import_list - list of imports for use with action class. num_conflicts -
 * number of conflicts detected. nowarn - true if we are not to issue warning
 * messages. not_reduced - count of number of productions that never reduce.
 * unused_term - count of unused terminal symbols. unused_non_term - count of
 * unused non terminal symbols. _time - a series of symbols indicating how long
 * various sub-parts of code generation took (used to produce optional time
 * reports in main).
 */

public class Emit {

	private Options options;
	private Timer timer;

	/** The package name of javacup's runtime. */
	private final static String RUNTIME_PACKAGE = "com.github.jhoenicke.javacup.runtime";

	/** The prefix placed on names that pollute someone else's name space. */
	private final static String prefix = "CUP$";

	public Emit(Options options, Timer timer) {
		super();
		this.options = options;
		this.timer = timer;
	}

	/**
	 * Build a string with the standard prefix.
	 * 
	 * @param str string to prefix.
	 */
	protected final static String pre(String str) {
		return prefix + str;
	}

	/**
	 * TUM changes; proposed by Henning Niss 20050628 Build a string with the
	 * specified type arguments, if present, otherwise an empty string.
	 */
	protected String typeArgument() {
		return options.class_type_argument == null ? "" : "<" + options.class_type_argument + ">";
	}

	/**
	 * Emit a package spec if the user wants one.
	 * 
	 * @param out stream to produce output on.
	 */
	protected void emitPackage(PrintWriter out) {
		/* generate a package spec if we have a name for one */
		if (options.package_name != null) {
			out.println("package " + options.package_name + ";");
			out.println();
		}
	}

	public String stackElement(int index, boolean is_java15) {
		String access = pre("stack") + ".get(" + pre("size") + " - " + index + ")";
		return is_java15 ? access : "((" + RUNTIME_PACKAGE + ".Symbol) " + access + ")";
	}

	/**
	 * Emit code for the symbol constant as class or interface,
	 * optionally including non terms, if they have been requested.
	 * 
	 * @param out            stream to produce output on.
	 * @param emit_non_terms do we emit constants for non terminals?
	 * @param sym_interface  should we emit an interface, rather than a class?
	 */
	public void symbols(PrintWriter out, Grammar grammar) {
		timer.pushTimer();

		String type = null;
		switch (options.symType) {
		case CLASS:
			type = "class";
			break;
		case INTERFACE:
			type = "interface";
			break;
		case ENUM:
			break;
		}

		if (type == null) throw new Error ("Illegal call to symbols emiter with target enum class");

		/* top of file */
		out.println();
		out.println("//----------------------------------------------------");
		out.println("// The following code was generated by " + Version.title);
		out.println("// " + new Date());
		out.println("//----------------------------------------------------");
		out.println();
		emitPackage(out);

		/* class header */
		out.println("/** CUP generated " + type + " containing symbol constants. */");
		out.println("public " + type + " " + options.symbol_const_class_name + " {");

		out.println("  /* terminals */");

		/* walk over the terminals */ /* later might sort these */
		for (Terminal term : grammar.terminals()) {

			/* output a constant decl for the terminal */
			out.println("  public static final int " + term.getName() + " = " + term.getIndex() + ";");
		}

		/* do the non terminals if they want them (parser doesn't need them) */
		if (options.include_non_terms) {
			out.println();
			out.println("  /* non terminals */");

			/* walk over the non terminals */ /* later might sort these */
			for (NonTerminal nt : grammar.non_terminals()) {
				// ****
				// TUM Comment: here we could add a typesafe enumeration
				// ****

				/* output a constant decl for the terminal */
				out.println("  static final int " + nt.getName() + " = " + nt.getIndex() + ";");
			}
		}

		/* end of class */
		out.println("}");
		out.println();

		timer.popTimer(Timer.TIMESTAMP.symbols_time);
	}

	/**
	 * Emit code for the terminal constant as enum.
	 * 
	 * @param out            stream to produce output on.
	 * @param emit_non_terms do we emit constants for non terminals?
	 * @param sym_interface  should we emit an interface, rather than a class?
	 */
	public void terminals(PrintWriter out, Grammar grammar) {
		timer.pushTimer();

		String type = null;
		switch (options.symType) {
		case CLASS:
			break;
		case INTERFACE:
			break;
		case ENUM:
			type = "enum";
			break;
		}

		if (type == null) throw new Error ("Illegal call to terminal emiter with target not an enum class");

		/* top of file */
		out.println();
		out.println("//----------------------------------------------------");
		out.println("// The following code was generated by " + Version.title);
		out.println("// " + new Date());
		out.println("//----------------------------------------------------");
		out.println();
		emitPackage(out);

		/* class header */
		out.println("/** CUP generated " + type + " containing terminal constants. */");
		out.println("public " + type + " " + options.symbol_const_terminal_name + " {");

		out.println("  /* terminals */");

		/* walk over the terminals */ /* later might sort these */
		for (Terminal term : grammar.terminals()) {
			/* output a constant decl for the terminal */
			out.println("   "+term.getName() + ",");
		}
		out.println("   ;");

		out.println("}");
		out.println();

		timer.popTimer(Timer.TIMESTAMP.symbols_time);
	}

	/**
	 * Emit code for the terminal constant as enum.
	 * 
	 * @param out            stream to produce output on.
	 * @param emit_non_terms do we emit constants for non terminals?
	 * @param sym_interface  should we emit an interface, rather than a class?
	 */
	public void nonterminals(PrintWriter out, Grammar grammar) {
		timer.pushTimer();

		String type = null;
		switch (options.symType) {
		case CLASS:
			break;
		case INTERFACE:
			break;
		case ENUM:
			type = "enum";
			break;
		}

		if (type == null) throw new Error ("Illegal call to nonterminal emiter with target not an enum class");

		/* top of file */
		out.println();
		out.println("//----------------------------------------------------");
		out.println("// The following code was generated by " + Version.title);
		out.println("// " + new Date());
		out.println("//----------------------------------------------------");
		out.println();
		emitPackage(out);

		/* class header */
		out.println("/** CUP generated " + type + " containing nonterminal constants. */");
		// package private as only Parser should use it
		out.println(type + " " + options.symbol_const_nonterminal_name + " {");

		out.println("  /* nonterminals */");

		/* walk over the nonterminals */ /* later might sort these */
		for (NonTerminal nonterm : grammar.non_terminals()) {
			/* output a constant decl for the nonterminal */
			out.println("  "+nonterm.getName() + ",");
		}
		out.println("   ;");

		out.println("}");
		out.println();

		timer.popTimer(Timer.TIMESTAMP.symbols_time);
	}

	private void emitStdAction(PrintWriter out, Grammar grammar, Production prod, Options options) {
		boolean is_star_action = prod.getAction() != null && prod.getAction().getCode().startsWith("CUP$STAR");
		String result = "";
		if (prod.getLhs().getType() != null && !is_star_action) {
			int lastResult = prod.getIndexOfIntermediateResult();
			String init_result = "";
			if (lastResult != -1) {
				init_result = " = (" + prod.getLhs().getType() + ") "
						+ stackElement(prod.getRhsStackDepth() - lastResult, options.opt_java15) + ".value";
			} else if (prod instanceof ActionProduction) {
				init_result = " = null";
			}
			/* create the result symbol */
			/*
			 * make the variable RESULT which will point to the new Symbol (see below) and
			 * be changed by action code 6/13/96 frankf
			 */
			out.println("              " + prod.getLhs().getType() + " RESULT" + init_result + ";");

			result = ", RESULT";
		}

		Production baseprod;
		if (prod instanceof ActionProduction)
			baseprod = ((ActionProduction) prod).getBaseProduction();
		else
			baseprod = prod;
		String leftsym = null, rightsym = null;
		/*
		 * Add code to propagate RESULT assignments that occur in action code embedded
		 * in a production (ie, non-rightmost action code). 24-Mar-1998 CSA
		 */
		if (options.after_reduce_code != null)
			out.println("              " + RUNTIME_PACKAGE + ".Symbol[] " + pre("symbols_array") + " = new "
					+ RUNTIME_PACKAGE + ".Symbol[" + prod.getRhsStackDepth() + "];");
		for (int i = prod.getRhsStackDepth() - 1; i >= 0; i--) {
			SymbolPart symbol = baseprod.getRhsAt(i);
			String label = symbol.getLabel();
			String symtype = symbol.getSymbol().getType();
			boolean is_wildcard = !is_star_action && symtype != null
					&& (symbol.getSymbol().getName().endsWith("$0_many") || symbol.getSymbol().getName().endsWith("$1_many"));
			if (options.after_reduce_code != null) {
				out.println("              " + pre("symbols_array") + "[" + i + "] = "
						+ stackElement(prod.getRhsStackDepth() - i, options.opt_java15) + ";");
			}
			if (label != null) {
				if (i == 0)
					leftsym = label + "$";
				if (i == prod.getRhsStackDepth() - 1)
					rightsym = label + "$";

				out.println("              " + RUNTIME_PACKAGE + ".Symbol " + label + "$ = "
						+ stackElement(prod.getRhsStackDepth() - i, options.opt_java15) + ";");

				/* Put in the left/right value labels */
				if (options.opt_old_lr_values) {
					out.println("              int " + label + "left = " + label + "$.left;");
					out.println("              int " + label + "right = " + label + "$.right;");
				}
				if (symtype != null) {
					if (is_wildcard) {
						String basetype = symtype.substring(0, symtype.length() - 2);
						int arraySuffix = basetype.length();
						while (basetype.charAt(arraySuffix - 2) == '[')
							arraySuffix -= 2;
						if (options.use_list) {
							String listtype = "java.util.List" + "<" + basetype + ">";
							out.println("              " + listtype + " " + label + " = (" + listtype + ") " + label
									+ "$.value;");
						} else {
							String listtype = "java.util.ArrayList";
							String cast = "";
							if (options.opt_java15)
								listtype += "<" + basetype + ">";
							else
								cast = "(" + symtype + ") ";
							String symbollist = pre("list$" + label);
							out.println("              " + listtype + " " + symbollist + " = (" + listtype + ") " + label
									+ "$.value;");
							out.println("              " + symtype + " " + label + " = " + cast + symbollist + ".toArray("
									+ "new " + basetype.substring(0, arraySuffix) + "[" + symbollist + ".size()]"
									+ basetype.substring(arraySuffix) + ");");
						}
					} else {
						out.println("              " + symtype + " " + label + " = (" + symtype + ") " + label
								+ "$.value;");
					}
				}
			}
		}

		/* if there is an action string, emit it */
		if (prod.getAction() != null) {
			if (prod.getAction().getCode().startsWith("CUP$STAR")) {
				assert (prod.getLhs().getType() != null);
				String symtype = prod.getLhs().getType();
				String basetype = symtype.substring(0, symtype.length() - 2);
				String listtype = "java.util.ArrayList";
				if (options.opt_java15)
					listtype += "<" + basetype + ">";

				switch (prod.getAction().getCode().charAt(8)) {
				case '0':
					/* 
					 * something like
					 * java.util.ArrayList<XXX> RESULT = new java.util.ArrayList<XXX>();
					 */
					out.println("              " + listtype + " RESULT = new " + listtype + "();");
					result = ", RESULT";
					break;
				case '1':
					/* 
					 * something like
					 * java.util.ArrayList<XXX> RESULT = new java.util.ArrayList<XXX>();
					 * RESULT.add ((XXX) $0.value);
					 */
					leftsym = rightsym = pre("0");
					out.println("              " + RUNTIME_PACKAGE + ".Symbol " + rightsym + " = "
							+ stackElement(prod.getRhsStackDepth(), options.opt_java15) + ";");
					out.println("              " + listtype + " RESULT = new " + listtype + "();");
					out.println("              " + "RESULT.add((" + basetype + ") " + rightsym + ".value);");
					result = ", RESULT";
					break;
				case '2':
					/* 
					 * something like
					 * java.util.ArrayList<XXX> RESULT = (java.util.ArrayList<XXX>) $0.value;
					 * RESULT.add ((XXX) $1.value);
					 */
					leftsym = pre("0");
					rightsym = pre("1");
					out.println("              " + RUNTIME_PACKAGE + ".Symbol " + rightsym + " = "
							+ stackElement(prod.getRhsStackDepth() - 1, options.opt_java15) + ";");
					out.println("              " + RUNTIME_PACKAGE + ".Symbol " + leftsym + " = "
							+ stackElement(prod.getRhsStackDepth() - 0, options.opt_java15) + ";");
					out.println("              " + listtype + " RESULT = (" + listtype + ") " + leftsym + ".value;");
					out.println("              " + "RESULT.add((" + basetype + ") " + rightsym + ".value);");
					result = ", RESULT";
					break;
				}
			} else {
				out.println(prod.getAction().getCode());
			}
		}

		/*
		 * here we have the left and right values being propagated. must make this a
		 * command line option. frankf 6/18/96
		 */

		/*
		 * Create the code that assigns the left and right values of the new Symbol that
		 * the production is reducing to
		 */
		String leftright = "";
		if (options.opt_lr_values) {
			if (prod.getRhsSize() <= 1 && rightsym == null) {
				leftsym = rightsym = pre("sym");
				out.println("              " + RUNTIME_PACKAGE + ".Symbol " + rightsym + " = "
						+ stackElement(1, options.opt_java15) + ";");
			} else {
				if (rightsym == null)
					rightsym = stackElement(1, options.opt_java15);
				if (leftsym == null)
					leftsym = stackElement(prod.getRhsStackDepth(), options.opt_java15);
			}
			leftright = ", " + leftsym + ", " + rightsym;
		}
		/* code to call the after reduce user code */
		if (options.after_reduce_code != null)
			out.println("              " + pre("after_reduce") + "(RESULT, " + pre("symbols_array") + ");");
		/* code to return lhs symbol */
		String symbolName = "";
		String factoryName = "";
		
		switch (options.symType) {
		case CLASS:
		case INTERFACE:
			symbolName = "\"" + prod.getLhs().getName() + "\", " + prod.getLhs().getIndex();
			factoryName = "getSymbolFactory()";
			break;
		case ENUM:
			symbolName = options.symbol_const_nonterminal_name + "." + prod.getLhs().getName();
			factoryName = "getSymbolFactory2()";
			break;
		}
		out.println("              return parser." + factoryName + ".newSymbol(" + symbolName + leftright + result + ");");
	}

	private void emitCSTAction(PrintWriter out, Grammar grammar, Production prod, Options options) {
		out.println("              java.util.List<" + RUNTIME_PACKAGE + ".Symbol> children = new java.util.ArrayList<>();");
		for (int i = prod.getRhsStackDepth() - 1; i >= 0; i--) {
			out.println("              children.add("
					+ stackElement(prod.getRhsStackDepth() - i, options.opt_java15) + ");");
		}
		String symbolName = "";
		symbolName = options.symbol_const_nonterminal_name + "." + prod.getLhs().getName();
		out.println("              return parser.getSymbolFactory2().newSymbol(" + symbolName + ", children);");
	}
	
	private void emitAction(PrintWriter out, Grammar grammar, Production prod, Options options) {
		switch (options.generatorMode) {
		case ACTION :	emitStdAction(out, grammar, prod, options); break;
		case CST : 		emitCSTAction(out, grammar, prod, options); break;
		}
	}
	

	/* . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

	/**
	 * Emit code for the non-public class holding the actual action code.
	 * 
	 * @param out        stream to produce output on.
	 * @param start_prod the start production of the grammar.
	 */
	private void emitActionCode(PrintWriter out, Grammar grammar, String action_class, Options options) {
		timer.pushTimer();

		/* Stack generic parameter and optional casts depending on Java Version */
		String genericArg = options.opt_java15 ? "<" + RUNTIME_PACKAGE + ".Symbol>" : "           ";

		/* class header */
		out.println();
		out.println("/** Cup generated class to encapsulate user supplied action code.*/");
		/* TUM changes; proposed by Henning Niss 20050628: added type argument */
		out.println((options.opt_java15 ? "static " : "") + "class " + action_class + " {");
		/* user supplied code */
		if (options.action_code != null) {
			out.println();
			out.println(options.action_code);
		}

		/* field for parser object */
		/* TUM changes; proposed by Henning Niss 20050628: added typeArgument */
		out.println("  private final " + options.parser_class_name + typeArgument() + " parser;");

		/* constructor */
		out.println();
		out.println("  /** Constructor */");
		/* TUM changes; proposed by Henning Niss 20050628: added typeArgument */
		out.println("  " + action_class + "(" + options.parser_class_name + typeArgument() + " parser) {");
		out.println("    this.parser = parser;");
		out.println("  }");

		/* action method head */
		out.println();
		out.println("  /** Method with the actual generated action code. */");
		if (options.opt_java15)
			out.println("  @SuppressWarnings({ \"unused\", \"unchecked\" })");
		out.println("  public final " + RUNTIME_PACKAGE + ".Symbol " + pre("do_action") + "(");
		out.println("    int                        " + pre("act_num,"));
		out.println("    java.util.ArrayList" + genericArg + " " + pre("stack)"));
		out.println("    throws java.lang.Exception");
		out.println("    {");

		out.println("      /* Stack size for peeking into the stack */");
		out.println("      int " + pre("size") + " = " + pre("stack") + ".size();");
		out.println();

		/* switch top */
		out.println("      /* select the action based on the action number */");
		out.println("      switch (" + pre("act_num") + ")");
		out.println("        {");

		/* emit action code for each production as a separate case */
		for (Production prod : grammar.actions()) {
			/* case label */
			for (Production p2 : prod.getLhs().getProductions()) {
				if (p2.getActionIndex() == prod.getActionIndex())
					out.println("          // " + p2.toString());
			}
			out.println("          case " + prod.getActionIndex() + ":");
			/* give them their own block to work in */
			out.println("            {");

			emitAction(out, grammar, prod, options);

			/* end of their block */
			out.println("            }");
			out.println();
		}

		/* end of switch */
		out.println("          /* . . . . . .*/");
		out.println("          default:");
		out.println("            throw new InternalError(");
		out.println("               \"Invalid action number found in " + "internal parse table\");");
		out.println();
		out.println("        }");

		/* end of method */
		out.println("    }");

		/* user supplied code for after reduce code */
		if (options.after_reduce_code != null) {
			out.println();
			out.println("    /** After reduce code */");
			out.println("    public void " + prefix + "after_reduce(Object RESULT, " + RUNTIME_PACKAGE
					+ ".Symbol[] symbols) throws java.lang.Exception");
			out.println("    {");
			out.println(options.after_reduce_code);
			out.println("    }");
		}

		/* end of class */
		out.println("}");
		out.println();
		timer.popTimer(Timer.TIMESTAMP.action_code_time);
	}

	/** create a string encoding a given short[] array. */
	private String translateArrayAsString(short[] sharr) {
		StringBuilder sb = new StringBuilder();
		if (sharr.length >= 0x8000)
			sb.append((char) (0x8000 + (sharr.length >> 16)));
		sb.append((char) (sharr.length & 0xffff));
		for (int i = 0; i < sharr.length; i++)
			sb.append((char) sharr[i]);
		return sb.toString();
	}

	/** create a string encoding a given int[] array. */
	private String translateArrayAsString(int[] intarr) {
		StringBuilder sb = new StringBuilder();
		if (intarr.length >= 0x8000)
			sb.append((char) (0x8000 + (intarr.length >> 16)));
		sb.append((char) (intarr.length & 0xffff));
		for (int i = 0; i < intarr.length; i++) {
			assert (intarr[i] >= 0);
			if (intarr[i] >= 0x8000)
				sb.append((char) (0x8000 + (intarr[i] >> 16)));
			sb.append((char) (intarr[i] & 0xffff));
		}
		return sb.toString();
	}

	/**
	 * Build the production table.
	 * 
	 * @param grammar the grammar to process
	 * @return a String representing the production table
	 */
	private String buildProductionTable(Grammar grammar) {
		timer.pushTimer();

		short[] prod_table = new short[2 * grammar.gatActionCount()];
		for (Production prod : grammar.actions()) {
			prod_table[2 * prod.getActionIndex() + 0] = (short) prod.getLhs().getIndex();
			prod_table[2 * prod.getActionIndex() + 1] = (short) prod.getRhsSize();
		}
		String result = translateArrayAsString(prod_table);
		timer.popTimer(Timer.TIMESTAMP.production_table_time);
		return result;
	}

	/**
	 * Build the action table.
	 * 
	 * @param grammar the grammar to process
	 * @return a String representing the action table
	 */
	private String buildActionTable(Grammar grammar) {
		timer.pushTimer();

		ParseActionTable act_tab = grammar.getActionTable();
		int[] base_tab = new int[act_tab.getTable().length];
		short[] action_tab = act_tab.compress(base_tab);
		String result = translateArrayAsString(base_tab) + translateArrayAsString(action_tab);
		timer.popTimer(Timer.TIMESTAMP.action_table_time);
		return result;
	}

	/**
	 * Build the reduce table.
	 * 
	 * @param grammar the grammar to process
	 * @return a String representing the reduce table
	 */
	private String buildReduceTable(Grammar grammar) {
		timer.pushTimer();

		ParseReduceTable red_tab = grammar.getReduceTable();
		String result = translateArrayAsString(red_tab.compress());
		timer.popTimer(Timer.TIMESTAMP.goto_table_time);
		return result;
	}

	/** print a string in java source code */
	private void output_string(PrintWriter out, String str) {
		int utf8len = 0;
		for (int i = 0; i < str.length(); i += 11) {
			StringBuilder encoded = new StringBuilder();
			encoded.append("    \"");
			for (int j = 0; j < 11 && i + j < str.length(); j++) {
				char c = str.charAt(i + j);
				encoded.append('\\');
				if (c < 256) {
					String oct = "000" + Integer.toOctalString(c);
					oct = oct.substring(oct.length() - 3);
					encoded.append(oct);
				} else {
					String hex = "0000" + Integer.toHexString(c);
					hex = hex.substring(hex.length() - 4);
					encoded.append('u').append(hex);
				}
				utf8len++;
				if (c >= 128 || c == 0) {
					utf8len++;
					if (c >= 2048)
						utf8len++;
				}
			}
			encoded.append("\"");
			if (i + 11 < str.length()) {
				if (utf8len > 65000) {
					encoded.append(",");
					utf8len = 0;
				} else
					encoded.append(" +");
			}
			out.println(encoded.toString());
		}
	}

	/**
	 * Emit the parser subclass with embedded tables.
	 * 
	 * @param out              stream to produce output on.
	 * @param actionTable     internal representation of the action table.
	 * @param reduceTable     internal representation of the reduce-goto table.
	 * @param start_st         start state of the parse machine.
	 * @param start_prod       start production of the grammar.
	 * @param compact_reduces  do we use most frequent reduce as default?
	 * @param suppress_scanner should scanner be suppressed for compatibility?
	 */
	public void parser(PrintWriter out, Grammar grammar) {
		timer.pushTimer();

		String action_class = options.opt_java15 ? "Action$" : pre(options.parser_class_name + "$action");

		/* top of file */
		out.println();
		out.println("//----------------------------------------------------");
		out.println("// The following code was generated by " + Version.title);
		out.println("// " + new Date());
		out.println("//----------------------------------------------------");
		out.println();
		emitPackage(out);

		/* user supplied imports */
		for (String imp : options.import_list)
			out.println("import " + imp + ";");

		/* class header */
		out.println();
		out.println("/** " + Version.title + " generated parser.");
		out.println("  * @version " + new Date());
		out.println("  */");
		/* TUM changes; proposed by Henning Niss 20050628: added typeArgument */
		out.println("public class " + options.parser_class_name + typeArgument() + " extends " + RUNTIME_PACKAGE
				+ ".LRParser {");

		/* constructors [CSA/davidm, 24-jul-99] */
		out.println();
		out.println("  /** Default constructor. */");
		out.println("  public " + options.parser_class_name + "() {super();}");
		if (!options.suppress_scanner) {
			out.println();
			out.println("  /** Constructor which sets the default scanner. */");
			out.println("  public " + options.parser_class_name + "(" + RUNTIME_PACKAGE + ".Scanner s) {super(s);}");
			// TUM 20060327 added SymbolFactory aware constructor
			out.println();
			out.println("  /** Constructor which sets the default scanner. */");
			out.println("  public " + options.parser_class_name + "(" + RUNTIME_PACKAGE + ".Scanner s, "
					+ RUNTIME_PACKAGE + ".SymbolFactory sf) {super(s,sf);}");
		}

		/* emit the various tables */
		String tables = buildProductionTable(grammar) + buildActionTable(grammar) + buildReduceTable(grammar);

		out.println("  /** The static parse table */");
		out.println("  static " + RUNTIME_PACKAGE + ".ParseTable " + pre("parse_table") + " =");
		out.println("    new " + RUNTIME_PACKAGE + ".ParseTable(new String[] {");
		output_string(out, tables);
		out.println("    });");
		out.println();

		out.println("  /** Return parse table */");
		out.println("  protected " + RUNTIME_PACKAGE + ".ParseTable parse_table() {");
		out.println("    return " + pre("parse_table") + ";");
		out.println("  }");
		out.println();

		/* instance of the action encapsulation class */
		out.println("  /** Instance of action encapsulation class. */");
		out.println("  protected " + action_class + " action_obj;");
		out.println();

		/* action object initializer */
		out.println("  /** Action encapsulation object initializer. */");
		out.println("  protected void init_actions()");
		out.println("    {");
		out.println("      action_obj = new " + action_class + "(this);");
		out.println("    }");
		out.println();

		/* access to action code */
		out.println("  /** Invoke a user supplied parse action. */");
		out.println("  public " + RUNTIME_PACKAGE + ".Symbol do_action(");
		out.println("    int                        act_num,");
		if (options.opt_java15)
			out.println("    java.util.ArrayList<" + RUNTIME_PACKAGE + ".Symbol> stack)");
		else
			out.println("    java.util.ArrayList        stack)");
		out.println("    throws java.lang.Exception");
		out.println("  {");
		out.println("    /* call code in generated class */");
		out.println("    return action_obj." + pre("do_action(") + "act_num, stack);");
		out.println("  }");
		out.println("");

		/* user supplied code for user_init() */
		if (options.init_code != null) {
			out.println();
			out.println("  /** User initialization code. */");
			out.println("  public void user_init() throws java.lang.Exception");
			out.println("    {");
			out.println(options.init_code);
			out.println("    }");
		}

		/* user supplied code for scan */
		if (options.scan_code != null) {
			out.println();
			out.println("  /** Scan to get the next Symbol. */");
			out.println("  public " + RUNTIME_PACKAGE + ".Symbol scan()");
			out.println("    throws java.lang.Exception");
			out.println("    {");
			out.println(options.scan_code);
			out.println("    }");
		}

		/* user supplied code */
		if (options.parser_code != null) {
			out.println();
			out.println(options.parser_code);
		}

		/* put out the action code class as inner class */
		emitActionCode(out, grammar, action_class, options);

		/* end of class */
		out.println("}");

		timer.popTimer(Timer.TIMESTAMP.parser_time);
	}

	private void dump_tables(PrintWriter dump_file, Grammar grammar) {
		dump_file.println(grammar.getActionTable());
		dump_file.println(grammar.getReduceTable());
	}

	private void dump_machine(PrintWriter dump_file, Grammar grammar) {
		dump_file.println("===== Viable Prefix Recognizer =====");
		for (LalrState st : grammar.getLalrStates()) {
			dump_file.println(st);
			dump_file.println("-------------------");
		}
	}

	private void dump_grammar(PrintWriter dump_file, Grammar grammar) {
		dump_file.println("===== Terminals =====");
		int cnt = 0;
		for (Terminal t : grammar.terminals()) {
			dump_file.print("[" + t.getIndex() + "]" + t.getName());
			if (t.getType() != null) 
				dump_file.print("<" + t.getType() + ">");
			dump_file.print(" ");				
			if ((++cnt) % 4 == 0)
				dump_file.println();
		}
		dump_file.println();
		dump_file.println();

		dump_file.println("===== Non terminals =====");
		cnt = 0;
		for (NonTerminal nt : grammar.non_terminals()) {
			dump_file.print("[" + nt.getIndex() + "]" + nt.getName());
			if (nt.getType() != null) 
				dump_file.print("<" + nt.getType() + ">");
			dump_file.print(" ");				
			if ((++cnt) % 4 == 0)
				dump_file.println();
		}
		dump_file.println();
		dump_file.println();

		dump_file.println("===== Productions =====");
		for (Production prod : grammar.productions()) {
			dump_file.println("[" + prod.getIndex() + "] " + prod);
		}
		dump_file.println();
	}

	private void dump_messages (PrintWriter dump_file) {
		if (! ErrorManager.getManager().getMessages().isEmpty()) {
			dump_file.println("===== Messages =====");
			for (String message : ErrorManager.getManager().getMessages()) {
				dump_file.println(message);
			}
		}
	}

	public void dumps(PrintWriter dump_file, Grammar grammar) {
		if (options.opt_dump_includes_messages)
			dump_messages(dump_file);
		if (options.opt_dump_grammar)
			dump_grammar(dump_file, grammar);
		if (options.opt_dump_states)
			dump_machine(dump_file, grammar);
		if (options.opt_dump_tables)
			dump_tables(dump_file, grammar);
	}

	public void usage(String message) {
	}

}
