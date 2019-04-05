package edu.ucdenver.ccp.file.conversion.bionlp;

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
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileWriterUtil;
import edu.ucdenver.ccp.file.conversion.DocumentWriter;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.mention.ClassMention;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;

/**
 * This code assumes all input annotations are entities, e.g. use the 'T' prefix
 * for their identifier
 */
public class BioNLPDocumentWriter extends DocumentWriter {

	/**
	 * The BioNLP format does not support spaces in annotation and relation
	 * types, so any spaces in an annotation or relation type must be replaced.
	 * This constant is used as a replacement in the BioNLP format documents
	 * created by this DocumentWriter.
	 */
	public static final String SPACE_PLACEHOLDER = "^";

	@Override
	public void serialize(TextDocument td, OutputStream outputStream, CharacterEncoding encoding) throws IOException {
		List<TextAnnotation> annotations = td.getAnnotations();
		Collections.sort(annotations, TextAnnotation.BY_SPAN());
		int tIndex = 1;
		Map<TextAnnotation, String> annotToIdMap = new HashMap<TextAnnotation, String>();
		/* serialize all annotations */
		try (BufferedWriter writer = FileWriterUtil.initBufferedWriter(outputStream, encoding)) {
			for (TextAnnotation ta : annotations) {
				String id = "T" + tIndex++;
				annotToIdMap.put(ta, id);
				writer.write(serializeAnnotation(ta, id) + "\n");
			}

			/* then serialize relations */
			int rIndex = 1;
			for (Entry<TextAnnotation, String> entry : annotToIdMap.entrySet()) {
				Collection<ComplexSlotMention> csms = entry.getKey().getClassMention().getComplexSlotMentions();
				if (csms != null && !csms.isEmpty()) {
					for (ComplexSlotMention csm : csms) {
						String relationType = csm.getMentionName();
						String sourceAnnotId = entry.getValue();
						for (ClassMention cm : csm.getClassMentions()) {
							String targetAnnotId = annotToIdMap.get(cm.getTextAnnotation());
							String relationId = "R" + rIndex++;
							writer.write(
									serializeRelation(relationId, relationType, sourceAnnotId, targetAnnotId) + "\n");
						}
					}
				}
			}
		}
	}

	private static String serializeAnnotation(TextAnnotation ta, String annotId) {
		StringBuffer sb = new StringBuffer();
		sb.append(annotId + "\t");
		String annotType = ta.getClassMention().getMentionName();
		/*
		 * BioNLP format does not support spaces in the annotation type, so
		 * replace all spaces in annotType with SPACE_PLACEHOLDER
		 */
		annotType = annotType.replaceAll(" ", SPACE_PLACEHOLDER);
		sb.append(annotType + " ");
		for (int i = 0; i < ta.getSpans().size(); i++) {
			Span span = ta.getSpans().get(i);
			sb.append(((i > 0) ? ";" : "") + span.getSpanStart() + " " + span.getSpanEnd());
		}
		sb.append("\t" + ta.getCoveredText().replaceAll("\\n", " "));

		return sb.toString();
	}

	private static String serializeRelation(String relationId, String relationType, String sourceAnnotId,
			String targetAnnotId) {
		StringBuffer sb = new StringBuffer();
		sb.append(relationId + "\t");
		sb.append(
				relationType.replaceAll(" ", SPACE_PLACEHOLDER) + " Arg1:" + sourceAnnotId + " Arg2:" + targetAnnotId);
		
		return sb.toString();
	}

}
