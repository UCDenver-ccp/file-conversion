package edu.ucdenver.ccp.file.conversion.conllu;

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.collections.CollectionsUtil.SortOrder;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileWriterUtil;
import edu.ucdenver.ccp.file.conversion.DocumentWriter;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;

/**
 * Given {@link TextAnnotation} objects representing dependency structure,
 * serialize them to file using the CoNLL-U format.
 */
public class CoNLLUDocumentWriter extends DocumentWriter {

	@Override
	public void serialize(TextDocument td, OutputStream outputStream, CharacterEncoding encoding) throws IOException {
		serializeAnnotations(td.getAnnotations(), outputStream, encoding);
	}

	private static boolean inQuotes = false;

	public static void serializeAnnotations(List<TextAnnotation> annotations, File outputFile,
			CharacterEncoding encoding) throws IOException {
		serializeAnnotations(annotations, new FileOutputStream(outputFile), encoding);
	}

	public static void serializeAnnotations(List<TextAnnotation> annotations, OutputStream outputStream,
			CharacterEncoding encoding) throws IOException {
		List<CoNLLUFileRecord> records = generateRecords(annotations);
		try (BufferedWriter writer = FileWriterUtil.initBufferedWriter(outputStream, encoding)) {
			for (CoNLLUFileRecord record : records) {
				if (record.getWordIndex() > 0) {
					writer.write(record.toCoNLLUFormatString() + "\n");
				} else {
					writer.write("\n");
				}
			}
		}
	}

	/**
	 * @param annotations
	 * @return CoNNLU records for the token annotations in the specified list. A
	 *         placeholder record is created at each sentence boundary.
	 */
	public static List<CoNLLUFileRecord> generateRecords(List<TextAnnotation> annotations) {
		List<CoNLLUFileRecord> records = new ArrayList<CoNLLUFileRecord>();

		/*
		 * group tokens by sentence -- use linked hashmap to preserve sentence
		 * order
		 */
		Map<Integer, List<TextAnnotation>> sentenceEndOffsetToTokensMap = groupTokensBySentence(annotations);

		for (List<TextAnnotation> taList : sentenceEndOffsetToTokensMap.values()) {
			Collections.sort(taList, TextAnnotation.BY_SPAN());
			Map<TextAnnotation, CoNLLUFileRecord> annotToRecordMap = new HashMap<TextAnnotation, CoNLLUFileRecord>();
			int tokenIndex = 1;

			/*
			 * if any relations are detected they are assumed to be dependency
			 * relations
			 */
			boolean relationsDetected = false;
			/* create a record for each token */
			for (TextAnnotation ta : taList) {
				CoNLLUFileRecord record = generateRecord(ta, tokenIndex++);
				annotToRecordMap.put(ta, record);
				Collection<ComplexSlotMention> csms = ta.getClassMention().getComplexSlotMentions();
				relationsDetected = (csms != null && csms.size() > 0);
			}

			/* assign dependency relations to each record */
			for (TextAnnotation ta : taList) {
				CoNLLUFileRecord record = annotToRecordMap.get(ta);
				Collection<ComplexSlotMention> csms = ta.getClassMention().getComplexSlotMentions();
				if (csms != null && !csms.isEmpty()) {
					for (ComplexSlotMention csm : csms) { // TODO is it possible
															// to
															// have two
															// dependency
															// relations???
						TextAnnotation headAnnot = CollectionsUtil.getSingleElement(csm.getClassMentions())
								.getTextAnnotation();
						record.setDependencyRelation(csm.getMentionName());
						record.setHead(annotToRecordMap.get(headAnnot).getWordIndex());
					}
				} else if (relationsDetected) {
					/* if no relation, then this is the ROOT token */
					record.setDependencyRelation("ROOT");
					record.setHead(0);
				}
			}

			List<CoNLLUFileRecord> recordsToAdd = new ArrayList<CoNLLUFileRecord>(annotToRecordMap.values());
			Collections.sort(recordsToAdd, new Comparator<CoNLLUFileRecord>() {
				@Override
				public int compare(CoNLLUFileRecord record1, CoNLLUFileRecord record2) {
					return Integer.compare(record1.getWordIndex(), record2.getWordIndex());
				}
			});

			/* add record to signify sentence boundary */
			recordsToAdd.add(new CoNLLUFileRecord(-1, null, null, null, null, null, null, null, null, null, -1, -1));
			records.addAll(recordsToAdd);
		}
		return records;
	}

	/**
	 * TODO: this method could be more efficient
	 * 
	 * @param annotations
	 *            should contain token and sentence annotations
	 * @return lists of token annotations grouped by sentence
	 */
	private static Map<Integer, List<TextAnnotation>> groupTokensBySentence(List<TextAnnotation> annotations) {
		Map<Integer, List<TextAnnotation>> map = new HashMap<Integer, List<TextAnnotation>>();

		/* populate map with sentence end offsets */
		for (TextAnnotation ta : annotations) {
			if (ta.getClassMention().getMentionName().equalsIgnoreCase("sentence")) {
				map.put(ta.getAnnotationSpanEnd(), new ArrayList<TextAnnotation>());
			}
		}

		if (map.size() == 0) {
			throw new IllegalArgumentException("Cannot group tokens by sentence without any sentence annotations.");
		}

		Map<Integer, List<TextAnnotation>> sortedMap = CollectionsUtil.sortMapByKeys(map, SortOrder.ASCENDING);

		for (TextAnnotation ta : annotations) {
			if (!ta.getClassMention().getMentionName().equalsIgnoreCase("sentence")) {
				for (Entry<Integer, List<TextAnnotation>> entry : sortedMap.entrySet()) {
					if (ta.getAnnotationSpanStart() < entry.getKey()) {
						entry.getValue().add(ta);
						break;
					}
				}
			}
		}
		return sortedMap;

	}

	private static CoNLLUFileRecord generateRecord(TextAnnotation ta, int wordIndex) {
		String partOfSpeechTag = ta.getClassMention().getMentionName();
		String coveredText = ta.getCoveredText();
		/* quotation marks are handled differently in the CoNLL format */
		if (coveredText.equals("\"")) {
			coveredText = (inQuotes) ? "''" : "``";
			inQuotes = !inQuotes;
		}
		/* remove spaces after commas and colons */
		coveredText = coveredText.replaceAll(",[ ]+", ",");
		coveredText = coveredText.replaceAll(":[ ]+", ":");

		// keep track of the span in the miscellaneous column
		return new CoNLLUFileRecord(wordIndex, coveredText, null, partOfSpeechTag, null, null, null, null, null,
				"SPAN_" + ta.getAnnotationSpanStart() + "|" + ta.getAnnotationSpanEnd(), -1, -1);
	}

}
