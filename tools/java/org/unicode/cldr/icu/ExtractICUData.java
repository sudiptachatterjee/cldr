/*
 **********************************************************************
 * Copyright (c) 2002-2004, International Business Machines
 * Corporation and others.  All Rights Reserved.
 **********************************************************************
 * Author: Mark Davis
 **********************************************************************
 */
package org.unicode.cldr.icu;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Utility;

import com.ibm.icu.dev.test.util.CaseIterator;
import com.ibm.icu.dev.test.util.BagFormatter;
import com.ibm.icu.dev.test.util.UnicodeMap;
import com.ibm.icu.impl.ICUResourceBundle;
import com.ibm.icu.impl.PrettyPrinter;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.CanonicalIterator;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.Normalizer;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.text.Transliterator;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.util.UResourceBundle;

public class ExtractICUData {
	public static void main(String[] args) throws Exception {
		generateTransliterators();
		System.out.println("Done");
	}
	
	static Set skipLines = new HashSet(Arrays.asList(new String[]{
			"#--------------------------------------------------------------------",
			"# Copyright (c) 1999-2005, International Business Machines",
			"# Copyright (c) 1999-2004, International Business Machines",
			"# Corporation and others. All Rights Reserved.",
			"#--------------------------------------------------------------------"
	}));
	static Set skipFiles = new HashSet(Arrays.asList(new String[]{
			//"Any_Accents",
			"el",
			"en",
			"root"
	}));
	static void generateTransliterators() throws IOException {
		Matcher fileFilter = Pattern.compile(".*").matcher("");
		
		CLDRFile accumulatedItems = CLDRFile.makeSupplemental("allItems");
		getTranslitIndex(accumulatedItems);
		
		File translitSource = new File("C:\\cvsdata\\icu\\icu\\source\\data\\translit\\");
		System.out.println("Source: " + translitSource.getCanonicalPath());
		File[] fileArray = translitSource.listFiles();
		List list = new ArrayList(Arrays.asList(fileArray));
		
		List extras = Arrays.asList(new String[]{
				"Arabic_Latin.txt", 
				"CanadianAboriginal_Latin.txt", 
				"Cyrillic_Latin.txt", 
				"Georgian_Latin.txt", 
				// "Khmer_Latin.txt", "Lao_Latin.txt", "Tibetan_Latin.txt"
				"Latin_Armenian.txt",
				"Latin_Ethiopic.txt",
				"Syriac_Latin.txt", "Thaana_Latin.txt",});
		list.addAll(extras);
		
		String[] attributesOut = new String[1];
		for (int i = 0; i < list.size(); ++i) {
			Object file = list.get(i);
			String fileName = (file instanceof File) ? ((File)file).getName() : (String)file;
			if (file instanceof File && extras.contains(fileName)) {
				System.out.println("Skipping old version: " + fileName);
			}
			if (!fileName.endsWith(".txt")) continue;
			String coreName = fileName.substring(0,fileName.length()-4);
			if (skipFiles.contains(coreName)) continue;
			String id = fixTransID(coreName, attributesOut);
			String outName = id.replace('/', '-');
			String attributes = attributesOut[0];
			attributes += "[@direction=\"both\"]";
			
			System.out.println(coreName + "\t=>\t" + outName + " => " + attributes);
			
			if (!fileFilter.reset(fileName).matches()) continue;
			
			BufferedReader input;
			if (file instanceof File) {
				//if (true) continue;
				input = BagFormatter.openUTF8Reader(((File)file).getParent() + File.separator, fileName);
			} else {
				input = Utility.getUTF8Data(fileName);
			}
			CLDRFile outFile = CLDRFile.makeSupplemental(fileName);
			int count = 0;
			String prefixBase = "//supplementalData[@version=\"" + CLDRFile.GEN_VERSION + "\"]/transforms/transform" + attributes;
			String rulePrefix = prefixBase + "/tRule[@_q=\"";
			String commentPrefix = prefixBase + "/comment[@_q=\"";
			
			StringBuffer accumulatedLines = new StringBuffer();
			while (true) {
				String line = input.readLine();
				if (line == null) break;
				if (line.startsWith("\uFEFF")) line = line.substring(1); // remove BOM
				line = line.trim();
				if (skipLines.contains(line)) continue;
				if (line.length() == 0) continue;
				String fixedLine = fixTransRule(line);
				//if (accumulatedLines.length() == 0) 
				accumulatedLines.append("\n\t\t");
				accumulatedLines.append(fixedLine);
				String prefix = (line.startsWith("#")) ? commentPrefix : rulePrefix;
				addInTwo(outFile, accumulatedItems, prefix + (++count) + "\"]", fixedLine);
			}
			
			PrintWriter pw = BagFormatter.openUTF8Writer(Utility.GEN_DIRECTORY + "/translit/gen/", outName + ".xml");
			outFile.write(pw);
			pw.close();
			
		}
		PrintWriter pw = BagFormatter.openUTF8Writer(Utility.GEN_DIRECTORY + "/translit/gen/", "All" + ".xml");
		accumulatedItems.write(pw);
		pw.close();
	}
	
