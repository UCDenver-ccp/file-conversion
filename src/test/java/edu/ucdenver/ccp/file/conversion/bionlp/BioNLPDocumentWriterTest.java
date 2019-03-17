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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultComplexSlotMention;

public class BioNLPDocumentWriterTest {

	@Test
	public void testBioNLPAnotationSerialization() throws IOException {
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 5, "BRCA2", new DefaultClassMention("Protein")));
		annotations.add(factory.createAnnotation(55, 60, "PPARD", new DefaultClassMention("Gene")));

		TextAnnotation proteinWithDiscontinuousSpan = factory.createAnnotation(100, 104, "PPAR gamma",
				new DefaultClassMention("Gene"));
		proteinWithDiscontinuousSpan.addSpan(new Span(115, 120));
		annotations.add(proteinWithDiscontinuousSpan);

		TextDocument td = new TextDocument("12345", "PMC", "this is where the document text goes");
		td.setAnnotations(annotations);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new BioNLPDocumentWriter().serialize(td, outputStream, CharacterEncoding.UTF_8);
		String serializedAnnotations = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName());
		// .replaceAll("\\n", " ");

		String expectedSerializedAnnotations = ("T1\tProtein 0 5\tBRCA2\n" + "T2\tGene 55 60\tPPARD\n"
				+ "T3\tGene 100 104;115 120\tPPAR gamma\n");// .replaceAll("\\n",
															// " ");

		System.out.println("SER:\n" + serializedAnnotations + ";;;");
		System.out.println("EXP:\n" + expectedSerializedAnnotations + ";;;");

		assertEquals("annotations in serialized BioNLP format not as expected", expectedSerializedAnnotations,
				serializedAnnotations);

	}

	@Test
	public void testBioNLPAnotationSerialization_withRelation() throws IOException {
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 5, "BRCA2", new DefaultClassMention("Protein")));
		TextAnnotation ppardAnnot1 = factory.createAnnotation(55, 60, "PPARD", new DefaultClassMention("Gene"));
		annotations.add(ppardAnnot1);

		TextAnnotation ppardAnnot2 = factory.createAnnotation(100, 104, "PPAR gamma", new DefaultClassMention("Gene"));
		ppardAnnot2.addSpan(new Span(115, 120));
		annotations.add(ppardAnnot2);

		/* link the PPARD annotations with a relation (type = identity chain) */
		String relationType = "related";
		ComplexSlotMention csm = new DefaultComplexSlotMention(relationType);
		csm.addClassMention(ppardAnnot2.getClassMention());
		ppardAnnot1.getClassMention().addComplexSlotMention(csm);

		TextDocument td = new TextDocument("12345", "PMC", "this is where the document text goes");
		td.setAnnotations(annotations);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new BioNLPDocumentWriter().serialize(td, outputStream, CharacterEncoding.UTF_8);
		String serializedAnnotations = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName());

		String expectedSerializedAnnotations = ("T1\tProtein 0 5\tBRCA2\n" + "T2\tGene 55 60\tPPARD\n"
				+ "T3\tGene 100 104;115 120\tPPAR gamma\n" + "R1\t" + relationType + " Arg1:T2 Arg2:T3\n");

		assertEquals("annotations in serialized BioNLP format not as expected", expectedSerializedAnnotations,
				serializedAnnotations);

	}

}
