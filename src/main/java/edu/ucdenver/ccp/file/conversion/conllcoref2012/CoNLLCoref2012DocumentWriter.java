package edu.ucdenver.ccp.file.conversion.conllcoref2012;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.collections.CollectionsUtil.SortOrder;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileWriterUtil;
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

/*
 * #%L
 * Colorado Computational Pharmacology's file conversion
 * 						project
 * %%
 * Copyright (C) 2012 - 2019 Regents of the University of Colorado
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

/**
 * Writes IDENTITY chain and APPOS relations to the CoNLLCoref 2011/2012 file
 * format. -- if an APPOS head is part of an identity chain, then add its
 * attribute to that chain. -- If an APPOS relation exists on its own (not
 * connected to an IDENTITY chain) then treat it as an IDENTITY chain of length
 * 2
 */
public class CoNLLCoref2012DocumentWriter extends DocumentWriter {

	public enum IncludeCorefType {
		IDENT, APPOS
	}

	private IncludeCorefType includeCorefType;

	public CoNLLCoref2012DocumentWriter(IncludeCorefType includeCorefType) {
		super();
		this.includeCorefType = includeCorefType;
	}

	static final String START_STATUS_INDICATOR = "BEGIN";
	static final String END_STATUS_INDICATOR = "END";

	private static final Logger logger = Logger.getLogger(CoNLLCoref2012DocumentWriter.class);

