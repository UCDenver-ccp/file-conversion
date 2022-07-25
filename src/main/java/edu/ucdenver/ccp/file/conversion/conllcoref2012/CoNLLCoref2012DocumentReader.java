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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileReaderUtil;
import edu.ucdenver.ccp.common.io.StreamUtil;
import edu.ucdenver.ccp.common.string.StringUtil;
import edu.ucdenver.ccp.file.conversion.DocumentReader;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.file.conversion.conllu.CoNLLUDocumentReader;
import edu.ucdenver.ccp.file.conversion.util.DocumentReaderUtil;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.SpanUtils;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;
import lombok.Data;

public class CoNLLCoref2012DocumentReader extends DocumentReader {
	public static final String NOUN_PHRASE = "Noun Phrase";

	public static final String IDENTITY_CHAIN = "IDENTITY chain";
	public static final String IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT = "Coreferring strings";

	public static final String APPOS_RELATION = "APPOS relation";
	public static final String APPOS_HEAD_SLOT = "APPOS Head";
	public static final String APPOS_ATTRIBUTES_SLOT = "APPOS Attributes";

	@Override
	public TextDocument readDocument(String sourceId, String sourceDb, InputStream inputStream,
			InputStream documentTextStream, CharacterEncoding encoding) throws IOException {
		// document text is used to get the spans for all annotations
		String documentText = StreamUtil.toString(new InputStreamReader(documentTextStream, encoding.getDecoder()));
		TextDocument td = new TextDocument(sourceId, sourceDb, documentText);
		
		List<TextAnnotation> annotations = getAnnotations(inputStream, sourceId, documentText, encoding);
		DocumentReaderUtil.validateSpans(annotations, documentText, sourceId);
		td.addAnnotations(annotations);
		return td;

	}

	public static List<TextAnnotation> getAnnotations(InputStream conllUStream, String documentId, String documentText,
			CharacterEncoding encoding) throws IOException {

		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults(documentId);
		int documentOffset = 0;
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();

		Map<Integer, TextAnnotation> chainIdToIdentityAnnotMap = new HashMap<Integer, TextAnnotation>();
		Map<Integer, Stack<OpenChainMember>> chainIdToOpenChainMembers = new HashMap<Integer, Stack<OpenChainMember>>();

		/*
		 * track the annotations for discontinuous chain members so that their
		 * spans can be added as they are observed by the reader
		 */
		Map<String, TextAnnotation> discontinuousNpIdToAnnotMap = new HashMap<String, TextAnnotation>();
		Map<String, Integer> discontinuousNpIdToChainIdMap = new HashMap<String, Integer>();

		BufferedReader conllUReader = FileReaderUtil.initBufferedReader(conllUStream, encoding);
		String sentenceLines;
		while ((sentenceLines = CoNLLUDocumentReader.getLinesForNextSentence(conllUReader)) != null) {

			if (sentenceLines.startsWith("#end document")) {
				continue;
			}
			
			int sentenceStart = documentOffset;
			Map<Integer, TextAnnotation> tokenNumToAnnotMap = new HashMap<Integer, TextAnnotation>();

			/* create a token annotation for each CoNNL-U line */
			int tokenIndex = 1;
			for (CoNLLCoref2012RecordReader rr = new CoNLLCoref2012RecordReader(
					new ByteArrayInputStream(sentenceLines.getBytes()), encoding); rr.hasNext();) {
				CoNLLCoref2012FileRecord record = rr.next();
				TextAnnotation token = CoNLLUDocumentReader.createTokenAnnotation(record.getWord(),
						record.getPartOfSpeech(), documentText, documentOffset, factory);

				annotations.add(token);
				tokenNumToAnnotMap.put(tokenIndex++, token);
				documentOffset = token.getAggregateSpan().getSpanEnd();

				trimAnnotation(documentText, token);

				/*
				 * now check to see if a coreference chain member starts, ends,
				 * or both at this token.
				 */
				String corefInfo = record.getCoreference();
				if (!corefInfo.equals("-")) {
					String[] toks = corefInfo.split("\\|");
					for (String tok : toks) {
						tok = tok.trim();
						if (tok.matches("\\([0-9]+\\w*\\)")) {
							/* then this token is itself a chain member */
							Matcher m = Pattern.compile("\\(([0-9]+)(\\w*)\\)").matcher(tok);
							if (m.find()) {
								int chainId = Integer.parseInt(m.group(1));
								String mentionId = (m.group(2).isEmpty()) ? null : m.group(2);
								ClosedChainMember chainMember = new ClosedChainMember(chainId, mentionId,
										token.getAnnotationSpanStart(), token.getAnnotationSpanEnd());
								addChainMember(chainMember, factory, annotations, chainIdToIdentityAnnotMap,
										discontinuousNpIdToAnnotMap, discontinuousNpIdToChainIdMap, documentText);
							} else {
								rr.close();
								throw new IllegalStateException(
										"Inner pattern should match outer pattern, so it should be impossible to be here.");
							}
						} else if (tok.matches("\\([0-9]+\\w*")) {
							Matcher m = Pattern.compile("\\(([0-9]+)(\\w*)").matcher(tok);
							if (m.find()) {
								int chainId = Integer.parseInt(m.group(1));
								String mentionId = (m.group(2).isEmpty()) ? null : m.group(2);
								OpenChainMember openChainMember = new OpenChainMember(chainId, mentionId,
										token.getAnnotationSpanStart());
								storeOpenChainMember(openChainMember, chainIdToOpenChainMembers);
							} else {
								rr.close();
								throw new IllegalStateException(
										"Inner pattern should match outer pattern, so it should be impossible to be here.");
							}
						} else if (tok.matches("[0-9]+\\w*\\)")) {
							Matcher m = Pattern.compile("([0-9]+)(\\w*)\\)").matcher(tok);
							if (m.find()) {
								int chainId = Integer.parseInt(m.group(1));
								String mentionId = (m.group(2).isEmpty()) ? null : m.group(2);
								ClosedChainMember closedChainMember = closeChainMember(chainId, mentionId,
										token.getAnnotationSpanEnd(), chainIdToOpenChainMembers);
								addChainMember(closedChainMember, factory, annotations, chainIdToIdentityAnnotMap,
										discontinuousNpIdToAnnotMap, discontinuousNpIdToChainIdMap, documentText);
							} else {
								rr.close();
								throw new IllegalStateException(
										"Inner pattern should match outer pattern, so it should be impossible to be here.");
							}
						} else {
							rr.close();
							throw new IllegalStateException("Encountered unexpected chain status: " + tok);
						}
					}
				}
			}
			int sentenceEnd = documentOffset;

			TextAnnotation sentence = factory.createAnnotation(sentenceStart, sentenceEnd, SpanUtils
					.getCoveredText(CollectionsUtil.createList(new Span(sentenceStart, sentenceEnd)), documentText),
					new DefaultClassMention("sentence"));

			trimAnnotation(documentText, sentence);
			annotations.add(sentence);

		}
		return annotations;
	}

