package edu.ucdenver.ccp.file.conversion.knowtator;

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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.EventFilter;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.io.StreamUtil;
import edu.ucdenver.ccp.file.conversion.DocumentReader;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.file.conversion.util.DocumentReaderUtil;
import edu.ucdenver.ccp.knowtator.Annotation;
import edu.ucdenver.ccp.knowtator.Annotations;
import edu.ucdenver.ccp.knowtator.Annotator;
import edu.ucdenver.ccp.knowtator.ClassMention;
import edu.ucdenver.ccp.knowtator.ComplexSlotMention;
import edu.ucdenver.ccp.knowtator.ComplexSlotMentionValue;
import edu.ucdenver.ccp.knowtator.HasSlotMention;
import edu.ucdenver.ccp.knowtator.Mention;
import edu.ucdenver.ccp.knowtator.MentionClass;
import edu.ucdenver.ccp.knowtator.MentionSlot;
import edu.ucdenver.ccp.knowtator.Span;
import edu.ucdenver.ccp.knowtator.StringSlotMention;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.PrimitiveSlotMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultComplexSlotMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultPrimitiveSlotMentionFactory;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultStringSlotMention;

public class KnowtatorDocumentReader extends DocumentReader {

	@Override
	public TextDocument readDocument(String sourceId, String sourceDb, InputStream inputStream,
			InputStream documentTextStream, CharacterEncoding encoding) throws IOException {
		String documentText = StreamUtil.toString(new InputStreamReader(documentTextStream, encoding.getDecoder()));
		TextDocument td = new TextDocument(sourceId, sourceDb, documentText);
		try {
			List<TextAnnotation> annotations = getAnnotations(inputStream);
			DocumentReaderUtil.validateSpans(annotations, documentText, sourceId);
			td.addAnnotations(annotations);
		} catch (XMLStreamException | JAXBException e) {
			throw new IOException("Error while reading Knowtator2 file.", e);
		}
		return td;
	}