	@Override
	public void serialize(TextDocument td, OutputStream outputStream, CharacterEncoding encoding) throws IOException {

		/*
		 * check for any leading or trailing white space in annotations, adjust
		 * spans as necessary to remove whitespace
		 */
		List<TextAnnotation> annotations = td.getAnnotations();
		trimAnnotations(annotations, td.getText());

		/*
		 * TD assumed to contain sentence & token/pos annotations +
		 * single/multi-word base NP annotations linked into IDENT chains and
		 * APPOS relations
		 */

		List<TextAnnotation> sentenceAndTokenAnnots = filterSentenceAndTokenAnnots(annotations);
		if (sentenceAndTokenAnnots.size() == 0) {
			throw new IllegalArgumentException("No sentence/token annotations were detected in the input document. "
					+ "Unable to write the CoNLLCoref 2011/12 format without sentence and token annotations.");
		}

		/*
		 * group annotations by identity chain -- isolated appos relation =
		 * chain of length 2
		 */
		Set<Set<TextAnnotation>> chains = getCoreferenceChains(
				TextAnnotationFactory.createFactoryWithDefaults(td.getSourceid()), annotations, includeCorefType);

		// System.out.println("++++++ CHAIN COUNT (" + td.getSourceid() + "): "
		// + chains.size());

		/*
		 * if there is an annotation that is a member of >1 chains, then those
		 * chains should be combined - this step fixes some annotation errors.
		 * Ideally this step would not change the annotation at all. This step
		 * is only relevan for IDENTITY chains.
		 */
		if (includeCorefType == IncludeCorefType.IDENT) {
			chains = mergeChainsIfSharedAnnotation(chains);
			// System.out.println("++++++ CONSOLIDATED CHAIN COUNT (" +
			// td.getSourceid() + "): " + chains.size());
		}

		/*
		 * Sort the chains to provide reproducibility in the chain numbering.
		 * This is beneficial for unit testing, and could be potentially
		 * beneficial in production as well.
		 */
		List<Set<TextAnnotation>> sortedChains = sortChains(chains);

		// System.out.println("++++++ SORTED CHAIN COUNT (" + td.getSourceid() +
		// "): " + sortedChains.size());

		/*
		 * The structure of the CoNLL Coref 2011/12 file format is similar to
		 * that of CoNLL-U. It lists tokens sequentially with line breaks at
		 * sentence boundaries. We can use logic in the CoNLL-U Document Writer
		 * to get the token ordering.
		 */
		List<CoNLLUFileRecord> records = CoNLLUDocumentWriter.generateRecords(sentenceAndTokenAnnots);

		/*
		 * the miscellaneous field of each record should have one and only one
		 * span indication
		 */
		for (CoNLLUFileRecord record : records) {
			if (record.getWordIndex() > -1 && !record.getMiscellaneous().matches("SPAN_\\d+\\|\\d+")) {
				throw new IllegalStateException(
						"misc doesn't match single span pattern: " + record.getMiscellaneous() + ";;;");
			}
		}

		Map<Integer, CoNLLUFileRecord> tokenStartIndexToRecordMap = new HashMap<Integer, CoNLLUFileRecord>();
		Map<Integer, CoNLLUFileRecord> tokenEndIndexToRecordMap = new HashMap<Integer, CoNLLUFileRecord>();

		populateTokenIndexToRecordMaps(records, tokenStartIndexToRecordMap, tokenEndIndexToRecordMap);

		Map<Integer, CoNLLUFileRecord> sortedTokenStartIndexToRecordMap = CollectionsUtil
				.sortMapByKeys(tokenStartIndexToRecordMap, SortOrder.ASCENDING);
		Map<Integer, CoNLLUFileRecord> sortedTokenEndIndexToRecordMap = CollectionsUtil
				.sortMapByKeys(tokenEndIndexToRecordMap, SortOrder.ASCENDING);

		/*
		 * Add an indicator to the miscellaneous column of the CoNLLURecord to
		 * indicate if a chain member starts or ends at a given token
		 */
		int chainCount = 1;
		for (Set<TextAnnotation> chain : sortedChains) {
			for (TextAnnotation annot : chain) {
				int spanStart = annot.getAnnotationSpanStart();
				int spanEnd = annot.getAnnotationSpanEnd();

				// System.out.println("Process chains. Add to misc column.
				// Chain" + chainCount + ": "
				// + annot.getAggregateSpan().toString() + " -- " +
				// annot.getCoveredText());

				CoNLLUFileRecord startRecord = sortedTokenStartIndexToRecordMap.get(spanStart);
				// System.out.println("START RECORD: " +
				// startRecord.toCoNLLUFormatString());
				// if (startRecord != null && startRecord.getWordIndex() > -1 &&
				// !startRecord.getMiscellaneous().matches("SPAN_\\d+\\|\\d+"))
				// {
				// throw new IllegalStateException(
				// "misc doesn't match single span pattern: " +
				// startRecord.getMiscellaneous() + ";;;");
				// }

				if (startRecord == null) {
					/*
					 * coreference noun phrases may not exactly align with the
					 * tokenization of the document. In cases like this, we
					 * consider the token overlapping the start index to be the
					 * start of the coreference.
					 */
					logger.info(
							"++++++ CHAIN MEMBER START SPAN MISMATCH WITH TOKENENIZATION - ALIGNING TO OVERLAPPING TOKEN");
					startRecord = findOverlappingStartToken(sortedTokenStartIndexToRecordMap, spanStart);
					logger.info("------ CHAIN MEMBER START -- " + annot.getAggregateSpan().toString() + " -- "
							+ annot.getCoveredText() + " MATCH WITH TOKEN: " + startRecord.getForm());

				}
				List<String> startStatus = stringToList(startRecord.getMiscellaneous());
				startStatus.add(START_STATUS_INDICATOR + "_" + chainCount);
				startRecord.setMiscellaneous(listToString(startStatus));
				CoNLLUFileRecord endRecord = sortedTokenEndIndexToRecordMap.get(spanEnd);
				if (endRecord == null) {
					logger.info(
							"------ CHAIN MEMBER END SPAN MISMATCH WITH TOKENENIZATION - ALIGNING TO OVERLAPPING TOKEN");
					endRecord = findOverlappingEndToken(sortedTokenEndIndexToRecordMap, spanEnd);
					logger.info("------ CHAIN MEMBER END -- " + annot.getAggregateSpan().toString() + " -- "
							+ annot.getCoveredText() + " MATCH WITH TOKEN: " + endRecord.getForm());

				}
				List<String> endStatus = stringToList(endRecord.getMiscellaneous());
				endStatus.add(END_STATUS_INDICATOR + "_" + chainCount);

				// the misc column should already contain an indicator of
				// the
				// token span, e.g. 'SPAN_0|5'
				endRecord.setMiscellaneous(listToString(endStatus));

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
					while (annot.getCoveredText().startsWith(" ")) {
						incrementSpanStart(annot, docText);
					}
					while (annot.getCoveredText().endsWith(" ")) {
						decrementSpanEnd(annot, docText);
					}
				}
			}
		}

