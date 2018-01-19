package de.m3y3r.offlinewiki.utility;

import java.util.HashMap;
import java.util.Map;

public class HtmlUtility {

	private static Map<String,Character> entityMap = new HashMap<String,Character>();
	static {
		entityMap.put("&quot;",'\u0022');
		entityMap.put("&amp;",'\u0026');
		entityMap.put("&nbsp;",'\u00a0');
	}

	public static String decodeEntities(CharSequence input) {
		int from = -1, to = -1;
		StringBuffer result = new StringBuffer(input);
		for(int i = 0; i < result.length(); i++) {
			char c = result.charAt(i);

			if(c == '&') {
				from = i;
			} else if(c == ';') {
				to = i;
				if(from >= 0 && to >= 0 && to > from) {
					String entity = result.substring(from, to + 1);
					Character replace = entityMap.get(entity);
					if(replace != null) {
						result.delete(from, to + 1);
						result.insert(from, replace.charValue());
					}
					else {
						System.err.println("Unknown HTML entity: " + entity);
					}
				}
				from = -1;
				to = -1;
			}

		}
		return result.toString();
	}
}
