
package com.github.jhoenicke.javacup;

/**
 * This class represents a part of a production which contains an action. These
 * are eventually eliminated from productions and converted to trailing actions
 * by factoring out with a production that derives the empty string (and ends
 * with this action).
 *
 * @see com.github.jhoenicke.javacup.Production
 * @version last update: 11/25/95
 * @author Scott Hudson
 */

public class ActionPart extends ProductionPart {

	/** String containing code for the action in question. */
	private String code;

	/**
	 * Simple constructor.
	 * 
	 * @param code_str string containing the actual user code.
	 */
	public ActionPart(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public void addCode(String moreCode) {
		code += moreCode;
	}

	public String toString() {
		return super.toString() + "{" + getCode() + "}";
	}

}
