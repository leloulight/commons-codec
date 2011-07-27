/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.codec.language.bm;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <p>
 * A phoneme rule.
 * </p>
 * <p>
 * Rules have a pattern, left context, right context, output phoneme, set of languages for which they apply and a logical flag indicating if
 * all lanugages must be in play. A rule matches if:
 * <ul>
 * <li>the pattern matches at the current position</li>
 * <li>the string up until the beginning of the pattern matches the left context</li>
 * <li>the string from the end of the pattern matches the right context</li>
 * <li>logical is ALL and all languages are in scope; or</li>
 * <li>logical is any other value and at least one language is in scope</li>
 * </ul>
 * </p>
 * <p>
 * Rules are typically generated by parsing rules resources. In normal use, there will be no need for the user to explicitly construct their
 * own.
 * </p>
 * <p>
 * Rules are immutable and thread-safe.
 * <h2>Rules resources</h2>
 * <p>
 * Rules are typically loaded from resource files. These are UTF-8 encoded text files. They are systematically named following the pattern:
 * <blockquote>org/apache/commons/codec/language/bm/${NameType#getName}_${RuleType#getName}_${language}.txt</blockquote>
 * </p>
 * <p>
 * The format of these resources is the following:
 * <ul>
 * <li><b>Rules:</b> whitespace separated, double-quoted strings. There should be 4 columns to each row, and these will be interpreted as:
 * <ol>
 * <li>pattern</li>
 * <li>left context</li>
 * <li>right context</li>
 * <li>phoneme</li>
 * </ol>
 * </li>
 * <li><b>End-of-line comments:</b> Any occurance of '//' will cause all text following on that line to be discarded as a comment.</li>
 * <li><b>Multi-line comments:</b> Any line starting with '/*' will start multi-line commenting mode. This will skip all content until a
 * line ending in '*' and '/' is found.</li>
 * <li><b>Blank lines:</b> All blank lines will be skipped.</li>
 * </ul>
 * </p>
 * 
 * @author Apache Software Foundation
 * @since 2.0
 */
public class Rule {
    private static final String DOUBLE_QUOTE = "\"";

    public static final String ALL = "ALL";

    private static final String HASH_INCLUDE = "#include";

    private static final Map<NameType, Map<RuleType, Map<String, List<Rule>>>> RULES = new EnumMap<NameType, Map<RuleType, Map<String, List<Rule>>>>(
            NameType.class);

    static {
        for (NameType s : NameType.values()) {
            Map<RuleType, Map<String, List<Rule>>> rts = new EnumMap<RuleType, Map<String, List<Rule>>>(RuleType.class);

            for (RuleType rt : RuleType.values()) {
                Map<String, List<Rule>> rs = new HashMap<String, List<Rule>>();

                Languages ls = Languages.instance(s);
                for (String l : ls.getLanguages()) {
                    rs.put(l, parseRules(mkScanner(s, rt, l)));
                }
                if (!rt.equals(RuleType.RULES)) {
                    rs.put("common", parseRules(mkScanner(s, rt, "common")));
                }

                rts.put(rt, Collections.unmodifiableMap(rs));
            }

            RULES.put(s, Collections.unmodifiableMap(rts));
        }
    }

    /**
     * Gets rules for a combination of name type, rule type and languages.
     * 
     * @param nameType
     *            the NameType to consider
     * @param rt
     *            the RuleType to consider
     * @param langs
     *            the set of languages to consider
     * @return a list of Rules that apply
     */
    public static List<Rule> instance(NameType nameType, RuleType rt, Set<String> langs) {
        if (langs.size() == 1) {
            return instance(nameType, rt, langs.iterator().next());
        } else {
            return instance(nameType, rt, "any");
        }
    }

    /**
     * Gets rules for a combination of name type, rule type and a single language.
     * 
     * @param nameType
     *            the NameType to consider
     * @param rt
     *            the RuleType to consider
     * @param lang
     *            the language to consider
     * @return a list rules for a combination of name type, rule type and a single language.
     */
    public static List<Rule> instance(NameType nameType, RuleType rt, String lang) {
        List<Rule> rules = RULES.get(nameType).get(rt).get(lang);

        if (rules == null) {
            throw new IllegalArgumentException(String.format("No rules found for %s, %s, %s.", nameType.getName(), rt.getName(), lang));
        }

        return rules;
    }

    private static Scanner mkScanner(NameType nameType, RuleType rt, String lang) {
        String resName = String.format("org/apache/commons/codec/language/bm/%s_%s_%s.txt", nameType.getName(), rt.getName(), lang);
        InputStream rulesIS = Languages.class.getClassLoader().getResourceAsStream(resName);

        if (rulesIS == null) {
            throw new IllegalArgumentException("Unable to load resource: " + resName);
        }

        return new Scanner(rulesIS, ResourceConstants.ENCODING);
    }

    private static Scanner mkScanner(String lang) {
        String resName = String.format("org/apache/commons/codec/language/bm/%s.txt", lang);
        InputStream rulesIS = Languages.class.getClassLoader().getResourceAsStream(resName);

        if (rulesIS == null) {
            throw new IllegalArgumentException("Unable to load resource: " + resName);
        }

        return new Scanner(rulesIS, ResourceConstants.ENCODING);
    }

    private static List<Rule> parseRules(Scanner scanner) {
        List<Rule> lines = new ArrayList<Rule>();

        boolean inMultilineComment = false;
        while (scanner.hasNextLine()) {
            String rawLine = scanner.nextLine();
            String line = rawLine;

            if (inMultilineComment) {
                if (line.endsWith(ResourceConstants.EXT_CMT_END)) {
                    inMultilineComment = false;
                } else {
                    // skip
                }
            } else {
                if (line.startsWith(ResourceConstants.EXT_CMT_START)) {
                    inMultilineComment = true;
                } else {
                    // discard comments
                    int cmtI = line.indexOf(ResourceConstants.CMT);
                    if (cmtI >= 0) {
                        line = line.substring(0, cmtI);
                    }

                    // trim leading-trailing whitespace
                    line = line.trim();

                    if (line.length() == 0)
                        continue; // empty lines can be safely skipped

                    if (line.startsWith(HASH_INCLUDE)) {
                        // include statement
                        String incl = line.substring(HASH_INCLUDE.length()).trim();
                        if (incl.contains(" ")) {
                            System.err.println("Warining: malformed import statement: " + rawLine);
                        } else {
                            lines.addAll(parseRules(mkScanner(incl)));
                        }
                    } else {
                        // rule
                        String[] parts = line.split("\\s+");
                        if (parts.length != 4) {
                            System.err.println("Warning: malformed rule statement split into " + parts.length + " parts: " + rawLine);
                        } else {
                            String pat = stripQuotes(parts[0]);
                            String lCon = stripQuotes(parts[1]);
                            String rCon = stripQuotes(parts[2]);
                            String ph = stripQuotes(parts[3]);
                            Rule r = new Rule(pat, lCon, rCon, ph, Collections.<String> emptySet(), ""); // guessing last 2 parameters
                            lines.add(r);
                        }
                    }
                }
            }
        }

        return lines;
    }

    private static String stripQuotes(String str) {
        if (str.startsWith(DOUBLE_QUOTE)) {
            str = str.substring(1);
        }

        if (str.endsWith(DOUBLE_QUOTE)) {
            str = str.substring(0, str.length() - 1);
        }

        return str;
    }

    private Set<String> language;

    private Pattern lContext;

    private String logical;

    private String pattern;

    private String phoneme;

    private Pattern rContext;

    /**
     * Creates a new rule.
     * 
     * @param pattern
     *            the pattern
     * @param lContext
     *            the left context
     * @param rContext
     *            the right context
     * @param phoneme
     *            the resulting phoneme
     * @param language
     *            the required languages
     * @param logical
     *            flag to indicate if all or only some languages must be in scope
     */
    public Rule(String pattern, String lContext, String rContext, String phoneme, Set<String> language, String logical) {
        this.pattern = pattern;
        this.lContext = Pattern.compile(lContext + "$");
        this.rContext = Pattern.compile("^" + rContext + ".*");
        this.phoneme = phoneme;
        this.language = language;
        this.logical = logical;
    }

    /**
     * Gets the languages that must be in scope. Not all rules apply in every language.
     * 
     * @return a Set of Strings giving the relevant languages
     */
    public Set<String> getLanguage() {
        return this.language;
    }

    /**
     * Gets the left context. This is a regular expression that must match to the left of the pattern.
     * 
     * @return the left context Pattern
     */
    public Pattern getLContext() {
        return this.lContext;
    }

    /**
     * Gets the logical combinator for the languages. ALL means all languages must be in scope for the rule to apply. Any other value means
     * that any one language must be in scope for the rule to apply.
     * 
     * @return the logical combinator String
     */
    public String getLogical() {
        return this.logical;
    }

    /**
     * Gets the pattern. This is a string-literal that must exactly match.
     * 
     * @return the pattern
     */
    public String getPattern() {
        return this.pattern;
    }

    /**
     * Gets the phoneme. If the rule matches, this is the phoneme associated with the pattern match.
     * 
     * @return the phoneme
     */
    public String getPhoneme() {
        return this.phoneme;
    }

    /**
     * Gets the right context. This is a regular expression that must match to the right of the pattern.
     * 
     * @return the right context Pattern
     */
    public Pattern getRContext() {
        return this.rContext;
    }

    /**
     * Decides if the language restriction for this rule applies.
     * 
     * @param languageArg
     *            a Set of Strings giving the names of the languages in scope
     * @return true if these satistfy the language and logical restrictions on this rule, false otherwise
     */
    public boolean languageMatches(Set<String> languageArg) {
        if (!languageArg.contains(Languages.ANY) && !this.language.isEmpty()) {
            if (ALL.equals(this.logical) && !languageArg.containsAll(this.language)) {
                return false;
            } else {
                Set<String> isect = new HashSet<String>(languageArg);
                isect.retainAll(this.language);
                return !isect.isEmpty();
            }
        } else {
            return true;
        }
    }

    /**
     * Decides if the pattern and context match the input starting at a position.
     * 
     * @param input
     *            the input String
     * @param i
     *            the int position within the input
     * @return true if the pattern and left/right context match, false otherwise
     */
    public boolean patternAndContextMatches(String input, int i) {
        if (i < 0)
            throw new IndexOutOfBoundsException("Can not match pattern at negative indexes");

        int patternLength = this.pattern.length();
        int ipl = i + patternLength;

        if (ipl > input.length()) {
            // not enough room for the pattern to match
            return false;
        }

        boolean patternMatches = input.substring(i, ipl).equals(this.pattern);
        boolean rContextMatches = this.rContext.matcher(input.substring(ipl)).find();
        boolean lContextMatches = this.lContext.matcher(input.substring(0, i)).find();

        return patternMatches && rContextMatches && lContextMatches;
    }

}