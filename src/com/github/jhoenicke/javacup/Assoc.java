package com.github.jhoenicke.javacup;

/* Defines integers that represent the associativity of terminals
 * @version last updated: 7/3/96
 * @author  Frank Flannery
 */

public class Assoc {

	/* various associativities, no_prec being the default value */
	public final static int LEFT = 0;
	public final static int NONASSOC = 1;
	public final static int RIGHT = 2;
	public final static int NOPREC = -1;

}