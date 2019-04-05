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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.collections.CollectionsUtil.SortOrder;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileComparisonUtil;
import edu.ucdenver.ccp.common.file.FileComparisonUtil.ColumnOrder;
import edu.ucdenver.ccp.common.file.FileComparisonUtil.LineOrder;
import edu.ucdenver.ccp.common.file.FileComparisonUtil.LineTrim;
import edu.ucdenver.ccp.common.file.FileComparisonUtil.ShowWhiteSpace;
import edu.ucdenver.ccp.common.file.FileReaderUtil;
import edu.ucdenver.ccp.common.io.ClassPathUtil;
import edu.ucdenver.ccp.common.io.StreamUtil;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.file.conversion.conllu.CoNLLUDocumentWriter;
import edu.ucdenver.ccp.file.conversion.conllu.CoNLLUFileRecord;
import edu.ucdenver.ccp.file.conversion.knowtator.KnowtatorDocumentReader;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.ClassMention;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultComplexSlotMention;

public class CoNLLCoref2012DocumentWriterTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void testDocumentWriter() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		String documentText = "The red car. It is fast.";
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 3, "The", new DefaultClassMention("DT")));
		annotations.add(factory.createAnnotation(4, 7, "red", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(8, 11, "car", new DefaultClassMention("NN")));
		annotations.add(factory.createAnnotation(11, 12, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(0, 12, "The red car.", new DefaultClassMention("sentence")));
		annotations.add(factory.createAnnotation(13, 15, "It", new DefaultClassMention("PRN")));
		annotations.add(factory.createAnnotation(16, 18, "is", new DefaultClassMention("VB")));
		annotations.add(factory.createAnnotation(19, 23, "fast", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(23, 24, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(13, 24, "It is fast.", new DefaultClassMention("sentence")));

		TextAnnotation identChainAnnot = factory.createAnnotation(8, 11, "car",
				new DefaultClassMention("Identity Chain"));
		TextAnnotation carNPAnnot = factory.createAnnotation(8, 11, "car", new DefaultClassMention("Noun Phrase"));
		TextAnnotation itNPAnnot = factory.createAnnotation(13, 15, "It", new DefaultClassMention("Noun Phrase"));

		/* form an identity chain of 'car' and 'It' */
		ComplexSlotMention csm = new DefaultComplexSlotMention("Coreferring strings");
		csm.addClassMention(carNPAnnot.getClassMention());
		csm.addClassMention(itNPAnnot.getClassMention());
		identChainAnnot.getClassMention().addComplexSlotMention(csm);

		annotations.add(identChainAnnot);
		annotations.add(carNPAnnot);
		annotations.add(itNPAnnot);

		TextDocument td = new TextDocument("12345", "PMC", documentText);
		td.addAnnotations(annotations);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new CoNLLCoref2012DocumentWriter().serialize(td, outputStream, encoding);
		String serializedConllText = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName());

		String expectedConllText = "#begin document (12345); part 000\n"
				+ "12345\t0\t1\tThe\tDT\t-\t-\t-\t-\t-\t-\t-\t-\n" + "12345\t0\t2\tred\tJJ\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "12345\t0\t3\tcar\tNN\t-\t-\t-\t-\t-\t-\t-\t(1)\n" + "12345\t0\t4\t.\t.\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "\n" + "12345\t0\t1\tIt\tPRN\t-\t-\t-\t-\t-\t-\t-\t(1)\n"
				+ "12345\t0\t2\tis\tVB\t-\t-\t-\t-\t-\t-\t-\t-\n" + "12345\t0\t3\tfast\tJJ\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "12345\t0\t4\t.\t.\t-\t-\t-\t-\t-\t-\t-\t-\n" + "\n";

		// System.out.println("SER:\n" + serializedConllText + ";;;");
		// System.out.println("EXP:\n" + expectedConllText + ";;;");

		assertEquals("annotations in serialized CoNLL-U format not as expected", expectedConllText,
				serializedConllText);

	}

	@Test
	public void testDocumentWriter_testTwoEndsOfSameChainAtSameToken() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		String documentText = "The red car. It is fast.";
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 3, "The", new DefaultClassMention("DT")));
		annotations.add(factory.createAnnotation(4, 7, "red", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(8, 11, "car", new DefaultClassMention("NN")));
		annotations.add(factory.createAnnotation(11, 12, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(0, 12, "The red car.", new DefaultClassMention("sentence")));
		annotations.add(factory.createAnnotation(13, 15, "It", new DefaultClassMention("PRN")));
		annotations.add(factory.createAnnotation(16, 18, "is", new DefaultClassMention("VB")));
		annotations.add(factory.createAnnotation(19, 23, "fast", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(23, 24, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(13, 24, "It is fast.", new DefaultClassMention("sentence")));

		TextAnnotation identChainAnnot = factory.createAnnotation(4, 11, "red car",
				new DefaultClassMention("Identity Chain"));
		TextAnnotation carNPAnnot = factory.createAnnotation(4, 11, "red car", new DefaultClassMention("Noun Phrase"));
		TextAnnotation carNPAnnot2 = factory.createAnnotation(8, 11, "car", new DefaultClassMention("Noun Phrase"));
		TextAnnotation itNPAnnot = factory.createAnnotation(13, 15, "It", new DefaultClassMention("Noun Phrase"));

		/* form an identity chain of 'car' and 'It' */
		ComplexSlotMention csm = new DefaultComplexSlotMention("Coreferring strings");
		csm.addClassMention(carNPAnnot.getClassMention());
		csm.addClassMention(carNPAnnot2.getClassMention());
		csm.addClassMention(itNPAnnot.getClassMention());
		identChainAnnot.getClassMention().addComplexSlotMention(csm);

		annotations.add(identChainAnnot);
		annotations.add(carNPAnnot);
		annotations.add(itNPAnnot);

		TextDocument td = new TextDocument("12345", "PMC", documentText);
		td.addAnnotations(annotations);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new CoNLLCoref2012DocumentWriter().serialize(td, outputStream, encoding);
		String serializedConllText = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName());

		String expectedConllText = "#begin document (12345); part 000\n"
				+ "12345\t0\t1\tThe\tDT\t-\t-\t-\t-\t-\t-\t-\t-\n" + "12345\t0\t2\tred\tJJ\t-\t-\t-\t-\t-\t-\t-\t(1\n"
				+ "12345\t0\t3\tcar\tNN\t-\t-\t-\t-\t-\t-\t-\t(1)|1)\n" + "12345\t0\t4\t.\t.\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "\n" + "12345\t0\t1\tIt\tPRN\t-\t-\t-\t-\t-\t-\t-\t(1)\n"
				+ "12345\t0\t2\tis\tVB\t-\t-\t-\t-\t-\t-\t-\t-\n" + "12345\t0\t3\tfast\tJJ\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "12345\t0\t4\t.\t.\t-\t-\t-\t-\t-\t-\t-\t-\n" + "\n";

		// System.out.println("SER:\n" + serializedConllText + ";;;");
		// System.out.println("EXP:\n" + expectedConllText + ";;;");

		assertEquals("annotations in serialized CoNLL-U format not as expected", expectedConllText,
				serializedConllText);

	}

	/**
	 * Some annotations may have excess leading or trailing whitespace. This test checks to see that
	 * it is removed.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testDocumentWriter_removeExcessWhitespace() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		String documentText = "The red car. It is fast.";
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 3, "The", new DefaultClassMention("DT")));
		annotations.add(factory.createAnnotation(4, 8, "red ", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(7, 11, " car", new DefaultClassMention("NN")));
		annotations.add(factory.createAnnotation(11, 12, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(0, 12, "The red car.", new DefaultClassMention("sentence")));
		annotations.add(factory.createAnnotation(13, 15, "It", new DefaultClassMention("PRN")));
		annotations.add(factory.createAnnotation(16, 18, "is", new DefaultClassMention("VB")));
		annotations.add(factory.createAnnotation(19, 23, "fast", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(23, 24, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(13, 24, "It is fast.", new DefaultClassMention("sentence")));

		TextAnnotation identChainAnnot = factory.createAnnotation(8, 11, " car",
				new DefaultClassMention("Identity Chain"));
		TextAnnotation carNPAnnot = factory.createAnnotation(8, 11, " car", new DefaultClassMention("Noun Phrase"));
		TextAnnotation itNPAnnot = factory.createAnnotation(12, 15, " It", new DefaultClassMention("Noun Phrase"));
		TextAnnotation blankNPAnnot = factory.createAnnotation(3, 4, " ", new DefaultClassMention("Noun Phrase"));

		/* form an identity chain of 'car' and 'It' */
		ComplexSlotMention csm = new DefaultComplexSlotMention("Coreferring strings");
		csm.addClassMention(carNPAnnot.getClassMention());
		csm.addClassMention(itNPAnnot.getClassMention());
		identChainAnnot.getClassMention().addComplexSlotMention(csm);

		annotations.add(identChainAnnot);
		annotations.add(carNPAnnot);
		annotations.add(itNPAnnot);
		annotations.add(blankNPAnnot);

		TextDocument td = new TextDocument("12345", "PMC", documentText);
		td.addAnnotations(annotations);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new CoNLLCoref2012DocumentWriter().serialize(td, outputStream, encoding);
		String serializedConllText = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName());

		String expectedConllText = "#begin document (12345); part 000\n"
				+ "12345\t0\t1\tThe\tDT\t-\t-\t-\t-\t-\t-\t-\t-\n" + "12345\t0\t2\tred\tJJ\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "12345\t0\t3\tcar\tNN\t-\t-\t-\t-\t-\t-\t-\t(1)\n" + "12345\t0\t4\t.\t.\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "\n" + "12345\t0\t1\tIt\tPRN\t-\t-\t-\t-\t-\t-\t-\t(1)\n"
				+ "12345\t0\t2\tis\tVB\t-\t-\t-\t-\t-\t-\t-\t-\n" + "12345\t0\t3\tfast\tJJ\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "12345\t0\t4\t.\t.\t-\t-\t-\t-\t-\t-\t-\t-\n" + "\n";

		// System.out.println("SER:\n" + serializedConllText + ";;;");
		// System.out.println("EXP:\n" + expectedConllText + ";;;");

		assertEquals("annotations in serialized CoNLL-U format not as expected", expectedConllText,
				serializedConllText);

	}

	/**
	 * sometimes the NP span start might not match a token exactly. In that case, we should mark the
	 * overlapping token as part of the coref chain in the CoNLL coref output format. Example: token
	 * = "post-measurement"; coref chain member = "measurement"
	 * 
	 * @throws IOException
	 */
	@Test
	public void testDocumentWriter_npStartSpanMismatch() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		String documentText = "The red car. It is fast.";
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 3, "The", new DefaultClassMention("DT")));
		annotations.add(factory.createAnnotation(4, 7, "red", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(8, 11, "car", new DefaultClassMention("NN")));
		annotations.add(factory.createAnnotation(11, 12, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(0, 12, "The red car.", new DefaultClassMention("sentence")));
		annotations.add(factory.createAnnotation(13, 15, "It", new DefaultClassMention("PRN")));
		annotations.add(factory.createAnnotation(16, 18, "is", new DefaultClassMention("VB")));
		annotations.add(factory.createAnnotation(19, 23, "fast", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(23, 24, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(13, 24, "It is fast.", new DefaultClassMention("sentence")));

		/*
		 * to simulate a token mismatch, this chain member annotation is "ar" instead of "car"
		 */
		TextAnnotation identChainAnnot = factory.createAnnotation(9, 11, "ar",
				new DefaultClassMention("Identity Chain"));
		TextAnnotation carNPAnnot = factory.createAnnotation(9, 11, "ar", new DefaultClassMention("Noun Phrase"));
		TextAnnotation itNPAnnot = factory.createAnnotation(13, 15, "It", new DefaultClassMention("Noun Phrase"));

		/* form an identity chain of 'car' and 'It' */
		ComplexSlotMention csm = new DefaultComplexSlotMention("Coreferring strings");
		csm.addClassMention(carNPAnnot.getClassMention());
		csm.addClassMention(itNPAnnot.getClassMention());
		identChainAnnot.getClassMention().addComplexSlotMention(csm);

		annotations.add(identChainAnnot);
		annotations.add(carNPAnnot);
		annotations.add(itNPAnnot);

		TextDocument td = new TextDocument("12345", "PMC", documentText);
		td.addAnnotations(annotations);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new CoNLLCoref2012DocumentWriter().serialize(td, outputStream, encoding);
		String serializedConllText = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName());

		String expectedConllText = "#begin document (12345); part 000\n"
				+ "12345\t0\t1\tThe\tDT\t-\t-\t-\t-\t-\t-\t-\t-\n" + "12345\t0\t2\tred\tJJ\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "12345\t0\t3\tcar\tNN\t-\t-\t-\t-\t-\t-\t-\t(1)\n" + "12345\t0\t4\t.\t.\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "\n" + "12345\t0\t1\tIt\tPRN\t-\t-\t-\t-\t-\t-\t-\t(1)\n"
				+ "12345\t0\t2\tis\tVB\t-\t-\t-\t-\t-\t-\t-\t-\n" + "12345\t0\t3\tfast\tJJ\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "12345\t0\t4\t.\t.\t-\t-\t-\t-\t-\t-\t-\t-\n" + "\n";

		// System.out.println("SER:\n" + serializedConllText + ";;;");
		// System.out.println("EXP:\n" + expectedConllText + ";;;");

		assertEquals("annotations in serialized CoNLL-U format not as expected", expectedConllText,
				serializedConllText);

	}

	/**
	 * sometimes the NP span end might not match a token exactly. In that case, we should mark the
	 * overlapping token as part of the coref chain in the CoNLL coref output format. Example:
	 * tokens = "C-terminal peptides"; coref chain member = "C-terminal peptide"
	 * 
	 * @throws IOException
	 */
	@Test
	public void testDocumentWriter_npEndSpanMismatch() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		String documentText = "The red car. It is fast.";
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 3, "The", new DefaultClassMention("DT")));
		annotations.add(factory.createAnnotation(4, 7, "red", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(8, 11, "car", new DefaultClassMention("NN")));
		annotations.add(factory.createAnnotation(11, 12, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(0, 12, "The red car.", new DefaultClassMention("sentence")));
		annotations.add(factory.createAnnotation(13, 15, "It", new DefaultClassMention("PRN")));
		annotations.add(factory.createAnnotation(16, 18, "is", new DefaultClassMention("VB")));
		annotations.add(factory.createAnnotation(19, 23, "fast", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(23, 24, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(13, 24, "It is fast.", new DefaultClassMention("sentence")));

		TextAnnotation identChainAnnot = factory.createAnnotation(8, 11, "car",
				new DefaultClassMention("Identity Chain"));
		TextAnnotation carNPAnnot = factory.createAnnotation(8, 11, "car", new DefaultClassMention("Noun Phrase"));
		/*
		 * to simulate a token mismatch, this chain member annotation is "I" instead of "It"
		 */
		TextAnnotation itNPAnnot = factory.createAnnotation(13, 14, "I", new DefaultClassMention("Noun Phrase"));

		/* form an identity chain of 'car' and 'It' */
		ComplexSlotMention csm = new DefaultComplexSlotMention("Coreferring strings");
		csm.addClassMention(carNPAnnot.getClassMention());
		csm.addClassMention(itNPAnnot.getClassMention());
		identChainAnnot.getClassMention().addComplexSlotMention(csm);

		annotations.add(identChainAnnot);
		annotations.add(carNPAnnot);
		annotations.add(itNPAnnot);

		TextDocument td = new TextDocument("12345", "PMC", documentText);
		td.addAnnotations(annotations);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new CoNLLCoref2012DocumentWriter().serialize(td, outputStream, encoding);
		String serializedConllText = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName());

		String expectedConllText = "#begin document (12345); part 000\n"
				+ "12345\t0\t1\tThe\tDT\t-\t-\t-\t-\t-\t-\t-\t-\n" + "12345\t0\t2\tred\tJJ\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "12345\t0\t3\tcar\tNN\t-\t-\t-\t-\t-\t-\t-\t(1)\n" + "12345\t0\t4\t.\t.\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "\n" + "12345\t0\t1\tIt\tPRN\t-\t-\t-\t-\t-\t-\t-\t(1)\n"
				+ "12345\t0\t2\tis\tVB\t-\t-\t-\t-\t-\t-\t-\t-\n" + "12345\t0\t3\tfast\tJJ\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "12345\t0\t4\t.\t.\t-\t-\t-\t-\t-\t-\t-\t-\n" + "\n";

		// System.out.println("SER:\n" + serializedConllText + ";;;");
		// System.out.println("EXP:\n" + expectedConllText + ";;;");

		assertEquals("annotations in serialized CoNLL-U format not as expected", expectedConllText,
				serializedConllText);

	}

	/**
	 * sometimes the NP span end might not match a token exactly. In that case, we should mark the
	 * overlapping token as part of the coref chain in the CoNLL coref output format. Example:
	 * tokens = "C-terminal peptides"; coref chain member = "C-terminal peptide"
	 * 
	 * @throws IOException
	 */
	@Test
	public void testDocumentWriter_npEndSpanMismatch_multiword_np() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		String documentText = "The red car. It is fast.";
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 3, "The", new DefaultClassMention("DT")));
		annotations.add(factory.createAnnotation(4, 7, "red", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(8, 11, "car", new DefaultClassMention("NN")));
		annotations.add(factory.createAnnotation(11, 12, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(0, 12, "The red car.", new DefaultClassMention("sentence")));
		annotations.add(factory.createAnnotation(13, 15, "It", new DefaultClassMention("PRN")));
		annotations.add(factory.createAnnotation(16, 18, "is", new DefaultClassMention("VB")));
		annotations.add(factory.createAnnotation(19, 23, "fast", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(23, 24, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(13, 24, "It is fast.", new DefaultClassMention("sentence")));

		TextAnnotation identChainAnnot = factory.createAnnotation(4, 10, "red ca",
				new DefaultClassMention("Identity Chain"));
		TextAnnotation carNPAnnot = factory.createAnnotation(4, 10, "red ca", new DefaultClassMention("Noun Phrase"));
		/*
		 * to simulate a token mismatch, this chain member annotation is "I" instead of "It"
		 */
		TextAnnotation itNPAnnot = factory.createAnnotation(13, 14, "I", new DefaultClassMention("Noun Phrase"));

		/* form an identity chain of 'car' and 'It' */
		ComplexSlotMention csm = new DefaultComplexSlotMention("Coreferring strings");
		csm.addClassMention(carNPAnnot.getClassMention());
		csm.addClassMention(itNPAnnot.getClassMention());
		identChainAnnot.getClassMention().addComplexSlotMention(csm);

		annotations.add(identChainAnnot);
		annotations.add(carNPAnnot);
		annotations.add(itNPAnnot);

		TextDocument td = new TextDocument("12345", "PMC", documentText);
		td.addAnnotations(annotations);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new CoNLLCoref2012DocumentWriter().serialize(td, outputStream, encoding);
		String serializedConllText = outputStream.toString(CharacterEncoding.UTF_8.getCharacterSetName());

		String expectedConllText = "#begin document (12345); part 000\n"
				+ "12345\t0\t1\tThe\tDT\t-\t-\t-\t-\t-\t-\t-\t-\n" + "12345\t0\t2\tred\tJJ\t-\t-\t-\t-\t-\t-\t-\t(1\n"
				+ "12345\t0\t3\tcar\tNN\t-\t-\t-\t-\t-\t-\t-\t1)\n" + "12345\t0\t4\t.\t.\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "\n" + "12345\t0\t1\tIt\tPRN\t-\t-\t-\t-\t-\t-\t-\t(1)\n"
				+ "12345\t0\t2\tis\tVB\t-\t-\t-\t-\t-\t-\t-\t-\n" + "12345\t0\t3\tfast\tJJ\t-\t-\t-\t-\t-\t-\t-\t-\n"
				+ "12345\t0\t4\t.\t.\t-\t-\t-\t-\t-\t-\t-\t-\n" + "\n";

		// System.out.println("SER:\n" + serializedConllText + ";;;");
		// System.out.println("EXP:\n" + expectedConllText + ";;;");

		assertEquals("annotations in serialized CoNLL-U format not as expected", expectedConllText,
				serializedConllText);

	}

	@Test
	public void findOverlappingTokenTest() {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		String documentText = "The red car. It is fast.";
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 3, "The", new DefaultClassMention("DT")));
		annotations.add(factory.createAnnotation(4, 7, "red", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(8, 11, "car", new DefaultClassMention("NN")));
		annotations.add(factory.createAnnotation(11, 12, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(0, 12, "The red car.", new DefaultClassMention("sentence")));
		annotations.add(factory.createAnnotation(13, 15, "It", new DefaultClassMention("PRN")));
		annotations.add(factory.createAnnotation(16, 18, "is", new DefaultClassMention("VB")));
		annotations.add(factory.createAnnotation(19, 23, "fast", new DefaultClassMention("JJ")));
		annotations.add(factory.createAnnotation(23, 24, ".", new DefaultClassMention(".")));
		annotations.add(factory.createAnnotation(13, 24, "It is fast.", new DefaultClassMention("sentence")));

		List<CoNLLUFileRecord> records = CoNLLUDocumentWriter.generateRecords(annotations);

		Map<Integer, CoNLLUFileRecord> tokenStartIndexToRecordMap = new HashMap<Integer, CoNLLUFileRecord>();
		Map<Integer, CoNLLUFileRecord> tokenEndIndexToRecordMap = new HashMap<Integer, CoNLLUFileRecord>();

		CoNLLCoref2012DocumentWriter.populateTokenIndexToRecordMaps(records, tokenStartIndexToRecordMap,
				tokenEndIndexToRecordMap);

		Map<Integer, CoNLLUFileRecord> sortedTokenStartIndexToRecordMap = CollectionsUtil
				.sortMapByKeys(tokenStartIndexToRecordMap, SortOrder.ASCENDING);
		Map<Integer, CoNLLUFileRecord> sortedTokenEndIndexToRecordMap = CollectionsUtil
				.sortMapByKeys(tokenEndIndexToRecordMap, SortOrder.ASCENDING);

		/* test starts */
		assertEquals("if np start is 5, then the overlapping record should be for the 'red' token.", "red",
				CoNLLCoref2012DocumentWriter.findOverlappingStartToken(sortedTokenStartIndexToRecordMap, 5).getForm());

		assertEquals("if np start is 22, then the overlapping record should be for the 'fast' token.", "fast",
				CoNLLCoref2012DocumentWriter.findOverlappingStartToken(sortedTokenStartIndexToRecordMap, 22).getForm());

		/* test ends */
		assertEquals("if np end is 22, then the overlapping record should be for the 'fast' token.", "fast",
				CoNLLCoref2012DocumentWriter.findOverlappingEndToken(sortedTokenEndIndexToRecordMap, 22).getForm());

	}

	@Test
	public void testFormCorefInfoString() {

		String startIndicator = CoNLLCoref2012DocumentWriter.START_STATUS_INDICATOR;
		String endIndicator = CoNLLCoref2012DocumentWriter.END_STATUS_INDICATOR;

		String misc = "SPAN_0|15;" + startIndicator + "_9";
		assertEquals("(9", CoNLLCoref2012DocumentWriter.formCorefInfoString(misc));

		misc = "SPAN_0|15;" + endIndicator + "_9";
		assertEquals("9)", CoNLLCoref2012DocumentWriter.formCorefInfoString(misc));

		misc = "SPAN_0|15;" + endIndicator + "_9;" + startIndicator + "_9";
		assertEquals("(9)", CoNLLCoref2012DocumentWriter.formCorefInfoString(misc));

		misc = "SPAN_0|15;" + endIndicator + "_9;" + startIndicator + "_9;" + startIndicator + "_23";
		assertEquals("(23|(9)", CoNLLCoref2012DocumentWriter.formCorefInfoString(misc));

		misc = "SPAN_0|15;" + endIndicator + "_9;" + startIndicator + "_9;" + endIndicator + "_23";
		assertEquals("(9)|23)", CoNLLCoref2012DocumentWriter.formCorefInfoString(misc));

		misc = "SPAN_0|15;" + endIndicator + "_9;" + endIndicator + "_23";
		String corefInfoString = CoNLLCoref2012DocumentWriter.formCorefInfoString(misc);
		// return order is non-deterministic so check for either case
		assertTrue(corefInfoString.equals("9)|23)") || corefInfoString.equals("23)|9)"));

		misc = "SPAN_0|15;" + startIndicator + "_23;" + startIndicator + "_9";
		corefInfoString = CoNLLCoref2012DocumentWriter.formCorefInfoString(misc);
		// return order is non-deterministic so check for either case
		assertTrue(corefInfoString.equals("(23|(9") || corefInfoString.equals("(9|(23"));

		// multiple ends for same chain on same token
		misc = "SPAN_0|15;" + endIndicator + "_9;" + startIndicator + "_9;" + endIndicator + "_9";
		assertEquals("(9)|9)", CoNLLCoref2012DocumentWriter.formCorefInfoString(misc));

		// multiple ends for same chain on same token
		misc = "SPAN_0|15;" + endIndicator + "_9;" + endIndicator + "_9";
		assertEquals("9)|9)", CoNLLCoref2012DocumentWriter.formCorefInfoString(misc));
	}

	@Test(expected = IllegalStateException.class)
	public void testFormCorefInfoString_throwsExceptionForIdentWithMultipleChainMembersForSingleToken() {
		String startIndicator = CoNLLCoref2012DocumentWriter.START_STATUS_INDICATOR;
		String endIndicator = CoNLLCoref2012DocumentWriter.END_STATUS_INDICATOR;

		String misc = "SPAN_0|15;" + startIndicator + "_23;" + startIndicator + "_9;" + endIndicator + "_23;"
				+ endIndicator + "_9";
		CoNLLCoref2012DocumentWriter.formCorefInfoString(misc);
	}

	/**
	 * In cases where there are chains that share member annotations, test that they get merged
	 * properly, e.g. [A,B,C,D] and [B,E] get merged to [A,B,C,D,E]
	 * 
	 * @throws IOException
	 */
	@Test
	public void testDocumentWriter_merge2ConnectedChains() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		InputStream conllStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(),
				"sample-craft.connected_chains.ident.conll");
		InputStream documentTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.txt");

		TextDocument td = new CoNLLCoref2012DocumentReader().readDocument("11532192", "PMID", conllStream,
				documentTextStream, encoding);

		/*
		 * The (1) chain has been broken into two pieces, (1) and (6). We will test if these get
		 * merged properly
		 */

		assertEquals(
				"Expected 132 (now 134) annotations, 101 tokens + 7 sentences + 19 (now 20) noun phrase + 5 (now 6) identity chain",
				134, td.getAnnotations().size());

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

		assertEquals("Expect 19 (now 20) noun phrase annotations", 20, npCount);
		assertEquals("Expect 5 (now 6) IDENTITY chain annotations", 6, identCount);

		// check the IOP ident chain
		for (TextAnnotation ta : td.getAnnotations()) {
			String type = ta.getClassMention().getMentionName();
			if (type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)) {
				// 1
				if (ta.getCoveredText().equals("Intraocular pressure")) {
					ComplexSlotMention csm = ta.getClassMention().getComplexSlotMentionByName(
							CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
					assertEquals("There should be 7 chain members for the IOP chain.", 5,
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
					assertEquals("There should be 3 chain members for the genetic factors chain.", 3,
							csm.getClassMentions().size());
				}
				// 5
				if (ta.getCoveredText().equals("genetically distinct mouse strains")) {
					ComplexSlotMention csm = ta.getClassMention().getComplexSlotMentionByName(
							CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
					assertEquals("There should be 3 chain members for the mouse strain chain.", 3,
							csm.getClassMentions().size());
				}
				// 6
				if (ta.getCoveredText().equals("IOP")) {
					ComplexSlotMention csm = ta.getClassMention().getComplexSlotMentionByName(
							CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
					assertEquals(
							"There should be 3 chain members for the IOP chain (this is the chain that was split from (1)).",
							3, csm.getClassMentions().size());
				}
			}
		}

		/*
		 * so we have confirmed at this point that there is are 6 chains, where chains (1) & (6)
		 * should really be combined b/c they overlap with one chain member. Now let's write the
		 * chains using the CoNLLCoref2012DocumentWriter and see if the chains get consolidated.
		 */

		File outputFile = folder.newFile("sample.conll");
		new CoNLLCoref2012DocumentWriter().serialize(td, outputFile, encoding);

		/*
		 * now load the output file and check that they chains have the correct number of members
		 */
		documentTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.txt");

		td = new CoNLLCoref2012DocumentReader().readDocument("11532192", "PMID", new FileInputStream(outputFile),
				documentTextStream, encoding);

		assertEquals("Expected 130 annotations, 101 tokens + 7 sentences + 19 noun phrase + 5 identity chain", 132,
				td.getAnnotations().size());

		npCount = 0;
		identCount = 0;

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

		/*
		 * expected lines come from the properly formatted file with only 5 chains
		 */
		List<String> expectedLines = FileReaderUtil.loadLinesFromFile(
				ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.ident.conll"), encoding);
		List<String> observedLines = FileReaderUtil.loadLinesFromFile(outputFile, encoding);

		assertTrue(FileComparisonUtil.hasExpectedLines(observedLines, expectedLines, null, LineOrder.AS_IN_FILE,
				ColumnOrder.AS_IN_FILE, LineTrim.OFF, ShowWhiteSpace.OFF));
	}

	/**
	 * In cases where there are chains that share member annotations, test that they get merged
	 * properly. This test tests that a 3-way merge is possible, e.g. [A,B,C,D] and [B,E] and [E,F]
	 * get merged to [A,B,C,D,E,F]
	 * 
	 * @throws IOException
	 */
	@Test
	public void testDocumentWriter_merge3ConnectedChains() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		InputStream conllStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(),
				"sample-craft.connected_chains2.ident.conll");
		InputStream documentTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.txt");

		TextDocument td = new CoNLLCoref2012DocumentReader().readDocument("11532192", "PMID", conllStream,
				documentTextStream, encoding);

		/*
		 * The (1) chain has been broken into three pieces, (1), (6), and (7). We will test if these
		 * get merged properly
		 */

		assertEquals(
				"Expected 132 (now 136) annotations, 101 tokens + 7 sentences + 19 (now 21) noun phrase + 5 (now 7) identity chain",
				136, td.getAnnotations().size());

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

		assertEquals("Expect 19 (now 21) noun phrase annotations", 21, npCount);
		assertEquals("Expect 5 (now 7) IDENTITY chain annotations", 7, identCount);

		// check the IOP ident chain
		for (TextAnnotation ta : td.getAnnotations()) {
			String type = ta.getClassMention().getMentionName();
			if (type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)) {
				// 1
				if (ta.getCoveredText().equals("Intraocular pressure")) {
					ComplexSlotMention csm = ta.getClassMention().getComplexSlotMentionByName(
							CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
					assertEquals("There should be 7 chain members for the IOP chain.", 5,
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
					assertEquals("There should be 3 chain members for the genetic factors chain.", 3,
							csm.getClassMentions().size());
				}
				// 5
				if (ta.getCoveredText().equals("genetically distinct mouse strains")) {
					ComplexSlotMention csm = ta.getClassMention().getComplexSlotMentionByName(
							CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
					assertEquals("There should be 3 chain members for the mouse strain chain.", 3,
							csm.getClassMentions().size());
				}
				// 6 & 7 are the same: IOP with 2 members
				if (ta.getCoveredText().equals("IOP")) {
					ComplexSlotMention csm = ta.getClassMention().getComplexSlotMentionByName(
							CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
					assertEquals(
							"There should be 2 chain members for the IOP chain (this is the chain that was split from (1)).",
							2, csm.getClassMentions().size());
				}
			}
		}

		/*
		 * so we have confirmed at this point that there is are 7 chains, where chains (1), (6), &
		 * (7) should really be combined b/c they overlap with one chain member, i.e. (1) and (6)
		 * overlap by one member and (6) and (7) overlap by one member. Now let's write the chains
		 * using the CoNLLCoref2012DocumentWriter and see if the chains get consolidated.
		 */

		File outputFile = folder.newFile("sample.conll");
		new CoNLLCoref2012DocumentWriter().serialize(td, outputFile, encoding);

		/*
		 * now load the output file and check that they chains have the correct number of members
		 */
		documentTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.txt");

		td = new CoNLLCoref2012DocumentReader().readDocument("11532192", "PMID", new FileInputStream(outputFile),
				documentTextStream, encoding);

		assertEquals("Expected 130 annotations, 101 tokens + 7 sentences + 19 noun phrase + 5 identity chain", 132,
				td.getAnnotations().size());

		npCount = 0;
		identCount = 0;

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

		/*
		 * expected lines come from the properly formatted file with only 5 chains
		 */
		List<String> expectedLines = FileReaderUtil.loadLinesFromFile(
				ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.ident.conll"), encoding);
		List<String> observedLines = FileReaderUtil.loadLinesFromFile(outputFile, encoding);

		assertTrue(FileComparisonUtil.hasExpectedLines(observedLines, expectedLines, null, LineOrder.AS_IN_FILE,
				ColumnOrder.AS_IN_FILE, LineTrim.OFF, ShowWhiteSpace.OFF));
	}

	@Test
	public void testRedundantChainsInOutput() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		File docTextFile = new File(
				"/Users/bill/Dropbox/work/projects/craft-shared-task-2019/CRAFT.bill.git/articles/txt/15314659.txt");
		TextDocument td = new KnowtatorDocumentReader().readDocument("15314659", "PMC",
				new File(
						"/Users/bill/Dropbox/work/projects/craft-shared-task-2019/CRAFT.bill.git/coreference-annotation/knowtator/15314659.txt.knowtator.xml"),
				docTextFile, encoding);

		String docText = StreamUtil
				.toString(new InputStreamReader(new FileInputStream(docTextFile), encoding.getDecoder()));

		String docTitle = (docText.substring(0, 111));

		List<TextAnnotation> taList = new ArrayList<TextAnnotation>();
		for (TextAnnotation ta : td.getAnnotations()) {
			if (ta.getAggregateSpan().getSpanEnd() < 111) {
				taList.add(ta);
				if (ta.getClassMention().getComplexSlotMentionNames()
						.contains(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT)) {
					ComplexSlotMention csm = ta.getClassMention().getComplexSlotMentionByName(
							CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
					List<ClassMention> cmToRemove = new ArrayList<ClassMention>();
					for (ClassMention cm : csm.getClassMentions()) {
						if (cm.getTextAnnotation().getAggregateSpan().getSpanEnd() > 111) {
							cmToRemove.add(cm);
						}
					}
					for (ClassMention cm : cmToRemove) {
						csm.getClassMentions().remove(cm);
					}
				}
			}
		}

		// for (TextAnnotation ta : taList) {
		// System.out.println(ta);
		// }

	}

	/**
	 * Test that annotations with discontinuous spans are properly represented in ident chains
	 * 
	 * @throws IOException
	 */
	@Test
	public void testDocumentWriter_discontinuousSpans() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		InputStream conllStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(),
				"sample.discontinuous.conll");
		InputStream documentTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(),
				"sample.discontinuous.txt");

		TextDocument td = new CoNLLCoref2012DocumentReader().readDocument("11532192", "PMID", conllStream,
				documentTextStream, encoding);

		assertEquals("Expected 178 annotations -- 148 tokens + 5 sentences + 17 noun phrase + 8 identity chain", 178,
				td.getAnnotations().size());

		int npCount = 0;
		int identCount = 0;
		int discontinuousCount = 0;

		for (TextAnnotation ta : td.getAnnotations()) {
			String type = ta.getClassMention().getMentionName();
			if (type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.NOUN_PHRASE)) {
				npCount++;
			} else if (type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)) {
				identCount++;
			}
			if (ta.getSpans().size() > 1) {
				discontinuousCount++;
			}
		}

		assertEquals("Expect 17 noun phrase annotations", 17, npCount);
		assertEquals("Expect 8 IDENTITY chain annotations", 8, identCount);
		assertEquals("Expect 9 discontinuous annotations: 6 noun phrase + 3 ident chain", 9, discontinuousCount);

		// File outputFile = folder.newFile("sample.conll");
		File outputFile = new File("/tmp/sample.conll");
		new CoNLLCoref2012DocumentWriter().serialize(td, outputFile, encoding);

		/*
		 * now load the output file and check that the chains have the correct number of members
		 */
		documentTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample.discontinuous.txt");

		td = new CoNLLCoref2012DocumentReader().readDocument("11532192", "PMID", new FileInputStream(outputFile),
				documentTextStream, encoding);

		assertEquals("Expected 178 annotations -- 148 tokens + 5 sentences + 17 noun phrase + 8 identity chain", 178,
				td.getAnnotations().size());

		npCount = 0;
		identCount = 0;
		discontinuousCount = 0;

		for (TextAnnotation ta : td.getAnnotations()) {
			String type = ta.getClassMention().getMentionName();
			if (type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.NOUN_PHRASE)) {
				npCount++;
			} else if (type.equalsIgnoreCase(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)) {
				identCount++;
			}
			if (ta.getSpans().size() > 1) {
				discontinuousCount++;
			}
		}

		assertEquals("Expect 17 noun phrase annotations", 17, npCount);
		assertEquals("Expect 8 IDENTITY chain annotations", 8, identCount);
		assertEquals("Expect 9 discontinuous annotations: 6 noun phrase + 3 ident chain", 9, discontinuousCount);

		CoNLLCoref2012DocumentReaderTest.testForExpectedAnnotations(td);

	}

	@Test
	public void testGetDiscontinuousMentionId() {
		assertEquals("a", CoNLLCoref2012DocumentWriter.getDiscontinuousMentionId(0));
		assertEquals("b", CoNLLCoref2012DocumentWriter.getDiscontinuousMentionId(1));
		assertEquals("c", CoNLLCoref2012DocumentWriter.getDiscontinuousMentionId(2));
		assertEquals("z", CoNLLCoref2012DocumentWriter.getDiscontinuousMentionId(25));
		assertEquals("aa", CoNLLCoref2012DocumentWriter.getDiscontinuousMentionId(26));
		assertEquals("bb", CoNLLCoref2012DocumentWriter.getDiscontinuousMentionId(27));
		assertEquals("cc", CoNLLCoref2012DocumentWriter.getDiscontinuousMentionId(28));
		assertEquals("aaa", CoNLLCoref2012DocumentWriter.getDiscontinuousMentionId(52));
		assertEquals("bbb", CoNLLCoref2012DocumentWriter.getDiscontinuousMentionId(53));
		assertEquals("ccc", CoNLLCoref2012DocumentWriter.getDiscontinuousMentionId(54));
	}

}
