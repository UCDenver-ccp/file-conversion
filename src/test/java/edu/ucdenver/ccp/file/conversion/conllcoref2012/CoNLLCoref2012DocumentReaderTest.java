package edu.ucdenver.ccp.file.conversion.conllcoref2012;

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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.io.ClassPathUtil;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.SpanUtils;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultComplexSlotMention;

public class CoNLLCoref2012DocumentReaderTest {

	@Test
	public void testDocumentReader() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		InputStream conllStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.ident.conll");
		InputStream documentTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.txt");

		TextDocument td = new CoNLLCoref2012DocumentReader().readDocument("11532192", "PMID", conllStream,
				documentTextStream, encoding);

		assertEquals("Expected 132 annotations, 101 tokens + 7 sentences + 19 noun phrase + 5 identity chain", 132,
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

	@Test
	public void testDocumentReader_discontinuousSpans() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		InputStream conllStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(),
				"sample.discontinuous.conll");
		InputStream documentTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(),
				"sample.discontinuous.txt");

		String documentId = "11532192";

		TextDocument td = new CoNLLCoref2012DocumentReader().readDocument(documentId, "PMID", conllStream,
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

		testForExpectedAnnotations(td);

	}

	public static void testForExpectedAnnotations(TextDocument td) {
		List<TextAnnotation> expectedAnnots = getExpectedAnnotations(td);

		HashSet<TextAnnotation> annots = new HashSet<TextAnnotation>();
		for (TextAnnotation ta : td.getAnnotations()) {
			if (ta.getClassMention().getMentionName().equals(CoNLLCoref2012DocumentReader.NOUN_PHRASE)) {
				annots.add(ta);
			}
		}

		for (TextAnnotation expectedAnnot : expectedAnnots) {
			if (expectedAnnot.getClassMention().getMentionName().equals(CoNLLCoref2012DocumentReader.NOUN_PHRASE)) {
				assertTrue("Expected annotation should be removed: " + expectedAnnot.toString(),
						annots.remove(expectedAnnot));
			}
		}
		assertTrue("The annots set should now be empty", annots.isEmpty());

		annots = new HashSet<TextAnnotation>();
		for (TextAnnotation ta : td.getAnnotations()) {
			if (ta.getClassMention().getMentionName().equals(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)) {
				annots.add(ta);
			}
		}

		for (TextAnnotation expectedAnnot : expectedAnnots) {
			if (expectedAnnot.getClassMention().getMentionName().equals(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)) {
				assertTrue("Expected annotation should be removed: " + expectedAnnot.toString() + "\n---\n"
						+ annots.toString(), annots.remove(expectedAnnot));
			}
		}
		assertTrue("The annots set should now be empty", annots.isEmpty());
	}

	private static List<TextAnnotation> getExpectedAnnotations(TextDocument td) {
		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults(td.getSourceid());

		TextAnnotation expectedNpAnnot2 = factory.createAnnotation(52, 59, "BUB/BnJ",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		assertEquals(expectedNpAnnot2.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot2.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot3 = factory.createAnnotation(126, 134, "the IOPs",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		assertEquals(expectedNpAnnot3.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot3.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot9 = factory.createAnnotation(163, 166, "IOP",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		assertEquals(expectedNpAnnot9.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot9.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot4 = factory.createAnnotation(445, 458, "strains SB/Le",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		assertEquals(expectedNpAnnot4.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot4.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot5 = factory.createAnnotation(445, 452, "strains .. BUB/BnJ",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		expectedNpAnnot5.addSpan(new Span(467, 474));
		assertEquals(expectedNpAnnot5.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot5.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot6 = factory.createAnnotation(445, 452, "strains .. C3H/HeJ",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		expectedNpAnnot6.addSpan(new Span(483, 490));
		assertEquals(expectedNpAnnot6.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot6.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot7 = factory.createAnnotation(445, 452, "strains",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		assertEquals(expectedNpAnnot7.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot7.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot8 = factory.createAnnotation(445, 452, "strains .. SWR/J",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		expectedNpAnnot8.addSpan(new Span(502, 507));
		assertEquals(expectedNpAnnot8.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot8.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot10 = factory.createAnnotation(194, 210, "strains C57BL/6J",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		assertEquals(expectedNpAnnot10.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot10.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot11 = factory.createAnnotation(194, 201, "strains .. 129P3/J",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		expectedNpAnnot11.addSpan(new Span(215, 222));
		assertEquals(expectedNpAnnot11.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot11.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot12 = factory.createAnnotation(194, 201, "strains .. C3H/HeJ",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		expectedNpAnnot12.addSpan(new Span(231, 238));
		assertEquals(expectedNpAnnot12.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot12.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot13 = factory.createAnnotation(244, 252, "C57BL/6J",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		assertEquals(expectedNpAnnot13.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot13.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot14 = factory.createAnnotation(194, 201, "strains",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		assertEquals(expectedNpAnnot14.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot14.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot15 = factory.createAnnotation(257, 264, "129P3/J",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		assertEquals(expectedNpAnnot15.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot15.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot16 = factory.createAnnotation(637, 650, "strains SB/Le",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		assertEquals(expectedNpAnnot16.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot16.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot17 = factory.createAnnotation(637, 644, "strains",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		assertEquals(expectedNpAnnot17.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot17.getSpans(), td.getText()));

		TextAnnotation expectedNpAnnot18 = factory.createAnnotation(637, 644, "strains .. SWR/J",
				new DefaultClassMention(CoNLLCoref2012DocumentReader.NOUN_PHRASE));
		expectedNpAnnot18.addSpan(new Span(655, 660));
		assertEquals(expectedNpAnnot18.getCoveredText(),
				SpanUtils.getCoveredText(expectedNpAnnot18.getSpans(), td.getText()));

		/* IDENT CHAINS */
		// 56
		TextAnnotation identAnnot1 = makeIdentChain(factory, td.getText(), expectedNpAnnot2, expectedNpAnnot5);
		// 12
		TextAnnotation identAnnot2 = makeIdentChain(factory, td.getText(), expectedNpAnnot3, expectedNpAnnot9);
		// 24
		TextAnnotation identAnnot3 = makeIdentChain(factory, td.getText(), expectedNpAnnot10, expectedNpAnnot13);
		// 74
		TextAnnotation identAnnot4 = makeIdentChain(factory, td.getText(), expectedNpAnnot12, expectedNpAnnot6);
		// 77
		TextAnnotation identAnnot5 = makeIdentChain(factory, td.getText(), expectedNpAnnot11, expectedNpAnnot15);
		// 2
		TextAnnotation identAnnot6 = makeIdentChain(factory, td.getText(), expectedNpAnnot14, expectedNpAnnot7,
				expectedNpAnnot17);
		// 53
		TextAnnotation identAnnot7 = makeIdentChain(factory, td.getText(), expectedNpAnnot4, expectedNpAnnot16);
		// 74
		TextAnnotation identAnnot8 = makeIdentChain(factory, td.getText(), expectedNpAnnot8, expectedNpAnnot18);

		List<TextAnnotation> annots = new ArrayList<TextAnnotation>();
		annots.add(expectedNpAnnot2);
		annots.add(expectedNpAnnot3);
		annots.add(expectedNpAnnot4);
		annots.add(expectedNpAnnot5);
		annots.add(expectedNpAnnot6);
		annots.add(expectedNpAnnot7);
		annots.add(expectedNpAnnot8);
		annots.add(expectedNpAnnot9);
		annots.add(expectedNpAnnot10);
		annots.add(expectedNpAnnot11);
		annots.add(expectedNpAnnot12);
		annots.add(expectedNpAnnot13);
		annots.add(expectedNpAnnot14);
		annots.add(expectedNpAnnot15);
		annots.add(expectedNpAnnot16);
		annots.add(expectedNpAnnot17);
		annots.add(expectedNpAnnot18);

		annots.add(identAnnot1);
		annots.add(identAnnot2);
		annots.add(identAnnot3);
		annots.add(identAnnot4);
		annots.add(identAnnot5);
		annots.add(identAnnot6);
		annots.add(identAnnot7);
		annots.add(identAnnot8);

		return annots;

	}

	private static TextAnnotation makeIdentChain(TextAnnotationFactory factory, String documentText,
			TextAnnotation... npAnnots) {
		TextAnnotation identAnnot = factory.createAnnotation(npAnnots[0].getSpans(), documentText,
				new DefaultClassMention(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN));
		ComplexSlotMention csm = new DefaultComplexSlotMention(
				CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
		for (int i = 0; i < npAnnots.length; i++) {
			csm.addClassMention(npAnnots[i].getClassMention());
		}
		identAnnot.getClassMention().addComplexSlotMention(csm);
		return identAnnot;
	}

	@Test
	public void testDocumentReader_checkCommasDontIncludeSpace() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		InputStream conllStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(),
				"sample.discontinuous.conll");
		InputStream documentTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(),
				"sample.discontinuous.txt");

		String documentId = "11532192";

		TextDocument td = new CoNLLCoref2012DocumentReader().readDocument(documentId, "PMID", conllStream,
				documentTextStream, encoding);

		for (TextAnnotation ta : td.getAnnotations()) {
			String coveredText = SpanUtils.getCoveredText(ta.getSpans(), td.getText());
			if (coveredText.equals(", ")) {
				fail("should be the comma only, not a space afterwards");
			}
		}

	}

}
