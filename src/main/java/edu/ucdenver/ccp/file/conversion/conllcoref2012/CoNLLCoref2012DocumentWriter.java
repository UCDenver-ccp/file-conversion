package edu.ucdenver.ccp.file.conversion.conllcoref2012;

/*-
 * #%L
 * Colorado Computational Pharmacology's file conversion
 * 						project
 * %%
 * Copyright (C) 2019 Regents of the University of Colorado
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the Regents of the University of Colorado nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.collections.CollectionsUtil.SortOrder;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileWriterUtil;
import edu.ucdenver.ccp.craft.coreference.CleanCorefAnnotations;
import edu.ucdenver.ccp.file.conversion.DocumentWriter;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.file.conversion.conllu.CoNLLUDocumentWriter;
import edu.ucdenver.ccp.file.conversion.conllu.CoNLLUFileRecord;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.SpanUtils;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.ClassMention;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;

/**
 * Writes IDENTITY chains to the CoNLLCoref 2011/2012 file format.
 */
public class CoNLLCoref2012DocumentWriter extends DocumentWriter {

	/**
	 * The original intent of this enum was to allow APPOS relations to also be output in the
	 * CoNLLCoref format, however there are APPOS relations that share an attribute with other APPOS
	 * relations, thus would have overlapping mentions. Overlapping mentions are not supported in
	 * the CoNLLCoref format, so the APPOS output has been disabled.
	 *
	 */
	public enum IncludeCorefType {
		IDENT, APPOS
	}

	/**
	 * the chain merge operation happens twice. First to merge where there are shared mentions
	 * between the chains, then again after the chain mention spans have been mapped to token
	 * boundaries. This enum allows us to indicate if a chain merge is due to a shared mention from
	 * the original data or for a shared mention caused by token boundary mapping.
	 */
	public enum MatchDueTo {
		SHARED_MENTION, SPAN_TO_TOKEN_BOUNDARY_MATCH
	}

	public CoNLLCoref2012DocumentWriter() {
		super();
	}

	static final String START_STATUS_INDICATOR = "BEGIN";
	static final String END_STATUS_INDICATOR = "END";

	private static final Logger logger = Logger.getLogger(CoNLLCoref2012DocumentWriter.class);

