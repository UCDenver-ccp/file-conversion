package edu.ucdenver.ccp.file.conversion.pubannotation;

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

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.mention.ClassMention;

public class PubAnnotationDocumentReaderTest {

	@Test
	public void testAnnotationDeserialization() {

		/* @formatter:off */
		String pubAnnotationData = "{\n"+
			   "\"text\": \"IRF-4 expression in CML may be induced by IFN-α therapy\",\n"+
			   "\"denotations\": [\n"+
			   "   {\"id\": \"T1\", \"span\": {\"begin\": 0, \"end\": 5}, \"obj\": \"Protein\"},\n"+
			   "   {\"id\": \"T2\", \"span\": {\"begin\": 42, \"end\": 47}, \"obj\": \"Promoter\"}\n"+
			   "],\n"+
			   "\"relations\": [\n"+
			   "   {\"id\": \"R1\", \"subj\": \"T2\", \"pred\": \"regulates\", \"obj\": \"T1\"}\n"+
			   "]\n"+
			"}";
		/* @formatter:on */

		List<TextAnnotation> annotations = PubAnnotationDocumentReader
				.getAnnotations(new ByteArrayInputStream(pubAnnotationData.getBytes()), CharacterEncoding.UTF_8);

		assertEquals("expect 2 annotations", 2, annotations.size());
		Collections.sort(annotations, TextAnnotation.BY_SPAN());
		assertEquals("Protein", annotations.get(0).getClassMention().getMentionName());
		assertEquals("Promoter", annotations.get(1).getClassMention().getMentionName());
		ClassMention cm = annotations.get(1).getClassMention().getComplexSlotMentionByName("regulates")
				.getClassMentions().iterator().next();
		assertEquals("Protein", cm.getMentionName());
		assertEquals(0, cm.getTextAnnotation().getAnnotationSpanStart());
	}

	@Test
	public void testAnnotationDeserialization_discontinuousSpan() {

		/* @formatter:off */
		String pubAnnotationData = "{\n"+
			   "\"text\": \"IRF-4 expression in CML may be induced by IFN-α therapy IRF-4 expression in CML may be induced by IFN-α therapy IRF-4 expression in CML may be induced by IFN-α therapy\",\n"+
			   "\"denotations\": [\n"+
			   "   {\"id\": \"T1\", \"span\": {\"begin\": 0, \"end\": 5}, \"obj\": \"Protein\"},\n"+
			   "   {\"id\": \"T2\", \"span\": {\"begin\": 42, \"end\": 47}, \"obj\": \"Promoter\"},\n"+
	   		   "   {\"id\":\"T3\",\"span\":{\"begin\":100,\"end\":104},\"obj\":\"_FRAGMENT\"},\n"+
			   "   {\"id\":\"T4\",\"span\":{\"begin\":115,\"end\":119},\"obj\":\"UBERON:0002168\"}\n"+
			   "],\n"+
			   "\"relations\": [\n"+
			   "   {\"id\": \"R1\", \"subj\": \"T2\", \"pred\": \"regulates\", \"obj\": \"T1\"},\n"+
			   "   {\"id\":\"R2\",\"pred\":\"_lexicallyChainedTo\",\"subj\":\"T4\",\"obj\":\"T3\"}\n"+
			   "]\n"+
			"}";
		/* @formatter:on */

		List<TextAnnotation> annotations = PubAnnotationDocumentReader
				.getAnnotations(new ByteArrayInputStream(pubAnnotationData.getBytes()), CharacterEncoding.UTF_8);

		assertEquals("expect 2 annotations", 3, annotations.size());
		Collections.sort(annotations, TextAnnotation.BY_SPAN());
		assertEquals("Protein", annotations.get(0).getClassMention().getMentionName());
		assertEquals("Promoter", annotations.get(1).getClassMention().getMentionName());
		ClassMention cm = annotations.get(1).getClassMention().getComplexSlotMentionByName("regulates")
				.getClassMentions().iterator().next();
		assertEquals("Protein", cm.getMentionName());
		assertEquals(0, cm.getTextAnnotation().getAnnotationSpanStart());

	}

}
