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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileUtil;
import edu.ucdenver.ccp.common.io.StreamUtil;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.file.conversion.util.DocumentReaderUtil;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.mention.ClassMention;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;

/**
 * Parses a CoNLLCoref 2011/12 formatted file and throws an exception if any annotations with
 * contiguous or overlapping discontinuous spans are detected.
 *
 */
public class CoNLLCoref2012DocumentValidator extends CoNLLCoref2012DocumentReader {

	@Override
	public TextDocument readDocument(String sourceId, String sourceDb, InputStream inputStream,
			InputStream documentTextStream, CharacterEncoding encoding) throws IOException {
		// document text is used to get the spans for all annotations
		String documentText = StreamUtil.toString(new InputStreamReader(documentTextStream, encoding.getDecoder()));
		TextDocument td = new TextDocument(sourceId, sourceDb, documentText);

		List<TextAnnotation> annotations = getAnnotations(inputStream, sourceId, documentText, encoding);
		List<TextAnnotation> annotsWithInvalidSpans = DocumentReaderUtil.validateSpans(annotations, documentText,
				sourceId);

		if (!annotsWithInvalidSpans.isEmpty()) {
			StringBuffer sb = new StringBuffer();
			for (TextAnnotation ta : annotsWithInvalidSpans) {
				sb.append("Annotation with invalid spans in document: " + ta.getDocumentID() + ": "
						+ Span.toString(ta.getSpans()) + " -- " + ta.getCoveredText() + "\n");
			}
			throw new IllegalStateException(
					"Detected one or more annotations with overlapping or adjacent (contiguous) discontinuous spans. "
							+ "Discontinuous spans cannot be overlapping or contiguous."
							+ "Annotations with discontinuous spans must not have spans that overlap, e.g. [0..10][7..8], and "
							+ "also must not have adjacent spans or spans separated only by whitespace, e.g. [0..10][11-15]. "
							+ "Please adjust as necessary. See annotations with invalid discontinuous spans listed below:\n"
							+ sb.toString());
		}

		String errorMessages = checkForRepeatedChainMembers(annotations);
		if (!errorMessages.isEmpty()) {
			throw new IllegalStateException(
					"Detected redundant annotation(s) appearing in a single Identity Chain, or annotation(s) appearing "
							+ "in multiple identity chains. Please ensure noun phrase annotations are members of only one identity "
							+ "chain, and that there are no duplicate members of any identity chain. Specific error messages listed below:\n"
							+ errorMessages);
		}

		td.addAnnotations(annotations);
		return td;

	}

	private String checkForRepeatedChainMembers(List<TextAnnotation> annotations) {
		StringBuffer errorMessages = new StringBuffer();
		Map<TextAnnotation, Set<Integer>> npAnnots = new HashMap<TextAnnotation, Set<Integer>>();
		int chainId = 0;
		for (TextAnnotation annot : annotations) {
			if (annot.getClassMention().getMentionName().equals("IDENTITY chain")) {
				chainId++;
				ComplexSlotMention csm = annot.getClassMention().getComplexSlotMentionByName("Coreferring strings");
				for (ClassMention cm : csm.getClassMentions()) {
					TextAnnotation ta = cm.getTextAnnotation();
					if (npAnnots.containsKey(ta)) {
						Set<Integer> chainIds = npAnnots.get(ta);
						if (chainIds.contains(chainId)) {
							errorMessages.append("Observed redundant annotation in a single chain -- document: "
									+ ta.getDocumentID() + " spans" + Span.toString(ta.getSpans()) + " covered_text: "
									+ ta.getCoveredText() + "\n");
						} else {
							errorMessages.append("Observed annotation in multiple chains -- document: "
									+ ta.getDocumentID() + " spans" + Span.toString(ta.getSpans()) + " covered_text: "
									+ ta.getCoveredText() + "\n");
						}
						npAnnots.get(ta).add(chainId);
					} else {
						Set<Integer> chainIds = CollectionsUtil.createSet(chainId);
						npAnnots.put(ta, chainIds);
					}
				}
			}
		}
		return errorMessages.toString();
	}

	public static void main(String[] args) {

		File conllCorefDirectory = new File(args[0]);
		File txtDirectory = new File(args[1]);
		boolean validationPassed = true;
		StringBuffer errorMessage = new StringBuffer();
		try {
			for (Iterator<File> fileIter = FileUtil.getFileIterator(conllCorefDirectory, false); fileIter.hasNext();) {
				File conllCorefFile = fileIter.next();
				String sourceId = conllCorefFile.getName().substring(0, conllCorefFile.getName().indexOf("."));
				String sourceDb = "unknown";
				File documentTextFile = new File(txtDirectory, sourceId + ".txt");
				if (documentTextFile.exists()) {
					try {
						new CoNLLCoref2012DocumentValidator().readDocument(sourceId, sourceDb, conllCorefFile,
								documentTextFile, CharacterEncoding.UTF_8);
					} catch (IllegalStateException invalidSpanException) {
						validationPassed = false;
						errorMessage.append("File: " + conllCorefFile.getAbsolutePath() + "\n"
								+ invalidSpanException.getMessage() + "\n");
					}
				} else {
					System.err
							.println("WARNING -- Unable to validate the following as no corresponding txt file exists: "
									+ conllCorefFile.getAbsolutePath());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (!validationPassed) {
			throw new IllegalStateException("Coreference file validation FAILED. " + errorMessage.toString());
		}

	}

}