	@Override
	public void serialize(TextDocument td, OutputStream outputStream, CharacterEncoding encoding) throws IOException {

		/*
		 * check for any leading or trailing white space in annotations, adjust spans as necessary
		 * to remove whitespace
		 */
		List<TextAnnotation> annotations = td.getAnnotations();
		trimAnnotations(annotations, td.getText());

		/*
		 * remove any "Nonreferential pronoun" annotations. These are part of the CRAFT coreference
		 * project, but should not be included in the CoNLL-Coref 2011/12 output format.
		 */
		List<TextAnnotation> annotsToRemove = new ArrayList<TextAnnotation>();
		for (TextAnnotation annot : annotations) {
			if (annot.getClassMention().getMentionName().equals(CleanCorefAnnotations.NONREFERENTIAL_PRONOUN)
					|| annot.getClassMention().getMentionName().equals(CleanCorefAnnotations.PARTONYMY_RELATION)) {
				annotsToRemove.add(annot);
			}
		}
		for (TextAnnotation annotToRemove : annotsToRemove) {
			annotations.remove(annotToRemove);
		}

		/*
		 * TD assumed to contain sentence & token/pos annotations + single/multi-word base NP
		 * annotations linked into IDENT chains and APPOS relations
		 */

		List<TextAnnotation> sentenceAndTokenAnnots = filterSentenceAndTokenAnnots(annotations);
		if (sentenceAndTokenAnnots.size() == 0) {
			throw new IllegalArgumentException("No sentence/token annotations were detected in the input document. "
					+ "Unable to write the CoNLLCoref 2011/12 format without sentence and token annotations.");
		}

		/*
		 * group annotations by identity chain -- isolated appos relation = chain of length 2
		 */
		Map<TextAnnotation, Set<TextAnnotation>> chainAnnotToMemberAnnotsMap = getCoreferenceChains(
				TextAnnotationFactory.createFactoryWithDefaults(td.getSourceid()), td.getText(), annotations, null);

		Set<Set<TextAnnotation>> chains = new HashSet<Set<TextAnnotation>>(chainAnnotToMemberAnnotsMap.values());
		/*
		 * if there is an annotation that is a member of >1 chains, then those chains should be
		 * combined - this step fixes some annotation errors. Ideally this step would not change the
		 * annotation at all. This step is only relevan for IDENTITY chains.
		 */
		chains = mergeChainsIfSharedAnnotation(chains, MatchDueTo.SHARED_MENTION);

		/*
		 * The structure of the CoNLL Coref 2011/12 file format is similar to that of CoNLL-U. It
		 * lists tokens sequentially with line breaks at sentence boundaries. We can use logic in
		 * the CoNLL-U Document Writer to get the token ordering.
		 */
		List<CoNLLUFileRecord> records = CoNLLUDocumentWriter.generateRecords(sentenceAndTokenAnnots);

		Map<Integer, CoNLLUFileRecord> tokenStartIndexToRecordMap = new HashMap<Integer, CoNLLUFileRecord>();
		Map<Integer, CoNLLUFileRecord> tokenEndIndexToRecordMap = new HashMap<Integer, CoNLLUFileRecord>();

		populateTokenIndexToRecordMaps(records, tokenStartIndexToRecordMap, tokenEndIndexToRecordMap);

		Map<Integer, CoNLLUFileRecord> sortedTokenStartIndexToRecordMap = CollectionsUtil
				.sortMapByKeys(tokenStartIndexToRecordMap, SortOrder.ASCENDING);
		Map<Integer, CoNLLUFileRecord> sortedTokenEndIndexToRecordMap = CollectionsUtil
				.sortMapByKeys(tokenEndIndexToRecordMap, SortOrder.ASCENDING);

		/*
		 * update spans to match token boundaries. This can sometimes cause two chains to share a
		 * mention. If so, we need to re-merge.
		 */
		mapSpansToTokenBoundaries(chains, sortedTokenStartIndexToRecordMap, sortedTokenEndIndexToRecordMap);

		/*
		 * re-merge in case there were collision when matching token boundaries
		 */
		chains = mergeChainsIfSharedAnnotation(chains, MatchDueTo.SPAN_TO_TOKEN_BOUNDARY_MATCH);

		/*
		 * Sort the chains to provide reproducibility in the chain numbering. This is beneficial for
		 * unit testing, and could be potentially beneficial in production as well.
		 */
		List<Set<TextAnnotation>> sortedChains = sortChains(chains);

		/*
		 * the miscellaneous field of each record should have one and only one span indication
		 */
		for (CoNLLUFileRecord record : records) {
			if (record.getWordIndex() > -1 && !record.getMiscellaneous().matches("SPAN_\\d+\\|\\d+")) {
				throw new IllegalStateException(
						"misc doesn't match single span pattern: " + record.getMiscellaneous() + ";;;");
			}
		}

		/*
		 * Add an indicator to the miscellaneous column of the CoNLLURecord to indicate if a chain
		 * member starts or ends at a given token
		 */
		int chainCount = 1;
		for (Set<TextAnnotation> chain : sortedChains) {

			int discontinuousMentionCount = 0;
			for (TextAnnotation annot : chain) {

				String mentionId = null;
				if (annot.getSpans().size() > 1) {
					mentionId = getDiscontinuousMentionId(discontinuousMentionCount++);
				}
				for (Span span : annot.getSpans()) {
					int spanStart = span.getSpanStart();
					int spanEnd = span.getSpanEnd();
					CoNLLUFileRecord startRecord = sortedTokenStartIndexToRecordMap.get(spanStart);

					if (startRecord == null) {
						/*
						 * should not be null at this point b/c the annotation spans have been
						 * mapped to token boundaries
						 */
						throw new IllegalStateException(
								"Should not be null. Could not find record for start offset: " + spanStart);
					}
					String status = (mentionId == null) ? START_STATUS_INDICATOR + "_" + chainCount
							: START_STATUS_INDICATOR + "_" + chainCount + mentionId;
					List<String> startStatus = stringToList(startRecord.getMiscellaneous());
					startStatus.add(status);
					startRecord.setMiscellaneous(listToString(startStatus));
					CoNLLUFileRecord endRecord = sortedTokenEndIndexToRecordMap.get(spanEnd);
					if (endRecord == null) {
						/*
						 * should not be null at this point b/c the annotation spans have been
						 * mapped to token boundaries
						 */
						throw new IllegalStateException(
								"Should not be null. Could not find record for end offset: " + spanEnd);
					}
					List<String> endStatus = stringToList(endRecord.getMiscellaneous());
					status = (mentionId == null) ? END_STATUS_INDICATOR + "_" + chainCount
							: END_STATUS_INDICATOR + "_" + chainCount + mentionId;
					endStatus.add(status);

					// the misc column should already contain an indicator of
					// the
					// token span, e.g. 'SPAN_0|5'
					endRecord.setMiscellaneous(listToString(endStatus));

				}
			}
			chainCount++;
		}

		/* now write the coref chains to file */
		try (BufferedWriter writer = FileWriterUtil.initBufferedWriter(outputStream, encoding)) {
			/* header is necessary for the evaluation code to run */
			writer.write("#begin document (" + td.getSourceid() + "); part 000\n");
			for (CoNLLUFileRecord record : records) {
				if (record.getWordIndex() > 0) {
					writer.write(toCoNLLCoref2012FormatString(record, td.getSourceid()) + "\n");
				} else {
					writer.write("\n");
				}
			}
		}

	}