	private static void addInTwo(CLDRFile outFile, CLDRFile accumulatedItems, String path, String value) {
		//System.out.println("Adding: " + path + "\t\t" + value);
		outFile.add(path, value);
		accumulatedItems.add(path, value);
	}
	
	private static String fixTransRule(String line) {
		String fixedLine = line;
//		fixedLine = fixedLine.replaceAll("<>", "\u2194");
//		fixedLine = fixedLine.replaceAll("<", "\u2190");
//		fixedLine = fixedLine.replaceAll(">", "\u2192");
//		fixedLine = fixedLine.replaceAll("&", "\u00A7");
		fixedLine = fixLine.transliterate(line);
		return fixedLine;
	}
	
	static String fixLineRules = 
		"'<>' > '\u2194';" +
		"'<' > '\u2190';" +
		"'>' > '\u2192';" +
		"'&' > '\u00A7';" +
		"('\\u00'[0-7][0-9A-Fa-f]) > $1;" + // leave ASCII alone
		"('\\u'[0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]) > |@&hex-any/java($1);" +
		"([[:whitespace:][:Default_Ignorable_Code_Point:][:C:]-[\\u0020\\u200E\\0009]]) > &any-hex/java($1);"
		
		;
	static Transliterator fixLine = Transliterator.createFromRules("foo", fixLineRules, Transliterator.FORWARD);
	
	private static final String INDEX = "index",
	RB_RULE_BASED_IDS ="RuleBasedTransliteratorIDs";
	
	private static void getTranslitIndex(CLDRFile accumulatedItems) throws IOException {
		
		ICUResourceBundle bundle, transIDs, colBund;
		bundle = (ICUResourceBundle)UResourceBundle.getBundleInstance(ICUResourceBundle.ICU_TRANSLIT_BASE_NAME, INDEX);
		transIDs = bundle.get(RB_RULE_BASED_IDS);
		
		String[] attributesOut = new String[1];
		int count = 0;
		
		int maxRows = transIDs.getSize();
		for (int row = 0; row < maxRows; row++) {
			colBund = transIDs.get(row);
			String ID = colBund.getKey();
			ICUResourceBundle res = colBund.get(0);
			String type = res.getKey();
			if (type.equals("file") || type.equals("internal")) {
//				// Rest of line is <resource>:<encoding>:<direction>
//				//                pos       colon      c2
//				String resString = res.getString("resource");
//				String direction = res.getString("direction");
//				result.add(Arrays.asList(new Object[]{ID,
//				resString, // resource
//				"UTF-16", // encoding
//				direction,
//				type}));
			} else if (type.equals("alias")) {
                CLDRFile outFile = CLDRFile.makeSupplemental("transformAliases");
				//'alias'; row[2]=createInstance argument
				ID = fixTransID(ID, attributesOut);
				String outName = ID.replace('/', '-');
				String attributes = attributesOut[0];
				attributes += "[@direction=\"forward\"]";
				System.out.println(ID + " => " + attributes);
				String prefix = "//supplementalData[@version=\"" + CLDRFile.GEN_VERSION + "\"]/transforms/transform"
				+ attributes + "/tRule[@_q=\"";
				String resString = res.getString();
				if (!instanceMatcher.reset(resString).matches()) {
					System.out.println("Doesn't match id: " + resString);
				} else {
					String filter = instanceMatcher.group(1);
					if (filter != null) {
						filter = fixTransRule(filter);
						outFile.add(prefix + (++count) + "\"]", "::" + filter + ";");
						accumulatedItems.add(prefix + (++count) + "\"]", "::" + filter + ";");
					}
					String rest = instanceMatcher.group(2);
					String[] pieces = rest.split(";");
					for (int i = 0; i < pieces.length; ++i) {
						String piece = pieces[i].trim();
						if (piece.length() == 0) continue;
						piece = fixTransID(piece, null);
						outFile.add(prefix + (++count) + "\"]", "::" + piece + ";");
						accumulatedItems.add(prefix + (++count) + "\"]", "::" + piece + ";");
					}
				}
				PrintWriter pw = BagFormatter.openUTF8Writer(Utility.GEN_DIRECTORY + "/translit/gen/", outName + ".xml");
				outFile.write(pw);
				pw.close();				
			} else {
				// Unknown type
				throw new RuntimeException("Unknown type: " + type);
			}
		}
	}
	
	private static String fixTransID(String id, String[] attributesOut) {
		if (!idMatcher.reset(id).matches()) {
			System.out.println("Doesn't match id:: " + id);
		} else {
			String source = fixTransIDPart(idMatcher.group(1));
			String target = fixTransIDPart(idMatcher.group(2));
			String variant = fixTransIDPart(idMatcher.group(3));
			
			if (attributesOut != null) {
				attributesOut[0] = "[@source=\"" + source + "\"]"
				+ "[@target=\"" + target + "\"]"
				+ (variant == null ? "" : "[@variant=\"" + variant + "\"]");
				if (privateFiles.reset(id).matches()) attributesOut[0] += "[@visibility=\"internal\"]";
			}
			
			if (target == null) target = ""; else target = "-" + target;
			if (variant == null) variant = ""; else variant = "/" + variant;
			id = source + target + variant;
		}
		return id;
	}
	
