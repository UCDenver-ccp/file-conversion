package edu.ucdenver.ccp.file.conversion.conllu;

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

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultComplexSlotMention;

public class CoNLLUDocumentWriterTest {

	@Test
	public void testCoNLLUAnnotationSerialization() throws IOException {
		// 0123456789012
		String sentenceText = "The red car.";
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 3, "The", new DefaultClassMention("DT")));
		annotations.add(factory.createAnnotation(4, 7, "red", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(8, 11, "car", new DefaultClassMention("NN")));
		annotations.add(factory.createAnnotation(11, 12, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(0, 12, "The red car.", new DefaultClassMention("sentence")));

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		CoNLLUDocumentWriter.serializeAnnotations(annotations, outputStream, CharacterEncoding.UTF_8);
		String serializedAnnotations = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName())
				.replaceAll("\\n", " ");

		String expectedSerializedAnnotations = ("1\tThe\t_\tDT\t_\t_\t_\t_\t_\t_\n"
				+ "2\tred\t_\tJJ\t_\t_\t_\t_\t_\t_\n" + "3\tcar\t_\tNN\t_\t_\t_\t_\t_\t_\n"
				+ "4\t.\t_\t.\t_\t_\t_\t_\t_\t_\n" + "\n").replaceAll("\\n", " ");
		;

		System.out.println("SER:\n" + serializedAnnotations + ";;;");
		System.out.println("EXP:\n" + expectedSerializedAnnotations + ";;;");

		assertEquals("annotations in serialized CoNLL-U format not as expected", expectedSerializedAnnotations,
				serializedAnnotations);

	}

	@Test
	public void testCoNLLUAnnotationSerialization_withDependencyRelations() throws IOException {
		// 0123456789012
		String sentenceText = "The red car.";
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		DefaultClassMention cm_the = new DefaultClassMention("DT");
		DefaultClassMention cm_red = new DefaultClassMention("JJ");
		DefaultClassMention cm_car = new DefaultClassMention("NN");
		DefaultClassMention cm_period = new DefaultClassMention(".");

		/*
		 * Note, these dependency relations may not be actually correct from a
		 * linguistic standpoint; doesn't matter for this unit test
		 */
		DefaultComplexSlotMention csm_red = new DefaultComplexSlotMention("NMOD");
		csm_red.addClassMention(cm_car);
		cm_red.addComplexSlotMention(csm_red);

		DefaultComplexSlotMention csm_the = new DefaultComplexSlotMention("NMOD");
		csm_the.addClassMention(cm_car);
		cm_the.addComplexSlotMention(csm_the);

		DefaultComplexSlotMention csm_period = new DefaultComplexSlotMention("P");
		csm_period.addClassMention(cm_car);
		cm_period.addComplexSlotMention(csm_period);

		annotations.add(factory.createAnnotation(0, 3, "The", cm_the));
		annotations.add(factory.createAnnotation(4, 7, "red", cm_red));
		annotations.add(factory.createAnnotation(8, 11, "car", cm_car));
		annotations.add(factory.createAnnotation(11, 12, ".", cm_period));
		annotations.add(factory.createAnnotation(0, 12, "The red car.", new DefaultClassMention("sentence")));

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		CoNLLUDocumentWriter.serializeAnnotations(annotations, outputStream, CharacterEncoding.UTF_8);
		String serializedAnnotations = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName());

		String expectedSerializedAnnotations = ("1\tThe\t_\tDT\t_\t_\t3\tNMOD\t_\t_\n"
				+ "2\tred\t_\tJJ\t_\t_\t3\tNMOD\t_\t_\n" + "3\tcar\t_\tNN\t_\t_\t0\tROOT\t_\t_\n"
				+ "4\t.\t_\t.\t_\t_\t3\tP\t_\t_\n" + "\n");

		System.out.println("SER:\n" + serializedAnnotations + ";;;");
		System.out.println("EXP:\n" + expectedSerializedAnnotations + ";;;");

		assertEquals("annotations in serialized CoNLL-U format not as expected", expectedSerializedAnnotations,
				serializedAnnotations);

	}

}