	private void mapSpansToTokenBoundaries(Set<Set<TextAnnotation>> chains,
			Map<Integer, CoNLLUFileRecord> sortedTokenStartIndexToRecordMap,
			Map<Integer, CoNLLUFileRecord> sortedTokenEndIndexToRecordMap) {
		for (Set<TextAnnotation> chain : chains) {
			for (TextAnnotation annot : chain) {
				List<Span> updatedSpans = new ArrayList<Span>();
				for (Span span : annot.getSpans()) {
					int spanStart = span.getSpanStart();
					int spanEnd = span.getSpanEnd();
					CoNLLUFileRecord startRecord = sortedTokenStartIndexToRecordMap.get(spanStart);
					if (startRecord == null) {
						/*
						 * coreference noun phrases may not exactly align with the tokenization of
						 * the document. In cases like this, we consider the token overlapping the
						 * start index to be the start of the coreference.
						 */
						startRecord = findOverlappingStartToken(sortedTokenStartIndexToRecordMap, spanStart);
					}
					CoNLLUFileRecord endRecord = sortedTokenEndIndexToRecordMap.get(spanEnd);
					if (endRecord == null) {
						endRecord = findOverlappingEndToken(sortedTokenEndIndexToRecordMap, spanEnd);
					}
					int newSpanStart = getSpan(startRecord).getSpanStart();
					int newSpanEnd = getSpan(endRecord).getSpanEnd();
					updatedSpans.add(new Span(newSpanStart, newSpanEnd));
				}
				annot.setSpans(updatedSpans);
			}
		}
	}

	private Span getSpan(CoNLLUFileRecord record) {
		String miscellaneous = record.getMiscellaneous();
		Pattern p = Pattern.compile("SPAN_(\\d+)\\|(\\d+)");
		Matcher m = p.matcher(miscellaneous);
		if (m.find()) {
			int spanStart = Integer.parseInt(m.group(1));
			int spanEnd = Integer.parseInt(m.group(2));
			return new Span(spanStart, spanEnd);
		}
		throw new IllegalArgumentException("unable to extract span from miscellaneous column: " + record.toString());
	}

	/**
	 * @param index
	 * @return for the input integer, return a unique string of characters that will serve as the
	 *         discontinuous mention id
	 */
	static String getDiscontinuousMentionId(int index) {
		Vector<String> letters = new Vector<String>(CollectionsUtil.createList("a", "b", "c", "d", "e", "f", "g", "h",
				"i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"));

		StringBuffer sb = new StringBuffer();
		do {
			sb.append(letters.get(index % letters.size()));
			index = index - letters.size();
		} while (index >= 0);

		return sb.toString();
	}

	/**
	 * @param annotations
	 * @param text
	 * @return annotations with any leading or trailing whitespace removed
	 */
	private void trimAnnotations(List<TextAnnotation> annotations, String docText) {
		Set<TextAnnotation> blankAnnots = new HashSet<TextAnnotation>();
		for (TextAnnotation annot : annotations) {
			String coveredText = SpanUtils.getCoveredText(annot.getSpans(), docText);
			annot.setCoveredText(coveredText);
			if (annot.getCoveredText().trim().length() != annot.getCoveredText().length()) {
				if (annot.getCoveredText().trim().length() == 0) {
					blankAnnots.add(annot);
					logger.info("<<<<<<< Detected BLANK annot: " + annot.getSingleLineRepresentation());
				} else {
					logger.info("<<<<<<< Detected leading or trailing whitespace for annot: "
							+ annot.getSingleLineRepresentation());
					CoNLLCoref2012DocumentReader.trimAnnotation(docText, annot);
				}
			}
		}

		/* remove whitespace annotations */
		for (TextAnnotation blankAnnot : blankAnnots) {
			annotations.remove(blankAnnot);
		}
	}

	/**
	 * @param sortedTokenEndIndexToRecordMap
	 * @param spanStart
	 * @return the record that the specified span offset overlaps
	 */
	static CoNLLUFileRecord findOverlappingStartToken(Map<Integer, CoNLLUFileRecord> sortedTokenIndexToRecordMap,
			int spanOffset) {

		CoNLLUFileRecord record = null;
		for (Entry<Integer, CoNLLUFileRecord> entry : sortedTokenIndexToRecordMap.entrySet()) {
			if (spanOffset < entry.getKey()) {
				return record;
			}
			record = entry.getValue();
		}

		logger.info("Returning final record when searching for overlapping token.");
		return record;

	}

