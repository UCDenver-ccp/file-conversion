package edu.ucdenver.ccp.file.conversion.conllu;

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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileReaderUtil;
import edu.ucdenver.ccp.common.io.StreamUtil;
import edu.ucdenver.ccp.common.string.RegExPatterns;
import edu.ucdenver.ccp.common.string.StringUtil;
import edu.ucdenver.ccp.file.conversion.DocumentReader;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.file.conversion.util.DocumentReaderUtil;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.SpanUtils;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;

public class CoNLLUDocumentReader extends DocumentReader {

	@Override
	public TextDocument readDocument(String sourceId, String sourceDb, InputStream inputStream,
			InputStream documentTextStream, CharacterEncoding encoding) throws IOException {
		String documentText = StreamUtil.toString(new InputStreamReader(documentTextStream, encoding.getDecoder()));
		TextDocument td = new TextDocument(sourceId, sourceDb, documentText);

		List<TextAnnotation> annotations = getAnnotations(inputStream, documentText, encoding);
		DocumentReaderUtil.validateSpans(annotations, documentText, sourceId);
		td.addAnnotations(annotations);
		return td;
	}

	public static List<TextAnnotation> getAnnotations(File conllUFile, File documentTextFile,
			CharacterEncoding encoding) throws IOException {
		String documentText = StreamUtil
				.toString(new InputStreamReader(new FileInputStream(documentTextFile), encoding.getDecoder()));
		return getAnnotations(new FileInputStream(conllUFile), documentText, encoding);
	}

	public static List<TextAnnotation> getAnnotations(InputStream conllUStream, String documentText,
			CharacterEncoding encoding) throws IOException {

		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		int documentOffset = 0;
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();

		BufferedReader conllUReader = FileReaderUtil.initBufferedReader(conllUStream, encoding);
		String sentenceLines;
		while ((sentenceLines = getLinesForNextSentence(conllUReader)) != null) {

			int sentenceStart = documentOffset;
			Map<Integer, TextAnnotation> tokenNumToAnnotMap = new HashMap<Integer, TextAnnotation>();

			/* create a token annotation for each CoNNL-U line */
			int tokenIndex = 1;
			for (CoNLLURecordReader rr = new CoNLLURecordReader(new ByteArrayInputStream(sentenceLines.getBytes()),
					encoding); rr.hasNext();) {
				CoNLLUFileRecord record = rr.next();
				TextAnnotation token = createTokenAnnotation(record.getForm(), record.getUniversalPartOfSpeechTag(),
						documentText, documentOffset, factory);
				annotations.add(token);
				tokenNumToAnnotMap.put(tokenIndex++, token);
				documentOffset = token.getAggregateSpan().getSpanEnd();
			}
			int sentenceEnd = documentOffset;

			// account for extra whitespace in the document between sentences by checking to
			// make sure the sentence does not start with a space
			String substring = documentText.substring(sentenceStart, sentenceEnd);
			while (StringUtil.startsWithRegex(substring, "\\s")) {
				sentenceStart++;
				substring = documentText.substring(sentenceStart, sentenceEnd);
			}

			TextAnnotation sentence = factory.createAnnotation(sentenceStart, sentenceEnd, "",
					new DefaultClassMention("sentence"));

			annotations.add(sentence);

			/*
			 * now go back and add the dependency relations. Note, this is inefficient b/c
			 * we process each sentence twice.
			 */
			tokenIndex = 1;
			for (CoNLLURecordReader rr = new CoNLLURecordReader(new ByteArrayInputStream(sentenceLines.getBytes()),
					encoding); rr.hasNext();) {
				CoNLLUFileRecord record = rr.next();
				TextAnnotation annot = tokenNumToAnnotMap.get(tokenIndex++);
				addDependencyRelation(annot, record, tokenNumToAnnotMap);
			}
		}
		return annotations;
	}

	private static void addDependencyRelation(TextAnnotation annot, CoNLLUFileRecord record,
			Map<Integer, TextAnnotation> tokenNumToAnnotMap) {
		String dependencyRelation = record.getDependencyRelation();
		Integer headIndex = record.getHead();
		if (headIndex != null) {
			/* headIndex == 0 means this token is the ROOT of the parse tree */
			if (headIndex > 0) {
				TextAnnotation headAnnot = tokenNumToAnnotMap.get(headIndex);

				DocumentReader.createAnnotationRelation(annot, headAnnot, dependencyRelation);
			}
		}
	}

	public static TextAnnotation createTokenAnnotation(String tokenText, String partOfSpeechTag, String documentText,
			int documentOffset, TextAnnotationFactory factory) {
		String coveredText = tokenText;
		/* quotation marks may have been altered in the dependency parse */
		if (coveredText.equals("``")) {
			coveredText = "\"";
		} else if (coveredText.equals("''")) {
			coveredText = "\"";
		}
		Span span = getSpan(coveredText, documentText, documentOffset);
		return factory.createAnnotation(span.getSpanStart(), span.getSpanEnd(),
				SpanUtils.getCoveredText(CollectionsUtil.createList(span), documentText),
				new DefaultClassMention(partOfSpeechTag));
	}

	private static Span getSpan(String coveredText, String documentText, int documentOffset) {
		String pattern = RegExPatterns.escapeCharacterForRegEx(coveredText);
		/*
		 * if there are commas in the pattern, then we need to allow for optional spaces
		 * to occur after the commas; same thing for colons
		 */
		pattern = pattern.replaceAll(",", ",[ ]?").replaceAll(":", ":[ ]?");
		Pattern p = Pattern.compile(pattern);

		Matcher m = p.matcher(documentText);
		String upcomingText = documentText.substring(documentOffset,
				(documentText.length() - documentOffset > 100) ? (documentOffset + 100) : documentText.length());
		if (m.find(documentOffset)) {
			return new Span(m.start(), m.end());
		}
		throw new IllegalArgumentException(
				"Could not find token text: '" + coveredText + "' in document starting here: " + upcomingText);
	}

	/**
	 * @param conllUReader
	 * @return all CoNLL-U lines for the next sentence
	 * @throws IOException
	 */
	public static String getLinesForNextSentence(BufferedReader conllUReader) throws IOException {
		StringBuffer lines = new StringBuffer();
		String line;
		while ((line = conllUReader.readLine()) != null) {
			if (line.trim().isEmpty()) {
				break;
			}
			lines.append(line + "\n");
		}
		/*
		 * if there are no more lines in the file and if there are no lines to return,
		 * then return null indicating there is not another sentence to process.
		 */
		if (line == null && lines.toString().isEmpty()) {
			return null;
		}
		return lines.toString();
	}

}
