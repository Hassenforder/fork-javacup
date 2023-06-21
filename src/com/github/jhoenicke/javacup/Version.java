
package com.github.jhoenicke.javacup;

/**
 * This class contains version and authorship information. It contains only
 * static data elements and basically just a central place to put this kind of
 * information so it can be updated easily for each release.
 *
 * Version numbers used here are broken into 3 parts: major, minor, and update,
 * and are written as v<major>.<minor>.<update> (e.g. v0.10a). Major numbers
 * will change at the time of major reworking of some part of the system. Minor
 * numbers for each public release or change big enough to cause
 * incompatibilities. Finally update letter will be incremented for small bug
 * fixes and changes that probably wouldn't be noticed by a user.
 *
 * @author Frank Flannery
 */

public class Version {
	
	/** The major version number. */
	private static final int major = 1;

	/** The minor version number. */
	private static final int minor = 3;

	/** The update letter. */
	private static final String update = " 20230621";

	/** String for the current version. */
	public static final String version = "" + major + "." + minor + update;

	/** Full title of the system */
	public static final String title = "jh-javacup-" + version;

	/** Name of the author */
	public static final String authors = "Scott E. Hudson, Frank Flannery, Andrea Flexeder, Michael Petter, C. Scott Ananian, Jochen Hoenicke and Michel Hassenforder";

	/** The command name normally used to invoke this program */
	public static final String programName = "java -jar jh-javacup" + major + "." + minor + ".jar";
}