	static CoNLLUFileRecord findOverlappingEndToken(Map<Integer, CoNLLUFileRecord> sortedTokenIndexToRecordMap,
			int spanOffset) {

		CoNLLUFileRecord record = null;
		for (Entry<Integer, CoNLLUFileRecord> entry : sortedTokenIndexToRecordMap.entrySet()) {
			record = entry.getValue();
			if (spanOffset < entry.getKey()) {
				return record;
			}
		}

		logger.info("Returning final record when searching for overlapping token.");
		return record;

	}

	/**
	 * @param consolidatedChains
	 * @return sorted list of chains (sorted by appearance of first annotation in the chain).
	 *         Sorting adds reproducibility -- necessary for unit testing so that the chains have
	 *         reproducible identifiers.
	 */
	private List<Set<TextAnnotation>> sortChains(Set<Set<TextAnnotation>> chains) {

		Map<TextAnnotation, Set<TextAnnotation>> map = new HashMap<TextAnnotation, Set<TextAnnotation>>();

		for (Set<TextAnnotation> set : chains) {
			TextAnnotation firstAnnot = getFirstAppearingAnnot(set);
			map.put(firstAnnot, set);
		}

		ArrayList<TextAnnotation> taList = new ArrayList<TextAnnotation>(map.keySet());
		Collections.sort(taList, TextAnnotation.BY_SPAN());

		List<Set<TextAnnotation>> returnList = new ArrayList<Set<TextAnnotation>>();
		for (TextAnnotation ta : taList) {
			returnList.add(map.get(ta));
		}

		return returnList;
	}

	private TextAnnotation getFirstAppearingAnnot(Set<TextAnnotation> set) {
		List<TextAnnotation> list = new ArrayList<TextAnnotation>(set);
		Collections.sort(list, TextAnnotation.BY_SPAN());

		return list.get(0);
	}

	/**
	 * This method fixes presumable annotation errors by joining chains that share an annotation,
	 * e.g. if there are two chains [A B C] and [B D] then they should be joined to become [A B C D]
	 * 
	 * @param chains
	 * @param matchDueTo
	 * @return
	 */
	public static Set<Set<TextAnnotation>> mergeChainsIfSharedAnnotation(Set<Set<TextAnnotation>> chains,
			MatchDueTo matchDueTo) {
		Set<Set<TextAnnotation>> updatedChains = new HashSet<Set<TextAnnotation>>(chains);
		int previousChainCount = -1;
		/* merge chains until there are no more chains to merge */
		do {
			previousChainCount = updatedChains.size();
			updatedChains = mergeChains(updatedChains, matchDueTo);
		} while (updatedChains.size() != previousChainCount);

		return updatedChains;
	}

