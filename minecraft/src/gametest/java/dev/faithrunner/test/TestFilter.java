package dev.faithrunner.test;

import java.util.Arrays;

/**
 * -Ptests=Fixtures,HandOff: only those suites run (by class name, with or without the "Faith"
 * and "Test" around it); the rest return at once. Without it, all of them.
 */
final class TestFilter {
	private TestFilter() {}

	static boolean skip(Class<?> suite) {
		String wanted = System.getProperty("faithrunner.tests", "").trim();
		if (wanted.isEmpty()) {
			return false;
		}
		String name = suite.getSimpleName();
		boolean run = Arrays.stream(wanted.split(",")).map(String::trim).anyMatch(w -> !w.isEmpty()
			&& (name.equalsIgnoreCase(w) || name.equalsIgnoreCase("Faith" + w + "Test") || name.equalsIgnoreCase(w + "Test")));
		if (!run) {
			System.out.println("FAITH-TESTS skipping " + name + " (-Ptests=" + wanted + ")");
		}
		return !run;
	}
}