	/**
	 * removes any leading/trailing whitespace from the annotation
	 * 
	 * @param documentText
	 * @param annot
	 */
	public static TextAnnotation trimAnnotation(String documentText, TextAnnotation annot) {
		while (StringUtil.endsWithRegex(SpanUtils.getCoveredText(annot.getSpans(), documentText),"\\s")) {
			int spanStart = annot.getAggregateSpan().getSpanStart();
			int spanEnd = annot.getAggregateSpan().getSpanEnd() - 1;
			annot.setSpan(new Span(spanStart, spanEnd));
			annot.setCoveredText(SpanUtils.getCoveredText(annot.getSpans(), documentText));
		}

		while (StringUtil.startsWithRegex(SpanUtils.getCoveredText(annot.getSpans(), documentText),"\\s")) {
			int spanStart = annot.getAggregateSpan().getSpanStart() + 1;
			int spanEnd = annot.getAggregateSpan().getSpanEnd();
			annot.setSpan(new Span(spanStart, spanEnd));
			annot.setCoveredText(SpanUtils.getCoveredText(annot.getSpans(), documentText));
		}
		return annot;
	}

	/**
	 * There should be an open chain member (i.e. a chain member where we know
	 * the span start but not the span end) waiting on top of a stack identified
	 * by the chain id. This method grabs it (pops from stack), populates the
	 * span end field and returns the complete (closed) chain member.
	 * 
	 * @param chainId
	 * @param spanEnd
	 * @param chainIdToOpenChainMembers
	 * @return
	 */
	private static ClosedChainMember closeChainMember(int chainId, String mentionId, int spanEnd,
			Map<Integer, Stack<OpenChainMember>> chainIdToOpenChainMembers) {
		OpenChainMember openChainMember = chainIdToOpenChainMembers.get(chainId).pop();
		return new ClosedChainMember(chainId, mentionId, openChainMember.getSpanStart(), spanEnd);
	}

	/**
	 * store the input open chain member (open b/c we know where it starts, but
	 * not where it ends) in a stack identified by the chain id.
	 * 
	 * @param openChainMember
	 * @param chainIdToOpenChainMembers
	 */
	private static void storeOpenChainMember(OpenChainMember openChainMember,
			Map<Integer, Stack<OpenChainMember>> chainIdToOpenChainMembers) {
		if (chainIdToOpenChainMembers.containsKey(openChainMember.getChainId())) {
			Stack<OpenChainMember> stack = chainIdToOpenChainMembers.get(openChainMember.getChainId());
			stack.push(openChainMember);
		} else {
			Stack<OpenChainMember> stack = new Stack<OpenChainMember>();
			stack.push(openChainMember);
			chainIdToOpenChainMembers.put(openChainMember.getChainId(), stack);
		}
	}

