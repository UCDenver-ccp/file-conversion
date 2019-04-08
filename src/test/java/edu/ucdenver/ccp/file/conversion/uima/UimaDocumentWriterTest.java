package edu.ucdenver.ccp.file.conversion.uima;

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;

import org.apache.uima.UIMAException;
import org.apache.uima.cas.impl.XmiCasDeserializer;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.junit.Test;
import org.xml.sax.SAXException;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;
import edu.ucdenver.ccp.nlp.core.uima.annotation.CCPTextAnnotation;
import edu.ucdenver.ccp.nlp.uima.util.TypeSystemUtil;
import edu.ucdenver.ccp.nlp.uima.util.UIMA_Util;

public class UimaDocumentWriterTest {

	@Test
	public void testUimaDocumentWriter() throws IOException, SAXException, UIMAException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();

		String documentText = "Intraocular pressure in genetically distinct mice: an update and strain survey\n\nAbstract\n\nBackground\n\nLittle is known about genetic factors affecting intraocular pressure (IOP) in mice and other mammals.";

		TextDocument td = new TextDocument("12345", "PMC", documentText);

		td.addAnnotation(factory.createAnnotation(0, 78,
				"Intraocular pressure in genetically distinct mice: an update and strain survey",
				new DefaultClassMention("sentence")));
		td.addAnnotation(factory.createAnnotation(80, 88, "Abstract", new DefaultClassMention("sentence")));
		td.addAnnotation(factory.createAnnotation(90, 100, "Background", new DefaultClassMention("sentence")));
		td.addAnnotation(factory.createAnnotation(102, 203,
				"Little is known about genetic factors affecting intraocular pressure (IOP) in mice and other mammals.",
				new DefaultClassMention("sentence")));

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new UimaDocumentWriter().serialize(td, outputStream, encoding);

		String xmi = outputStream.toString(encoding.getCharacterSetName());
		JCas jCas = JCasFactory.createJCas(TypeSystemUtil.CCP_TYPE_SYSTEM);
		XmiCasDeserializer.deserialize(new ByteArrayInputStream(xmi.getBytes()), jCas.getCas());

		Collection<CCPTextAnnotation> annots = JCasUtil.select(jCas, CCPTextAnnotation.class);

		assertEquals("should be 4 sentences", 4, annots.size());
		for (CCPTextAnnotation annot : annots) {
			assertEquals("each annot should be of type 'sentence'", "sentence",
					annot.getClassMention().getMentionName());
		}
		assertEquals("document id should be 12345", "12345", UIMA_Util.getDocumentID(jCas));
		assertEquals("document text should be the same", documentText, jCas.getDocumentText());

	}

}
