package edu.ucdenver.ccp.file.conversion.conllcoref2012;

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

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.io.ClassPathUtil;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;

public class CoNLLCoref2012DocumentReaderTest {

	@Test
	public void testDocumentReader() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		InputStream conllStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.ident.conll");
		InputStream documentTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.txt");

		TextDocument td = new CoNLLCoref2012DocumentReader().readDocument("11532192", "PMID", conllStream,
				documentTextStream, encoding);

		assertEquals("Expected 130 annotations, 101 tokens + 7 sentences + 19 noun phrase + 5 identity chain", 132,
				td.getAnnotations().size());

		int npCount = 0;
		int identCount = 0;

		for (TextAnnotation ta : td.getAnnotations()) {
			String type = ta.getClassMention().getMentionName();
			if (type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.NOUN_PHRASE)) {
				npCount++;
			} else if (type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)) {
				identCount++;
			}
		}

		assertEquals("Expect 19 noun phrase annotations", 19, npCount);
		assertEquals("Expect 5 IDENTITY chain annotations", 5, identCount);

		// check the IOP ident chain
		for (TextAnnotation ta : td.getAnnotations()) {
			String type = ta.getClassMention().getMentionName();
			if (type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)) {
				// 1
				if (ta.getCoveredText().equals("Intraocular pressure")) {
					ComplexSlotMention csm = ta.getClassMention().getComplexSlotMentionByName(
							CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
					assertEquals("There should be 7 chain members for the IOP chain.", 7,
							csm.getClassMentions().size());
				}
				// 2
				if (ta.getCoveredText().equals("genetically distinct mice")) {
					ComplexSlotMention csm = ta.getClassMention().getComplexSlotMentionByName(
							CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
					assertEquals("There should be 4 chain members for the mice chain.", 4,
							csm.getClassMentions().size());
				}
				// 3
				if (ta.getCoveredText().equals("update")) {
					ComplexSlotMention csm = ta.getClassMention().getComplexSlotMentionByName(
							CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
					assertEquals("There should be 2 chain members for the update chain.", 2,
							csm.getClassMentions().size());
				}
				// 4
				if (ta.getCoveredText().equals("genetic factors affecting intraocular pressure (IOP)")) {
					ComplexSlotMention csm = ta.getClassMention().getComplexSlotMentionByName(
							CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
					assertEquals("There should be 3 chain members for the mice chain.", 3,
							csm.getClassMentions().size());
				}
				// 5
				if (ta.getCoveredText().equals("genetically distinct mouse strains")) {
					ComplexSlotMention csm = ta.getClassMention().getComplexSlotMentionByName(
							CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
					assertEquals("There should be 3 chain members for the mice chain.", 3,
							csm.getClassMentions().size());
				}
			}
		}
	}

}