	/**
	 * Logs the completion (closing) of a chain member annotation. The
	 * annotation is created as a Noun phrase, and is added to an IDENTITY chain
	 * annotation in the 'Coreferring strings' slot. It is possible the
	 * ClosedChainMember is a closed span for a discontinuous noun phrase
	 * annotation. If this is the case, then a span gets added to an already
	 * existing noun phrase annotation.
	 * 
	 * @param chainMember
	 * @param factory
	 * @param annotations
	 * @param chainIdToIdentityAnnotMap
	 * @param documentText
	 */
	private static void addChainMember(ClosedChainMember chainMember, TextAnnotationFactory factory,
			List<TextAnnotation> annotations, Map<Integer, TextAnnotation> chainIdToIdentityAnnotMap,
			Map<String, TextAnnotation> discontinuousNpIdToNpAnnotMap,
			Map<String, Integer> discontinuousNpIdToChainIdMap, String documentText) {
		String key = chainMember.getChainId() + chainMember.getMentionId();
		if (chainMember.getMentionId() != null && discontinuousNpIdToNpAnnotMap.containsKey(key)) {
			Span span = new Span(chainMember.getSpanStart(), chainMember.getSpanEnd());
			discontinuousNpIdToNpAnnotMap.get(key).addSpan(span);
			discontinuousNpIdToNpAnnotMap.get(key).setCoveredText(
					SpanUtils.getCoveredText(discontinuousNpIdToNpAnnotMap.get(key).getSpans(), documentText));

			/*
			 * if this discontinuous chain member is the head of the chain, then
			 * update the span and covered text of the appropriate ident chain
			 * annotation
			 */
			if (discontinuousNpIdToChainIdMap.containsKey(key)) {
				TextAnnotation identAnnot = chainIdToIdentityAnnotMap.get(discontinuousNpIdToChainIdMap.get(key));
				identAnnot.addSpan(span);
				identAnnot.setCoveredText(SpanUtils.getCoveredText(identAnnot.getSpans(), documentText));
			}
		} else {
			TextAnnotation npAnnot = factory.createAnnotation(chainMember.getSpanStart(), chainMember.getSpanEnd(),
					documentText.substring(chainMember.getSpanStart(), chainMember.getSpanEnd()),
					new DefaultClassMention(NOUN_PHRASE));
			annotations.add(npAnnot);
			boolean createdNewChain = addToIdentityChain(chainMember.getChainId(), npAnnot, chainIdToIdentityAnnotMap,
					factory, annotations);
			if (chainMember.getMentionId() != null) {
				discontinuousNpIdToNpAnnotMap.put(key, npAnnot);
				if (createdNewChain) {
					discontinuousNpIdToChainIdMap.put(key, chainMember.getChainId());
				}
			}
		}
	}

	/**
	 * Adds the specified Noun phrase annotation (npAnnot) to an identity chain.
	 * Creates the Identity chain if it doesn't already exist.
	 * 
	 * @param chainId
	 * @param npAnnot
	 * @param chainIdToIdentityAnnotMap
	 * @param factory
	 * @param annotations
	 * @return true if a new identity chain was created
	 */
	private static boolean addToIdentityChain(int chainId, TextAnnotation npAnnot,
			Map<Integer, TextAnnotation> chainIdToIdentityAnnotMap, TextAnnotationFactory factory,
			List<TextAnnotation> annotations) {
		TextAnnotation identityChainAnnot = null;
		boolean createdNewChain = false;
		if (chainIdToIdentityAnnotMap.containsKey(chainId)) {
			identityChainAnnot = chainIdToIdentityAnnotMap.get(chainId);
		} else {
			createdNewChain = true;
			identityChainAnnot = factory.createAnnotation(npAnnot.getAnnotationSpanStart(),
					npAnnot.getAnnotationSpanEnd(), npAnnot.getCoveredText(), new DefaultClassMention(IDENTITY_CHAIN));
			chainIdToIdentityAnnotMap.put(chainId, identityChainAnnot);
			annotations.add(identityChainAnnot);
		}
		ComplexSlotMention csm = identityChainAnnot.getClassMention()
				.getComplexSlotMentionByName(IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
		csm.addClassMention(npAnnot.getClassMention());
		return createdNewChain;
	}

	/**
	 * data structure to log the observance of a Noun Phrase annotation that is
	 * open (i.e. we know the span start, but not the span end) as we read
	 * through the CoNLLCoref records sequentially.
	 *
	 */
	@Data
	private static class OpenChainMember {
		private final int chainId;
		private final String mentionId;
		private final int spanStart;
	}

	/**
	 * data structure to log the completion of a Noun phrase annotation that
	 * will be a member of an IDENTITY chain. This is created from an
	 * OpenChainMember once the span end is known.
	 */
	@Data
	private static class ClosedChainMember {
		private final int chainId;
		private final String mentionId;
		private final int spanStart;
		private final int spanEnd;
	}

}