	static String idPattern = "\\s*(\\p{L}+)(?:[_-](\\p{L}+))?(?:\\[_/](\\p{L}+))?";
	static Matcher idMatcher = Pattern.compile(idPattern).matcher("");
	static Matcher instanceMatcher = Pattern.compile("\\s*(\\[.*\\]\\s*)?(.*)").matcher("");
	
//	private static String fixTransName(String name, String[] attributesOut, String separator) {
//	String[] pieces = name.split(separator);
//	String source = fixTransIDPart(pieces[0]);
//	String target = fixTransIDPart(pieces[1]);
//	String variant = null;
//	if (pieces.length > 2) {
//	variant = pieces[2].toUpperCase();
//	}
//	attributesOut[0] = "[@source=\"" + source + "\"]"
//	+ "[@target=\"" + target + "\"]"
//	+ (variant == null ? "" : "[@variant=\"" + variant + "\"]");
//	if (privateFiles.reset(name).matches()) attributesOut[0] += "[@visibility=\"internal\"]";
//	return source + (target == null ? "" : "-") + target + (variant == null ? "" : "/" + variant);
//	}
	
	static Matcher privateFiles = Pattern.compile(".*(Spacedhan|InterIndic|ThaiLogical|ThaiSemi).*").matcher("");
	static Matcher allowNames = Pattern.compile("(Fullwidth|Halfwidth|NumericPinyin|Publishing)").matcher("");
	
	static Set collectedNames = new TreeSet();
	
	private static String fixTransIDPart(String name) {
		if (name == null) return name;
		int code = UScript.getCodeFromName(name);
		if (code < 0) {
			collectedNames.add(name);
//			if (!privateFiles.reset(name).matches() && !allowNames.reset(name).matches()) {
//			System.out.println("\tCan't convert: " + name);
//			}
//			return "x_" + name;
		}
		
		if (name.equals("Tone")) return "Pinyin";
		if (name.equals("Digit")) return "NumericPinyin";
		if (name.equals("Jamo")) return "ConjoiningJamo";
		if (name.equals("LowerLatin")) return "Latin";
		
		return name;
	}
	static void testProps() {
		int[][] ranges = {{UProperty.BINARY_START, UProperty.BINARY_LIMIT},
				{UProperty.INT_START, UProperty.INT_LIMIT},
				{UProperty.DOUBLE_START, UProperty.DOUBLE_START},
				{UProperty.STRING_START, UProperty.STRING_LIMIT},
		};
		Collator col = Collator.getInstance(ULocale.ROOT);
		((RuleBasedCollator)col).setNumericCollation(true);
		Map alpha = new TreeMap(col);
		
		for (int range = 0; range < ranges.length; ++range) {
			for (int propIndex = ranges[range][0]; propIndex < ranges[range][1]; ++propIndex) {
				String propName = UCharacter.getPropertyName(propIndex, UProperty.NameChoice.LONG);
				String shortPropName = UCharacter.getPropertyName(propIndex, UProperty.NameChoice.SHORT);
				propName = getName(propIndex, propName, shortPropName);
				Set valueOrder = new TreeSet(col);
				alpha.put(propName, valueOrder);
				switch (range) {
				case 0: valueOrder.add("[binary]"); break;
				case 2: valueOrder.add("[double]"); break;
				case 3: valueOrder.add("[string]"); break;
				case 1: for (int valueIndex = 0; valueIndex < 256; ++valueIndex) {
					try {
						String valueName = UCharacter.getPropertyValueName(propIndex, valueIndex, UProperty.NameChoice.LONG);
						String shortValueName = UCharacter.getPropertyValueName(propIndex, valueIndex, UProperty.NameChoice.SHORT);
						valueName = getName(valueIndex, valueName, shortValueName);
						valueOrder.add(valueName);
					} catch (RuntimeException e) {
						// just skip
					}
				} break;
				}
			}
		}
		PrintStream out = System.out;
		
		for (Iterator it = alpha.keySet().iterator(); it.hasNext();) {
			String propName = (String) it.next();
			Set values = (Set) alpha.get(propName);
			out.println("<tr><td>" + propName + "</td>");
			out.println("<td><table>");
			for (Iterator it2 = values.iterator(); it2.hasNext();) {
				String propValue = (String) it2.next();
				System.out.println("<tr><td>" + propValue + "</td></tr>");
			}
			out.println("</table></td></tr>");
		}
		
//		int enumValue = UCharacter.getIntPropertyValue(codePoint, propEnum);
//		return UCharacter.getPropertyValueName(propEnum,enumValue, (int)nameChoice);
		
	}
	
	private static String getName(int index, String valueName, String shortValueName) {
		if (valueName == null) {
			if (shortValueName == null) return String.valueOf(index);
			return shortValueName;
		}
		if (shortValueName == null) return valueName;
		if (valueName.equals(shortValueName)) return valueName;
		return valueName + "\u00A0(" + shortValueName + ")";
	}
}