		/* remove whitespace annotations */
		for (TextAnnotation blankAnnot : blankAnnots) {
			annotations.remove(blankAnnot);
		}
	}

	/**
	 * Increase the span start by one, update the annotation covered text
	 * 
	 * @param annot
	 * @param docText
	 */
	private void incrementSpanStart(TextAnnotation annot, String docText) {
		List<Span> spans = annot.getSpans();
		Collections.sort(spans, Span.ASCENDING());
		Span firstSpan = spans.get(0);
		firstSpan.setSpanStart(firstSpan.getSpanStart() + 1);
		annot.setCoveredText(SpanUtils.getCoveredText(spans, docText));
	}

	/**
	 * Decrease the span end by one, update the annotation covered text
	 * 
	 * @param annot
	 * @param docText
	 */
	private void decrementSpanEnd(TextAnnotation annot, String docText) {
		List<Span> spans = annot.getSpans();
		Collections.sort(spans, Span.ASCENDING());
		Span lastSpan = spans.get(spans.size() - 1);
		lastSpan.setSpanEnd(lastSpan.getSpanEnd() - 1);
		annot.setCoveredText(SpanUtils.getCoveredText(spans, docText));
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
				System.out.println("----- RECORD: " + record.getForm() + " -- " + record.getMiscellaneous());
				return record;
			}
			record = entry.getValue();
			if (Math.abs(spanOffset - entry.getKey()) < 30) {
				System.out.println("----- RECORD: " + record.getForm() + " -- " + record.getMiscellaneous());
			}
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
				System.out.println("----- RECORD: " + record.getForm() + " -- " + record.getMiscellaneous());
				return record;
			}
			if (Math.abs(spanOffset - entry.getKey()) < 30) {
				System.out.println("----- RECORD: " + record.getForm() + " -- " + record.getMiscellaneous());
			}
		}

		logger.info("Returning final record when searching for overlapping token.");
		return record;

	}

	/**
	 * @param consolidatedChains
	 * @return sorted list of chains (sorted by appearance of first annotation
	 *         in the chain). Sorting adds reproducibility -- necessary for unit
	 *         testing so that the chains have reproducible identifiers.
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
	 * This method fixes presumable annotation errors by joining chains that
	 * share an annotation, e.g. if there are two chains [A B C] and [B D] then
	 * they should be joined to become [A B C D]
	 * 
	 * @param chains
	 * @return
	 */
	static Set<Set<TextAnnotation>> mergeChainsIfSharedAnnotation(Set<Set<TextAnnotation>> chains) {
		Set<Set<TextAnnotation>> updatedChains = new HashSet<Set<TextAnnotation>>(chains);
		int previousChainCount = -1;
		/* merge chains until there are no more chains to merge */
		do {
			previousChainCount = updatedChains.size();
			updatedChains = mergeChains(updatedChains);
		} while (updatedChains.size() != previousChainCount);

		return updatedChains;
	}

	private static Set<Set<TextAnnotation>> mergeChains(Set<Set<TextAnnotation>> chains) {
		Map<TextAnnotation, Set<Set<TextAnnotation>>> taToChainMap = new HashMap<TextAnnotation, Set<Set<TextAnnotation>>>();

		/* map each text annotation to its chain(s) */
		for (Set<TextAnnotation> chain : chains) {
			for (TextAnnotation ta : chain) {
				CollectionsUtil.addToOne2ManyUniqueMap(ta, chain, taToChainMap);
			}
		}

		/* merge chains that share a member */
		Set<Set<TextAnnotation>> updatedChains = new HashSet<Set<TextAnnotation>>(chains);
		for (Entry<TextAnnotation, Set<Set<TextAnnotation>>> entry : taToChainMap.entrySet()) {
			if (entry.getValue().size() > 1) {
				Set<TextAnnotation> mergedChain = new HashSet<TextAnnotation>();
				logger.info(">>>>>>>>>>>>>>>>>> Discovered " + entry.getValue().size() + " chains to merge.");
				int chainId = 1;
				for (Set<TextAnnotation> chain : entry.getValue()) {
					mergedChain.addAll(chain);
					for (TextAnnotation ta : chain) {
						System.out.println("Chain-to-merge " + chainId + ": " + ta.getAggregateSpan().toString()
								+ " -- " + ta.getCoveredText());
					}
					chainId++;
				}
				/*
				 * remove chains that have been combined and add the newly
				 * merged chain
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
	 * @return members of identity chains grouped in separate lists (one list
	 *         per chain)
	 */
	private Set<Set<TextAnnotation>> getCoreferenceChains(TextAnnotationFactory factory,
			List<TextAnnotation> annotations, IncludeCorefType includeCorefType) {
		Set<Set<TextAnnotation>> chainSet = new HashSet<Set<TextAnnotation>>();
		List<TextAnnotation> sortedAnnotations = new ArrayList<TextAnnotation>(annotations);
		Collections.sort(sortedAnnotations, TextAnnotation.BY_SPAN());
		Map<Span, TextAnnotation> spanToNounPhraseAnnotMap = new HashMap<Span, TextAnnotation>();

		for (TextAnnotation annot : sortedAnnotations) {
			String type = annot.getClassMention().getMentionName();
			if (type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.NOUN_PHRASE)) {
				spanToNounPhraseAnnotMap.put(annot.getAggregateSpan(), annot);
			} else if (includeCorefType == IncludeCorefType.IDENT
					&& type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)) {
				ComplexSlotMention csm = annot.getClassMention().getComplexSlotMentionByName(
						CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
				Set<TextAnnotation> chain = new HashSet<TextAnnotation>();
				boolean hasChainHead = false;

				if (csm.getClassMentions().isEmpty()) {
					/*
					 * mark this as empty - should be removed - it is a chain of
					 * length 1
					 */
					logger.info("________ OBSERVED IDENT CHAIN OF LENGTH 1: " + annot.toString());
				} else {

					for (ClassMention cm : csm.getClassMentions()) {
						TextAnnotation ta = cm.getTextAnnotation();
						chain.add(ta);
						if (ta.getAggregateSpan().equals(annot.getAggregateSpan())) {
							// make sure the chain includes the annotation
							// covered
							// by the initial identity chain annotation
							hasChainHead = true;
						}
					}

					if (!hasChainHead) {
						System.out.println("========= IDENT CHAIN missing HEAD: " + annot.toString());

						/* look to see if the */
						/*
						 * then look to see if the head NP exists, create one if
						 * it doesn't
						 */
						TextAnnotation npAnnot = null;
						if (spanToNounPhraseAnnotMap.get(annot.getAggregateSpan()) != null) {
							npAnnot = spanToNounPhraseAnnotMap.get(annot.getAggregateSpan());
						} else {
							npAnnot = factory.createAnnotation(annot.getAnnotationSpanStart(),
									annot.getAnnotationSpanEnd(), annot.getCoveredText(),
									new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
						}
						chain.add(npAnnot);
						csm.addClassMention(npAnnot.getClassMention());
					}
					chainSet.add(chain);
				}
			} else if (includeCorefType == IncludeCorefType.APPOS
					&& type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.APPOS_RELATION)) {

				ComplexSlotMention headCsm = annot.getClassMention()
						.getComplexSlotMentionByName(CoNLLCoref2012DocumentReader.APPOS_HEAD_SLOT);

				ComplexSlotMention attributeCsm = annot.getClassMention()
						.getComplexSlotMentionByName(CoNLLCoref2012DocumentReader.APPOS_ATTRIBUTES_SLOT);

				if (!headCsm.getClassMentions().isEmpty() && !attributeCsm.getClassMentions().isEmpty()) {
					Set<TextAnnotation> chain = new HashSet<TextAnnotation>();

					boolean hasApposHead = false;
					for (ClassMention cm : headCsm.getClassMentions()) {
						TextAnnotation ta = cm.getTextAnnotation();
						chain.add(ta);
						if (ta.getAggregateSpan().equals(annot.getAggregateSpan())) {
							// make sure the chain includes the annotation
							// covered
							// by the initial identity chain annotation
							hasApposHead = true;
						}
					}
					for (ClassMention cm : attributeCsm.getClassMentions()) {
						TextAnnotation ta = cm.getTextAnnotation();
						chain.add(ta);
						if (ta.getAggregateSpan().equals(annot.getAggregateSpan())) {
							// make sure the chain includes the annotation
							// covered
							// by the initial identity chain annotation
							hasApposHead = true;
						}
					}

					if (!hasApposHead) {
						logger.info("##### EVEN WITH HEAD SLOT, NO HEAD ANNOT: " + annot.toString());
					}
					chainSet.add(chain);

				} else if (headCsm.getClassMentions().isEmpty()) {
					logger.info("##### APPOS MISSING HEAD: " + annot.toString());

				} else if (attributeCsm.getClassMentions().isEmpty()) {
					logger.info("##### APPOS MISSING ATTRIBUTES: " + annot.toString());

				}

			}
		}
		return chainSet;
	}

	/**
	 * Assumes any annotation not named "Noun Phrase" is a sentence or token
	 * annotation
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
	 * @return a String in CoNLL coref 2011/12 format using data from the input
	 *         record
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

		String corefInfo = formCorefInfoString(record.getMiscellaneous(), includeCorefType);
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
	 * </pre>
	 * 
	 * @param misc
	 * @return a parenthetical representation of the start and ends of identify
	 *         chain members
	 */
	static String formCorefInfoString(String misc, IncludeCorefType includeCorefType) {
		if (misc == null || misc.isEmpty()) {
			return "-";
		}

		Map<Integer, Collection<String>> chainIdToStatusMap = new HashMap<Integer, Collection<String>>();
		String[] toks = misc.split(";");
		for (String tok : toks) {
			// ignore the span indicator
			if (!tok.startsWith("SPAN_")) {
				String[] status_id = tok.split("_");
				Integer chainId = Integer.parseInt(status_id[1]);
				String status = status_id[0];
				CollectionsUtil.addToOne2ManyMap(chainId, status, chainIdToStatusMap);
			}
		}

		List<Integer> startList = new ArrayList<Integer>();
		List<Integer> endList = new ArrayList<Integer>();
		List<Integer> bothList = new ArrayList<Integer>();

		/* sort for reproducibility with unit tests - and in production */
		Map<Integer, Collection<String>> sortedMap = CollectionsUtil.sortMapByKeys(chainIdToStatusMap,
				SortOrder.DESCENDING);
		for (Entry<Integer, Collection<String>> entry : sortedMap.entrySet()) {
			Collection<String> statusList = entry.getValue();

			System.out.println("MISC: " + misc + " -- Chain ID: " + entry.getKey() + " -- " + statusList.toString());
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

		if (includeCorefType == IncludeCorefType.IDENT && bothList.size() > 1) {
			throw new IllegalStateException(
					"Should not have two complete IDENTITY chain members for a single token. If this occurs, "
							+ "it should be caught earlier durin the chain merging step where the two chains should be merged into one.");
		}

		String chainRepStr = "";
		if (bothList.size() == 1) {
			chainRepStr = "(" + bothList.get(0) + ")";
		}

		for (Integer start : startList) {
			chainRepStr = "(" + start + (chainRepStr.isEmpty() ? "" : "|") + chainRepStr;
		}

		for (Integer end : endList) {
			chainRepStr = chainRepStr + (chainRepStr.isEmpty() ? "" : "|") + end + ")";
		}

		if (chainRepStr.isEmpty()) {
			chainRepStr = "-";
		}

		return chainRepStr;
	}

}
