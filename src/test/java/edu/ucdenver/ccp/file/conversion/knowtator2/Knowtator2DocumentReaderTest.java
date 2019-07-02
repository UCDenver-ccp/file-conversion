package edu.ucdenver.ccp.file.conversion.knowtator2;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import org.junit.Test;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.io.ClassPathUtil;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;

public class Knowtator2DocumentReaderTest {

	@Test
	public void test() throws XMLStreamException, JAXBException, IOException {
		InputStream knowtator2Stream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample_disc_adj.xml");
		InputStream txtStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "17069463.txt");

		TextDocument td = new Knowtator2DocumentReader().readDocument("17069463", "PMID", knowtator2Stream, txtStream,
				CharacterEncoding.UTF_8);
		Set<TextAnnotation> annotations = new HashSet<TextAnnotation>(td.getAnnotations());

		assertEquals(3, annotations.size());

		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults("17069463");
		TextAnnotation annot1 = factory.createAnnotation(23954, 23978, "high-fat (~10% fat) diet",
				new DefaultClassMention("IDENTITY chain"));

		assertTrue(annotations.contains(annot1));

		/*
		 * This annot originally was annotated using adjacent discontinuous spans. In this case, the
		 * spans should be combined into a single continuous span.
		 */
		// TextAnnotation annot2 = factory.createAnnotation(23929, 23974,
		// "inbred RanBP2+/− mice on high-fat (~10% fat) .. diet", new DefaultClassMention("IDENTITY
		// chain"));
		// annot2.addSpan(new Span(23974, 23978));

		TextAnnotation annot2 = factory.createAnnotation(23929, 23978,
				"inbred RanBP2+/− mice on high-fat (~10% fat) diet", new DefaultClassMention("IDENTITY chain"));

		assertTrue(annotations.contains(annot2));

		TextAnnotation annot3 = factory.createAnnotation(23954, 23962, "high-fat diet",
				new DefaultClassMention("IDENTITY chain"));
		annot3.addSpan(new Span(23974, 23978));

		assertTrue(annotations.contains(annot3));
	}

}