	private static Set<Set<TextAnnotation>> mergeChains(Set<Set<TextAnnotation>> chains, MatchDueTo matchDueTo) {
		// System.out.println("Merging...");
		Map<TextAnnotation, Set<Set<TextAnnotation>>> taToChainMap = new HashMap<TextAnnotation, Set<Set<TextAnnotation>>>();

		/*
		 * the map above will catch an NP that occurs in >1 identity chains, however we are not
		 * catching an NP and an APPOS that have the same span that occur in the same identity
		 * chain. These should also be candidates to merge. The map below will be used to detect
		 * chain members that have identical spans.
		 */

		Map<List<Span>, Set<TextAnnotation>> spanToAnnotMap = new HashMap<List<Span>, Set<TextAnnotation>>();

		/* map each text annotation to its chain(s) */
		for (Set<TextAnnotation> chain : chains) {
			for (TextAnnotation ta : chain) {
				CollectionsUtil.addToOne2ManyUniqueMap(ta.getSpans(), ta, spanToAnnotMap);
				CollectionsUtil.addToOne2ManyUniqueMap(ta, chain, taToChainMap);
			}
		}

		for (Entry<List<Span>, Set<TextAnnotation>> entry : spanToAnnotMap.entrySet()) {
			if (entry.getValue().size() > 1) {
				/*
				 * the we have detected chain members with identical spans - should be a Noun Phrase
				 * and an APPOS annotation. We will replace usage of the NP with usage of the APPOS
				 * annotation for consistency.
				 */
				if (entry.getValue().size() > 2) {
					throw new IllegalStateException(
							"Did not expect more than 2 annotations here. Expected 1 Noun Phrase and 1 Appos with identical spans.");
				}

				TextAnnotation npAnnot = getNpAnnot(entry.getValue());
				TextAnnotation apposAnnot = getApposAnnot(entry.getValue());

				logger.info("#### Swapping Noun Phrase for APPOS annotation in identity chain(s)");
				logger.info(
						"The chain(s) listed below have as members a Noun Phrase annotation and an APPOS annotation with identical spans. For consistency, we remove the Noun Phrase annotation where used and replace its usage with the APPOS annotation.");

				logger.info("The Noun Phrase:" + toLogString(npAnnot));
				logger.info("The APPOS annot:" + toLogString(apposAnnot));

				Set<Set<TextAnnotation>> npAnnotChains = taToChainMap.get(npAnnot);
				for (Set<TextAnnotation> chain : npAnnotChains) {
					/*
					 * in each chain, remove the npAnnot and replace with the apposAnnot
					 */
					boolean chainUpdated = chain.remove(npAnnot);
					if (chainUpdated) {
						chain.add(apposAnnot);
						logger.info("Updated chain:");
						for (TextAnnotation ta : chain) {
							logger.info("_  " + toLogString(ta));
						}
					}
				}
				/*
				 * all references to the np annot have been removed from chains, so remove it from
				 * the taToChainMap
				 */
				taToChainMap.remove(npAnnot);
			}
		}

		/* merge chains that share a member */
		Set<Set<TextAnnotation>> updatedChains = new HashSet<Set<TextAnnotation>>(chains);
		for (Entry<TextAnnotation, Set<Set<TextAnnotation>>> entry : taToChainMap.entrySet()) {
			if (entry.getValue().size() > 1) {
				Set<TextAnnotation> mergedChain = new HashSet<TextAnnotation>();
				logger.info("#### Merging chains based on shared coreferring string "
						+ ((matchDueTo == MatchDueTo.SPAN_TO_TOKEN_BOUNDARY_MATCH)
								? "caused by matching spans to token boundaries" : ""));
				logger.info("Shared coreferring string:" + toLogString(entry.getKey()));
				Set<TextAnnotation> overlap = new HashSet<TextAnnotation>();
				for (Set<TextAnnotation> chain : entry.getValue()) {
					if (overlap.isEmpty()) {
						overlap = new HashSet<TextAnnotation>(chain);
					} else {
						overlap.retainAll(chain);
					}
				}
				logger.info("Overlap in original chains: " + overlap.size() + " annotations.\n");
				int chainCount = 0;
				for (Set<TextAnnotation> chain : entry.getValue()) {
					List<TextAnnotation> taList = new ArrayList<TextAnnotation>(chain);
					Collections.sort(taList, TextAnnotation.BY_SPAN());
					for (TextAnnotation ta : taList) {
						logger.info("> chain " + chainCount + " -- " + toLogString(ta));
					}
					logger.info("");
					mergedChain.addAll(chain);
					chainCount++;
				}
				/*
				 * remove chains that have been combined and add the newly merged chain
				 */
				for (Set<TextAnnotation> chain : entry.getValue()) {
					updatedChains.remove(chain);
				}
				updatedChains.add(mergedChain);
				return updatedChains;
			}
		}

		return updatedChains;
	}

	/**
	 * @param value
	 * @return appos annot from a set assumed to be of size 2, also containing an np annot
	 */
	private static TextAnnotation getApposAnnot(Set<TextAnnotation> set) {
		for (TextAnnotation ta : set) {
			if (ta.getClassMention().getMentionName().equalsIgnoreCase(CoNLLCoref2012DocumentReader.APPOS_RELATION)) {
				return ta;
			}
		}
		throw new IllegalArgumentException("set should have contained an APPOS relation annotation");
	}

	/**
	 * @param value
	 * @return np annot from a set assumed to be of size 2, also containing an appos annot
	 */
	private static TextAnnotation getNpAnnot(Set<TextAnnotation> set) {
		for (TextAnnotation ta : set) {
			if (ta.getClassMention().getMentionName().equalsIgnoreCase(CoNLLCoref2012DocumentReader.NOUN_PHRASE)) {
				return ta;
			}
		}
		throw new IllegalArgumentException("set should have contained an Noun Phrase annotation");
	}

	private String listToString(List<String> statusSet) {
		return CollectionsUtil.createDelimitedString(statusSet, ";");
	}

	private List<String> stringToList(String miscellaneous) {
		return new ArrayList<String>(Arrays.asList(miscellaneous.split(";")));
	}

	// private String setToString(Set<String> statusSet) {
	// return CollectionsUtil.createDelimitedString(statusSet, ";");
	// }
	//
	// private Set<String> stringToSet(String miscellaneous) {
	// return new HashSet<String>(Arrays.asList(miscellaneous.split(";")));
	// }

