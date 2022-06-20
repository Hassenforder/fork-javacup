package com.github.jhoenicke.javacup;

import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

/**
 * many times are required, we have a stack to collect them
 * when you need to measure an elapsed time
 * you push a timer on to the stack at beginning
 * and you pop the timer at the end and give it a name, 
 * elapsed time will be stored in the TreeMap
 * later it is possible to gather all times by name.
 * 
 */
public class Timer {

	/** internal way to register known elapsed time */
	public enum TIMESTAMP {
		preliminary_time,
		final_time,

		parse_time,
		check_time,
		build_time,
		emit_time,
		dump_time,
		
		nullability_time,
		first_time,
		machine_time,
		table_time,
		reduce_check_time,
		
		symbols_time,
		action_code_time,
		production_table_time,
		action_table_time,
		goto_table_time,
		parser_time,
		
	}

	/** a stack with all starting times */
	private Stack<Long> starts;

	/** a registry of elapsed time */
	private Map<TIMESTAMP, Long> times = null;

	private Stack<Long> getStarts() {
		if (starts == null)
			starts = new Stack<>();
		return starts;
	}

	private Map<TIMESTAMP, Long> getTimes() {
		if (times == null)
			times = new TreeMap<>();
		return times;
	}

	/**
	 * a way to clear all running timers
	 */
	public void clearAllTimers() {
		starts = null;
		times = null;
	}

	/**
	 * push current time on the stack
	 */
	public void pushTimer() {
		getStarts().add(System.currentTimeMillis());
	}

	/**
	 * pop a timer from stack
	 * computes elapsed time with the poped timer
	 * register it in a Map with a name
	 * 
	 * @param timeStamp the name of the elapsed time
	 */
	public void popTimer(TIMESTAMP timeStamp) {
		if (getStarts().isEmpty()) {
			ErrorManager.getManager().emit_fatal("Timer stack empty for : " + timeStamp.name());
		}
		long started = getStarts().pop();
		long current = System.currentTimeMillis();
		getTimes().put(timeStamp, current - started);
	}

	/**
	 * A way to insert in the map a known elapsed time
	 * 
	 * @param timeStamp
	 */
	public void insertTime(TIMESTAMP timeStamp) {
		getTimes().put(timeStamp, System.currentTimeMillis());
	}

	/**
	 * 
	 * @param timeStamp the name of the elapsed time
	 * 
	 * @return if this key exists
	 */
	public boolean hasTime(TIMESTAMP timeStamp) {
		return getTimes().containsKey(timeStamp);
	}

	/**
	 * 
	 * @param timeStamp the name of the elapsed time
	 * 
	 * @return the associated elapsed time or 0
	 */
	public long getTime(TIMESTAMP timeStamp) {
		return getTimes().get(timeStamp);
	}

}
