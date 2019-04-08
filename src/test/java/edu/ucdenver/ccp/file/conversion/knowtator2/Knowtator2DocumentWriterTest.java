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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultComplexSlotMention;

public class Knowtator2DocumentWriterTest {

	@Test
	public void testDocumentWriter_noRelations() throws IOException {
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();

		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 5, "BRCA2", "Protein", "CCP"));
		annotations.add(factory.createAnnotation(55, 60, "PPARD", "Gene", "CCP"));

		TextAnnotation proteinWithDiscontinuousSpan = factory.createAnnotation(100, 104, "PPAR gamma", "Gene", "CCP");
		proteinWithDiscontinuousSpan.addSpan(new Span(115, 120));
		proteinWithDiscontinuousSpan.setAnnotationID("annot-id-1234");
		annotations.add(proteinWithDiscontinuousSpan);
		// 0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
		TextDocument td = new TextDocument("12345", "PMC",
				"BRCA2 this is where the document text goes this is whe PPARD e the document text goes this is where PPAR the docum gamma nt text goes this is where the document text goes this is where the document text goes");
		td.setAnnotations(annotations);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new Knowtator2DocumentWriter().serialize(td, outputStream, CharacterEncoding.UTF_8);
		String serializedXml = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName());

		/* @formatter:off */
		List<String> expectedXmlLines = CollectionsUtil.createList(
		"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>",
		"<knowtator-project>",
		"    <document id=\"12345\" text-file=\"12345.txt\">",
		"        <annotation annotator=\"CCP\" id=\"0\" type=\"identity\">",
		"            <class id=\"Protein\" label=\"Protein\"/>",
		"            <span end=\"5\" id=\"12345-0\" start=\"0\">BRCA2</span>",
		"        </annotation>",
		"        <annotation annotator=\"CCP\" id=\"1\" type=\"identity\">",
		"            <class id=\"Gene\" label=\"Gene\"/>",
		"            <span end=\"60\" id=\"12345-1\" start=\"55\">PPARD</span>",
		"        </annotation>",
		"        <annotation annotator=\"CCP\" id=\"annot-id-1234\" type=\"identity\">",
		"            <class id=\"Gene\" label=\"Gene\"/>",
		"            <span end=\"104\" id=\"12345-2\" start=\"100\">PPAR</span>",
		"            <span end=\"120\" id=\"12345-3\" start=\"115\">gamma</span>",
		"        </annotation>",
		"        <graph-space id=\"Old Knowtator Relations\"/>",
		"    </document>",
		"</knowtator-project>");
		/* @formatter:on */

		StringBuffer sb = new StringBuffer();
		for (String line : expectedXmlLines) {
			sb.append(line + "\n");
		}
		String expectedXml = sb.toString();

		System.out.println("SER:\n" + serializedXml + ";;;");
		System.out.println("EXP:\n" + expectedXml + ";;;");

		assertEquals("XML not as expected", expectedXml, serializedXml);

	}

	@Test
	public void testDocumentWriter_withRelations() throws IOException {
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 5, "BRCA2", "Protein", "CCP"));
		TextAnnotation ppardAnnot1 = factory.createAnnotation(55, 60, "PPARD", "Gene", "CCP");
		annotations.add(ppardAnnot1);

		TextAnnotation ppardAnnot2 = factory.createAnnotation(100, 104, "PPAR gamma", "Gene", "CCP");
		ppardAnnot2.addSpan(new Span(115, 120));
		ppardAnnot2.setAnnotationID("annot-id-1234");
		annotations.add(ppardAnnot2);

		/* link the PPARD annotations with a relation (type = identity chain) */
		String relationType = "related";
		ComplexSlotMention csm = new DefaultComplexSlotMention(relationType);
		csm.addClassMention(ppardAnnot2.getClassMention());
		ppardAnnot1.getClassMention().addComplexSlotMention(csm);

		TextDocument td = new TextDocument("12345", "PMC",
				"BRCA2 this is where the document text goes this is whe PPARD e the document text goes this is where PPAR the docum gamma nt text goes this is where the document text goes this is where the document text goes");
		td.setAnnotations(annotations);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new Knowtator2DocumentWriter().serialize(td, outputStream, CharacterEncoding.UTF_8);
		String serializedXml = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName());

		/* @formatter:off */
		List<String> expectedXmlLines = CollectionsUtil.createList(
		"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>",
		"<knowtator-project>",
		"    <document id=\"12345\" text-file=\"12345.txt\">",
		"        <annotation annotator=\"CCP\" id=\"0\" type=\"identity\">",
		"            <class id=\"Protein\" label=\"Protein\"/>",
		"            <span end=\"5\" id=\"12345-0\" start=\"0\">BRCA2</span>",
		"        </annotation>",
		"        <annotation annotator=\"CCP\" id=\"1\" type=\"identity\">",
		"            <class id=\"Gene\" label=\"Gene\"/>",
		"            <span end=\"60\" id=\"12345-1\" start=\"55\">PPARD</span>",
		"        </annotation>",
	    "        <annotation annotator=\"CCP\" id=\"annot-id-1234\" type=\"identity\">",
		"            <class id=\"Gene\" label=\"Gene\"/>",
		"            <span end=\"104\" id=\"12345-2\" start=\"100\">PPAR</span>",
		"            <span end=\"120\" id=\"12345-3\" start=\"115\">gamma</span>",
		"        </annotation>",
		"        <graph-space id=\"Old Knowtator Relations\">",
		"            <vertex annotation=\"0\" id=\"node_0\"/>",
		"            <vertex annotation=\"1\" id=\"node_1\"/>",
		"            <vertex annotation=\"annot-id-1234\" id=\"node_2\"/>",
		"            <triple annotator=\"CCP\" id=\"edge_0\" object=\"node_2\" property=\"related\" quantifier=\"\" subject=\"node_1\" value=\"\"/>",
		"        </graph-space>",
		"    </document>",
		"</knowtator-project>");
		/* @formatter:on */

		StringBuffer sb = new StringBuffer();
		for (String line : expectedXmlLines) {
			sb.append(line + "\n");
		}
		String expectedXml = sb.toString();

		System.out.println("SER:\n" + serializedXml + ";;;");
		System.out.println("EXP:\n" + expectedXml + ";;;");

		assertEquals("XML not as expected", expectedXml, serializedXml);

	}

}