	/**
	 * populate two maps to allow for fast lookups by token start and end
	 * 
	 * @param records
	 * @param tokenStartIndexToRecordMap
	 * @param tokenEndIndexToRecordMap
	 */
	static void populateTokenIndexToRecordMaps(List<CoNLLUFileRecord> records,
			Map<Integer, CoNLLUFileRecord> tokenStartIndexToRecordMap,
			Map<Integer, CoNLLUFileRecord> tokenEndIndexToRecordMap) {

		for (CoNLLUFileRecord record : records) {
			if (record.getWordIndex() < 0) {
				continue;
			}

			Pattern p = Pattern.compile("SPAN_([0-9]+)\\|([0-9]+)");
			Matcher m = p.matcher(record.getMiscellaneous());
			if (m.find()) {
				int spanStart = Integer.parseInt(m.group(1));
				int spanEnd = Integer.parseInt(m.group(2));
				tokenStartIndexToRecordMap.put(spanStart, record);
				tokenEndIndexToRecordMap.put(spanEnd, record);

				if (record.getWordIndex() > -1 && !record.getMiscellaneous().matches("SPAN_\\d+\\|\\d+")) {
					throw new IllegalStateException(
							"misc doesn't match single span pattern: " + record.getMiscellaneous() + ";;;");
				}
			} else {
				throw new IllegalStateException(
						"No span indicator (e.g. 'SPAN_0|5') found in the record miscellaneous field.");
			}
		}
	}

	/**
	 * @param annotations
	 *            npAnnotations - optional parameter. if present then any created np annots will get
	 *            added to the set
	 * @return members of identity chains grouped in separate lists (one list per chain)
	 */
	public static Map<TextAnnotation, Set<TextAnnotation>> getCoreferenceChains(TextAnnotationFactory factory,
			String documentText, Collection<TextAnnotation> annotations, Set<TextAnnotation> npAnnotations) {
		Map<TextAnnotation, Set<TextAnnotation>> chainToMemberAnnotsMap = new HashMap<TextAnnotation, Set<TextAnnotation>>();
		List<TextAnnotation> sortedAnnotations = new ArrayList<TextAnnotation>(annotations);
		Collections.sort(sortedAnnotations, TextAnnotation.BY_SPAN());
		Map<List<Span>, TextAnnotation> spanToNounPhraseAnnotMap = new HashMap<List<Span>, TextAnnotation>();

		for (TextAnnotation annot : sortedAnnotations) {
			String type = annot.getClassMention().getMentionName();
			if (type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.NOUN_PHRASE)) {
				spanToNounPhraseAnnotMap.put(annot.getSpans(), annot);
			} else if (type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)) {
				ComplexSlotMention csm = annot.getClassMention().getComplexSlotMentionByName(
						CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
				Set<TextAnnotation> chain = new HashSet<TextAnnotation>();
				boolean hasChainHead = false;

				for (ClassMention cm : csm.getClassMentions()) {
					TextAnnotation ta = cm.getTextAnnotation();
					chain.add(ta);
					if (ta.getSpans().equals(annot.getSpans())) {
						// make sure the chain includes the annotation covered
						// by the initial identity chain annotation
						hasChainHead = true;
					}
				}

				if (!hasChainHead) {
					chain.add(findOrCreateCoveringNpAnnot(factory, documentText, spanToNounPhraseAnnotMap, annot, csm,
							IncludeCorefType.IDENT, npAnnotations));
				}
				/*
				 * a chain must have > 1 members so don't add it to the chainSet unless it has at
				 * least 2 members
				 */
				if (chain.size() > 1) {
					chainToMemberAnnotsMap.put(annot, chain);
				} else {
					logger.info("#### Excluding IDENT chain of length 1\n" + toLogString(annot));
				}
			}
		}

