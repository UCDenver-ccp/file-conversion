package edu.ucdenver.ccp.file.conversion.pubannotation;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultComplexSlotMention;

public class PubAnnotationDocumentWriterTest {

	@Test
	public void testWriteEntityAnnotation() throws CloneNotSupportedException, IOException {
		/* @formatter:off */
		/* document text from https://en.wikipedia.org/wiki/Peroxisome_proliferator-activated_receptor_delta */
		String documentText = "Peroxisome proliferator-activated receptor beta or delta (PPAR-β or PPAR-δ), also known as NR1C2 (nuclear receptor subfamily 1, group C, member 2) is a nuclear receptor that in humans is encoded by the PPARD gene.";
		//                     0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
		//                               1         2         3         4         5         6         7         8         9         0         1         2         3         4         5         6         7         8         9         0          1                  
		//                                                                                                                         1         1         1         1         1         1         1         1         1         1         2          2
        /* @formatter:on */

		String documentSource = "wikipedia";
		String documentId = "https://en.wikipedia.org/wiki/Peroxisome_proliferator-activated_receptor_delta";

		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults(documentId);
		TextAnnotation annot = factory.createAnnotation(222, 227, "PPARD", "gene");

		TextDocument td = new TextDocument(documentId, documentSource, documentText);
		td.addAnnotation(annot);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new PubAnnotationDocumentWriter().serialize(td, outputStream, CharacterEncoding.UTF_8);
		String json = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName()).replaceAll("\\s", "");

		String expectedJson = ("{" + "\"sourceid\":\"" + documentId + "\"," + "\"sourcedb\":\"" + documentSource + "\","
				+ "\"text\":\"" + documentText + "\"," + "\"denotations\":["
				+ "{\"id\":\"T1\",\"span\":{\"begin\":222,\"end\":227},\"obj\":\"gene\"}" + "]}").replaceAll("\\s", "");

		System.out.println("EXPECTED:" + expectedJson);
		System.out.println("GENERATD:" + json);

		assertEquals("PubAnnotation JSON not as expected.", expectedJson, json);
	}

	@Test
	public void testWriteEntityAnnotation_DiscontinuousSpan() throws CloneNotSupportedException, IOException {
		/* @formatter:off */
		/* document text from https://en.wikipedia.org/wiki/Peroxisome_proliferator-activated_receptor_delta */
		String documentText = "Peroxisome proliferator-activated receptor beta or delta (PPAR-β or PPAR-δ), also known as NR1C2 (nuclear receptor subfamily 1, group C, member 2) is a nuclear receptor that in humans is encoded by the PPARD gene.";
		//                     0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
		//                               1         2         3         4         5         6         7         8         9         0         1         2         3         4         5         6         7         8         9         0          1                  
		//                                                                                                                         1         1         1         1         1         1         1         1         1         1         2          2
        /* @formatter:on */

		String documentSource = "wikipedia";
		String documentId = "https://en.wikipedia.org/wiki/Peroxisome_proliferator-activated_receptor_delta";

		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults(documentId);
		TextAnnotation annot = factory.createAnnotation(0, 47, "Peroxisome proliferator-activated receptor delta",
				"gene");
		annot.addSpan(new Span(51, 56));

		TextDocument td = new TextDocument(documentId, documentSource, documentText);
		td.addAnnotation(annot);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new PubAnnotationDocumentWriter().serialize(td, outputStream, CharacterEncoding.UTF_8);
		String json = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName()).replaceAll("\\s", "");

		String expectedJson = ("{" + "\"sourceid\":\"" + documentId + "\"," + "\"sourcedb\":\"" + documentSource + "\","
				+ "\"text\":\"" + documentText + "\"," + "\"denotations\":["
				+ "{\"id\":\"T1\",\"span\":{\"begin\":0,\"end\":47},\"obj\":\"_FRAGMENT\"},"
				+ "{\"id\":\"T2\",\"span\":{\"begin\":51,\"end\":56},\"obj\":\"gene\"}],"

				+ "\"relations\":[" + "{\"id\":\"R1\",\"subj\":\"T2\",\"pred\":\"_lexicallyChainedTo\",\"obj\":\"T1\"}"

				+ "]}").replaceAll("\\s", "");

		System.out.println("EXPECTED:" + expectedJson);
		System.out.println("GENERATD:" + json);

		assertEquals("PubAnnotation JSON not as expected.", expectedJson, json);
	}

	@Test
	public void testWriteEntityAnnotation_DiscontinuousSpan_WithRelation()
			throws CloneNotSupportedException, IOException {
		/* @formatter:off */
		/* document text from https://en.wikipedia.org/wiki/Peroxisome_proliferator-activated_receptor_delta */
		String documentText = "Peroxisome proliferator-activated receptor beta or delta (PPAR-β or PPAR-δ), also known as NR1C2 (nuclear receptor subfamily 1, group C, member 2) is a nuclear receptor that in humans is encoded by the PPARD gene.";
		//                     0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
		//                               1         2         3         4         5         6         7         8         9         0         1         2         3         4         5         6         7         8         9         0          1                  
		//                                                                                                                         1         1         1         1         1         1         1         1         1         1         2          2
        /* @formatter:on */

		String documentSource = "wikipedia";
		String documentId = "https://en.wikipedia.org/wiki/Peroxisome_proliferator-activated_receptor_delta";

		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults(documentId);
		TextAnnotation annot = factory.createAnnotation(0, 47, "Peroxisome proliferator-activated receptor delta",
				"gene");
		annot.addSpan(new Span(51, 56));
		TextAnnotation annot2 = factory.createAnnotation(68, 74, "PPAR-δ", "gene");

		ComplexSlotMention csm = new DefaultComplexSlotMention("hasAbbreviation");
		csm.addClassMention(annot2.getClassMention());
		annot.getClassMention().addComplexSlotMention(csm);

		TextDocument td = new TextDocument(documentId, documentSource, documentText);
		td.addAnnotation(annot);
		td.addAnnotation(annot2);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new PubAnnotationDocumentWriter().serialize(td, outputStream, CharacterEncoding.UTF_8);
		String json = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName()).replaceAll("\\s", "");

		String expectedJson = ("{" + "\"sourceid\":\"" + documentId + "\"," + "\"sourcedb\":\"" + documentSource + "\","
				+ "\"text\":\"" + documentText + "\"," + "\"denotations\":["
				+ "{\"id\":\"T1\",\"span\":{\"begin\":0,\"end\":47},\"obj\":\"_FRAGMENT\"},"
				+ "{\"id\":\"T2\",\"span\":{\"begin\":51,\"end\":56},\"obj\":\"gene\"},"
				+ "{\"id\":\"T3\",\"span\":{\"begin\":68,\"end\":74},\"obj\":\"gene\"}],"

				+ "\"relations\":[" + "{\"id\":\"R1\",\"subj\":\"T2\",\"pred\":\"_lexicallyChainedTo\",\"obj\":\"T1\"},"
				+ "{\"id\":\"R2\",\"subj\":\"T2\",\"pred\":\"hasAbbreviation\",\"obj\":\"T3\"}"

				+ "]}").replaceAll("\\s", "");

		System.out.println("EXPECTED:" + expectedJson);
		System.out.println("GENERATD:" + json);

		assertEquals("PubAnnotation JSON not as expected.", expectedJson, json);
	}

}