	public static List<TextAnnotation> getAnnotations(InputStream knowtatorStream)
			throws XMLStreamException, JAXBException, FileNotFoundException {

		Class<?> entryClass = Annotations.class;
		JAXBContext ctx = JAXBContext.newInstance(entryClass);
		Unmarshaller um = ctx.createUnmarshaller();
		XMLInputFactory xmlif = XMLInputFactory.newInstance();
		XMLEventReader xmler = xmlif.createXMLEventReader(knowtatorStream);
		EventFilter filter = new EventFilter() {
			public boolean accept(XMLEvent event) {
				return event.isStartElement();
			}
		};
		XMLEventReader xmlfer = xmlif.createFilteredReader(xmler, filter);
		// StartElement e = (StartElement) xmlfer.nextEvent();

		List<TextAnnotation> annotationsToReturn = new ArrayList<TextAnnotation>();

		Map<String, Set<String>> csmIdToValueMap = new HashMap<String, Set<String>>();
		Map<String, Set<String>> cmIdToValueMap = new HashMap<String, Set<String>>();

		Map<String, DefaultClassMention> cmIdToCmMap = new HashMap<String, DefaultClassMention>();
		Map<String, DefaultComplexSlotMention> csmIdToCsmMap = new HashMap<String, DefaultComplexSlotMention>();
		Map<String, DefaultStringSlotMention> ssmIdToSsmMap = new HashMap<String, DefaultStringSlotMention>();

		while (xmlfer.peek() != null) {
			JAXBElement<?> unmarshalledElement = um.unmarshal(xmler, entryClass);
			Object o = unmarshalledElement.getValue();

			if (Annotations.class.isInstance(o)) {
				Annotations a = (Annotations) o;

				for (Object mention : a.getClassMentionOrComplexSlotMentionOrStringSlotMention()) {
					if (ComplexSlotMention.class.isInstance(mention)) {
						ComplexSlotMention csm = (ComplexSlotMention) mention;
						List<ComplexSlotMentionValue> complexSlotMentionValues = csm.getComplexSlotMentionValue();
						String id = csm.getId();
						MentionSlot mentionSlot = csm.getMentionSlot();
						String id2 = mentionSlot.getId();

						csmIdToCsmMap.put(id, new DefaultComplexSlotMention(id2));
						for (ComplexSlotMentionValue csmv : complexSlotMentionValues) {
							String value = csmv.getValue();
							CollectionsUtil.addToOne2ManyUniqueMap(id, value, csmIdToValueMap);
						}
					} else if (StringSlotMention.class.isInstance(mention)) {
						StringSlotMention ssm = (StringSlotMention) mention;
						String id = ssm.getId();
						String slotName = ssm.getMentionSlot().getId();
						String slotValue = ssm.getStringSlotMentionValue().getValue();
						DefaultStringSlotMention dssm = new DefaultStringSlotMention(slotName);
						dssm.addSlotValue(slotValue);
						ssmIdToSsmMap.put(id, dssm);
					} else if (ClassMention.class.isInstance(mention)) {
						ClassMention cm = (ClassMention) mention;
						String id = cm.getId();
						MentionClass mentionClass = cm.getMentionClass();
						List<HasSlotMention> hasSlotMention = cm.getHasSlotMention();
						String content = mentionClass.getContent();
						String id2 = mentionClass.getId();

						DefaultClassMention dcm = new DefaultClassMention(id2);
						cmIdToCmMap.put(id, dcm);
						for (HasSlotMention hsm : hasSlotMention) {
							String id3 = hsm.getId();
							CollectionsUtil.addToOne2ManyUniqueMap(id, id3, cmIdToValueMap);
						}
					} else {
						throw new IllegalArgumentException(
								"Unhandled slot mention type: " + mention.getClass().getName());
					}
				}

				/* add CMs to CSMs as slot values */
				for (Entry<String, Set<String>> entry : csmIdToValueMap.entrySet()) {
					DefaultComplexSlotMention dcsm = csmIdToCsmMap.get(entry.getKey());
					for (String cmId : entry.getValue()) {
						DefaultClassMention dcm = cmIdToCmMap.get(cmId);
						dcsm.addClassMention(dcm);
					}
				}

				/* add CSMs and SSMs as slots to CMs */
				for (Entry<String, Set<String>> entry : cmIdToValueMap.entrySet()) {
					DefaultClassMention dcm = cmIdToCmMap.get(entry.getKey());
					for (String csmOrSsmId : entry.getValue()) {
						if (csmIdToCsmMap.containsKey(csmOrSsmId)) {
						DefaultComplexSlotMention dcsm = csmIdToCsmMap.get(csmOrSsmId);
						dcm.addComplexSlotMention(dcsm);
						} else {
							DefaultStringSlotMention dssm = ssmIdToSsmMap.get(csmOrSsmId);
							dcm.addPrimitiveSlotMention(dssm);
						}
					}
				}

				/* now create annotations and link to appropriate CM */
				TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults(a.getTextSource());
				for (Annotation annot : a.getAnnotation()) {
					Annotator annotator = annot.getAnnotator();
					String annotatorId = "";
					String annotatorContent = "Unknown Annotator";
					if (annotator != null) {
						annotatorId = annotator.getId();
						annotatorContent = annotator.getContent();
					}
					String creationDate = annot.getCreationDate();
					Mention mention = annot.getMention();
					String mentionId = mention.getId();
					List<Span> spans = annot.getSpan();
					String spannedText = annot.getSpannedText();

					if (!spans.isEmpty()) {
						TextAnnotation ta = null;
						for (Span span : spans) {
							if (ta == null) {
								ta = factory.createAnnotation(span.getStart().intValue(), span.getEnd().intValue(),
										spannedText, cmIdToCmMap.get(mentionId));
								ta.setAnnotationID(mentionId);
							} else {
								ta.addSpan(new edu.ucdenver.ccp.nlp.core.annotation.Span(span.getStart().intValue(),
										span.getEnd().intValue()));
							}
						}
						ta.setAnnotator(new edu.ucdenver.ccp.nlp.core.annotation.Annotator(annotatorId,
								annotatorContent, null));
						annotationsToReturn.add(ta);
					}
				}
			}
		}

		return annotationsToReturn;
	}

}