		return chainToMemberAnnotsMap;
	}

	/**
	 * @param factory
	 * @param documentText
	 * @param spanToNounPhraseAnnotMap
	 * @param annot
	 * @param headCsm
	 * @param includeCorefType
	 * @param npAnnotations
	 *            - optional. if present then any newly created np annots are added to the set.
	 * @return
	 */
	public static TextAnnotation findOrCreateCoveringNpAnnot(TextAnnotationFactory factory, String documentText,
			Map<List<Span>, TextAnnotation> spanToNounPhraseAnnotMap, TextAnnotation annot, ComplexSlotMention headCsm,
			IncludeCorefType includeCorefType, Set<TextAnnotation> npAnnotations) {
		/*
		 * then look to see if the head NP exists, create one if it doesn't
		 */
		TextAnnotation npAnnot = null;
		if (spanToNounPhraseAnnotMap.get(annot.getSpans()) != null) {
			npAnnot = spanToNounPhraseAnnotMap.get(annot.getSpans());
		} else {
			npAnnot = createNpAnnotation(factory, documentText, annot.getSpans(), includeCorefType, npAnnotations);
		}
		headCsm.addClassMention(npAnnot.getClassMention());
		return npAnnot;
	}

	public static TextAnnotation createNpAnnotation(TextAnnotationFactory factory, String documentText,
			List<Span> spans, IncludeCorefType includeCorefType, Set<TextAnnotation> npAnnotations) {
		TextAnnotation npAnnot;
		npAnnot = factory.createAnnotation(spans, documentText,
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		if (npAnnotations != null) {
			npAnnotations.add(npAnnot);
		}
		logger.info("#### Creating missing Noun Phrase annotation inferred from " + includeCorefType.name()
				+ " annotation\n" + toLogString(npAnnot));
		return npAnnot;
	}

	/**
	 * Assumes any annotation not named "Noun Phrase" is a sentence or token annotation
	 * 
	 * @param annotations
	 * @return
	 */
	private List<TextAnnotation> filterSentenceAndTokenAnnots(List<TextAnnotation> annotations) {
		List<TextAnnotation> sentenceAndTokenAnnots = new ArrayList<TextAnnotation>();
		for (TextAnnotation annot : annotations) {
			String type = annot.getClassMention().getMentionName();
			if (!(type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.NOUN_PHRASE)
					|| type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)
					|| type.equalsIgnoreCase("APPOS relation"))) {
				sentenceAndTokenAnnots.add(annot);
			}
		}
		return sentenceAndTokenAnnots;
	}

	/**
	 * @param record
	 * @param documentId
	 * @return a String in CoNLL coref 2011/12 format using data from the input record
	 * 
	 *         <pre>
	 * Column	Type	Description
	1	Document ID	This is a variation on the document filename
	2	Part number	Some files are divided into multiple parts numbered as 000, 001, 002, ... etc.
	3	Word number	
	4	Word itself	This is the token as segmented/tokenized in the Treebank. Initially the *_skel file contain the placeholder [WORD] which gets replaced by the actual token from the Treebank which is part of the OntoNotes release.
	5	Part-of-Speech	
	6	Parse bit	This is the bracketed structure broken before the first open parenthesis in the parse, and the word/part-of-speech leaf replaced with a *. The full parse can be created by substituting the asterix with the "([pos] [word])" string (or leaf) and concatenating the items in the rows of that column.
	7	Predicate lemma	The predicate lemma is mentioned for the rows for which we have semantic role information. All other rows are marked with a "-"
	8	Predicate Frameset ID	This is the PropBank frameset ID of the predicate in Column 7.
	9	Word sense	This is the word sense of the word in Column 3.
	10	Speaker/Author	This is the speaker or author name where available. Mostly in Broadcast Conversation and Web Log data.
	11	Named Entities	These columns identifies the spans representing various named entities.
	12:N	Predicate Arguments	There is one column each of predicate argument structure information for the predicate mentioned in Column 7.
	N	Coreference	Coreference chain information encoded in a parenthesis structure.
	 *         </pre>
	 */
	private String toCoNLLCoref2012FormatString(CoNLLUFileRecord record, String documentId) {

		StringBuffer sb = new StringBuffer();
		sb.append(documentId);
		sb.append("\t0");
		sb.append("\t" + record.getWordIndex());
		sb.append("\t" + record.getForm());
		sb.append("\t" + record.getUniversalPartOfSpeechTag());
		sb.append("\t-");
		sb.append("\t-");
		sb.append("\t-");
		sb.append("\t-");
		sb.append("\t-");
		sb.append("\t-");
		sb.append("\t-");

		String corefInfo = formCorefInfoString(record.getMiscellaneous());
		sb.append("\t" + corefInfo);
		return sb.toString();
	}

	/**
	 * <pre>
	 * for a single token chain member: (0)
	 * for the start of a multi-token chain member and a single token chain member overlapping: (8|(0)
	 * for the end of a multi-token chain member and a single token chain member overlapping: (0)|8)
	 * for two chain members starting on the same token: (53|(67
	 * for two chain members ending on the same token: 67)|45)
	 * for a token that is a member of a discontinuous chain member: (0a) where 'a' is the member identifier ('a' can be any sequence of characters (non-digits))
	 * </pre>
	 * 
	 * @param misc
	 * @return a parenthetical representation of the start and ends of identify chain members
	 */
	static String formCorefInfoString(String misc) {
		if (misc == null || misc.isEmpty()) {
			return "-";
		}

		Map<String, Collection<String>> chainIdToStatusMap = new HashMap<String, Collection<String>>();
		String[] toks = misc.split(";");
		for (String tok : toks) {
			// ignore the span indicator
			if (!tok.startsWith("SPAN_")) {
				String[] status_id = tok.split("_");
				// chain id is a String b/c it could include a mentionId
				// (non-digit), e.g. START_1a
				String chainId = status_id[1];
				String status = status_id[0];
				CollectionsUtil.addToOne2ManyMap(chainId, status, chainIdToStatusMap);
			}
		}

		List<String> startList = new ArrayList<String>();
		List<String> endList = new ArrayList<String>();
		List<String> bothList = new ArrayList<String>();

		/* sort for reproducibility with unit tests - and in production */
		Map<String, Collection<String>> sortedMap = CollectionsUtil.sortMapByKeys(chainIdToStatusMap,
				SortOrder.DESCENDING);
		for (Entry<String, Collection<String>> entry : sortedMap.entrySet()) {
			Collection<String> statusList = entry.getValue();

			// can only have one start/end pair
			if (statusList.contains(START_STATUS_INDICATOR) && statusList.contains(END_STATUS_INDICATOR)) {
				bothList.add(entry.getKey());
				statusList.remove(START_STATUS_INDICATOR);
				statusList.remove(END_STATUS_INDICATOR);
			}

			// but can have potentially multiple starts for the same chain
			while (statusList.contains(START_STATUS_INDICATOR)) {
				startList.add(entry.getKey());
				statusList.remove(START_STATUS_INDICATOR);
			}

			// and can also have potentially multiple ends for the same chain
			while (statusList.contains(END_STATUS_INDICATOR)) {
				endList.add(entry.getKey());
				statusList.remove(END_STATUS_INDICATOR);
			}

			/* at this point there should be no more status keys to process */
			if (!statusList.isEmpty()) {
				throw new IllegalStateException("Status list should be empty at this point: " + statusList);
			}
		}

		if (bothList.size() > 1) {
			/*
			 * this is allowable if only one of the ids in bothList is a non-discontinuous mention
			 * id, i.e. it doesn't have a character
			 */
			int nonDiscontinousCount = 0;
			for (String id : bothList) {
				if (id.matches("\\d+")) {
					nonDiscontinousCount++;
				}
			}
			if (nonDiscontinousCount > 1) {
				throw new IllegalStateException(
						"Should not have two complete IDENTITY chain members for a single token. If this occurs, "
								+ "it should be caught earlier during the chain merging step where the two chains should be merged into one.");
			}
		}

		String chainRepStr = "";
		for (String both : bothList) {
			chainRepStr = "(" + both + ")" + (chainRepStr.isEmpty() ? "" : "|") + chainRepStr;
		}

		for (String start : startList) {
			chainRepStr = "(" + start + (chainRepStr.isEmpty() ? "" : "|") + chainRepStr;
		}

		for (String end : endList) {
			chainRepStr = chainRepStr + (chainRepStr.isEmpty() ? "" : "|") + end + ")";
		}

		if (chainRepStr.isEmpty()) {
			chainRepStr = "-";
		}

		return chainRepStr;
	}

	public static String toLogString(Collection<TextAnnotation> taList) {
		StringBuffer sb = new StringBuffer();
		for (TextAnnotation ta : taList) {
			sb.append(((sb.toString().isEmpty()) ? "" : "\n") + toLogString("", ta));
		}
		return sb.toString();
	}

	public static String toLogString(TextAnnotation ta) {
		return toLogString("", ta);
	}

	public static String toLogString(String linePrefix, TextAnnotation ta) {
		StringBuffer sb = new StringBuffer();
		sb.append(linePrefix + ta.getDocumentID() + " | " + Span.toString(ta.getSpans()) + " | "
				+ ta.getClassMention().getMentionName() + " | '" + ta.getCoveredText() + "'");
		Collection<ComplexSlotMention> csms = ta.getClassMention().getComplexSlotMentions();
		Map<String, Collection<String>> csmNameToAnnotStrMap = new HashMap<String, Collection<String>>();
		for (ComplexSlotMention csm : csms) {
			String csmName = csm.getMentionName();
			List<TextAnnotation> slotFillerAnnots = new ArrayList<TextAnnotation>();
			for (ClassMention cm : csm.getClassMentions()) {
				slotFillerAnnots.add(cm.getTextAnnotation());
			}
			Collections.sort(slotFillerAnnots, TextAnnotation.BY_SPAN());
			for (TextAnnotation slotFillerAnnot : slotFillerAnnots) {
				CollectionsUtil.addToOne2ManyMap(csmName, toLogString(slotFillerAnnot), csmNameToAnnotStrMap);
			}
		}

		Map<String, Collection<String>> sortedMap = CollectionsUtil.sortMapByKeys(csmNameToAnnotStrMap,
				SortOrder.ASCENDING);

		for (Entry<String, Collection<String>> entry : sortedMap.entrySet()) {
			for (String val : entry.getValue()) {
				sb.append("\n" + linePrefix + "_          " + entry.getKey() + " -- " + val);
			}
		}
		return sb.toString();

	}

}
