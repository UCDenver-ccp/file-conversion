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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.junit.Test;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileReaderUtil;
import edu.ucdenver.ccp.common.io.ClassPathUtil;
import edu.ucdenver.ccp.common.io.StreamUtil;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.mention.ClassMention;

public class CoNLLUDocumentReaderTest {

	@Test
	public void testGetLinesForNextSentence() throws IOException {
		InputStream conllUStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "doc1.conllu");
		BufferedReader conllUReader = FileReaderUtil.initBufferedReader(conllUStream, CharacterEncoding.UTF_8);

		String lines = CoNLLUDocumentReader.getLinesForNextSentence(conllUReader);
		assertEquals("Unexpected number of lines returned for next sentence", 12, lines.split("\\n").length);
		lines = CoNLLUDocumentReader.getLinesForNextSentence(conllUReader);
		assertEquals("Unexpected number of lines returned for next sentence", 1, lines.split("\\n").length);
		lines = CoNLLUDocumentReader.getLinesForNextSentence(conllUReader);
		assertEquals("Unexpected number of lines returned for next sentence", 1, lines.split("\\n").length);
		lines = CoNLLUDocumentReader.getLinesForNextSentence(conllUReader);
		assertEquals("Unexpected number of lines returned for next sentence", 18, lines.split("\\n").length);
		lines = CoNLLUDocumentReader.getLinesForNextSentence(conllUReader);
		assertEquals("Unexpected number of lines returned for next sentence", 50, lines.split("\\n").length);
		lines = CoNLLUDocumentReader.getLinesForNextSentence(conllUReader);
		assertEquals("Unexpected number of lines returned for next sentence", 1, lines.split("\\n").length);
		lines = CoNLLUDocumentReader.getLinesForNextSentence(conllUReader);
		assertEquals("Unexpected number of lines returned for next sentence", 18, lines.split("\\n").length);
		lines = CoNLLUDocumentReader.getLinesForNextSentence(conllUReader);

	}

	@Test
	public void testGetTokenAnnotations() throws IOException {
		InputStream conllUStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "doc1.conllu");
		InputStream docTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "doc1.txt");
		String documentText = StreamUtil
				.toString(new InputStreamReader(docTextStream, CharacterEncoding.UTF_8.getDecoder()));
		List<TextAnnotation> annotations = CoNLLUDocumentReader.getAnnotations(conllUStream, documentText,
				CharacterEncoding.UTF_8);

		assertEquals("Observed incorrect total number of annotations, 101 tokens + 7 sentences", 108,
				annotations.size());

		TextAnnotation annot = annotations.get(0);
		assertNotNull("deprel should be nmod", annot.getClassMention().getComplexSlotMentionByName("NMOD"));
		ClassMention pressureCm = annot.getClassMention().getComplexSlotMentionByName("NMOD").getClassMentions()
				.iterator().next();
		assertEquals("pressure token should be noun", "NN", pressureCm.getMentionName());
		assertTrue("pressure token should be the ROOT", pressureCm.getComplexSlotMentions().isEmpty());
	}

}
