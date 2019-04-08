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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.EventFilter;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.io.StreamUtil;
import edu.ucdenver.ccp.file.conversion.DocumentReader;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.knowtator2.Annotation;
import edu.ucdenver.ccp.knowtator2.Document;
import edu.ucdenver.ccp.knowtator2.GraphSpace;
import edu.ucdenver.ccp.knowtator2.KnowtatorProject;
import edu.ucdenver.ccp.knowtator2.Span;
import edu.ucdenver.ccp.knowtator2.Triple;
import edu.ucdenver.ccp.knowtator2.Vertex;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;

public class Knowtator2DocumentReader extends DocumentReader {

	@Override
	public TextDocument readDocument(String sourceId, String sourceDb, InputStream inputStream,
			InputStream documentTextStream, CharacterEncoding encoding) throws IOException {
		String documentText = StreamUtil.toString(new InputStreamReader(documentTextStream, encoding.getDecoder()));
		TextDocument td = new TextDocument(sourceId, sourceDb, documentText);
		try {
			td.addAnnotations(getAnnotations(inputStream));
		} catch (XMLStreamException | JAXBException e) {
			throw new IOException("Error while reading Knowtator2 file.", e);
		}
		return td;
	}

	public static List<TextAnnotation> getAnnotations(InputStream knowtator2Stream)
			throws XMLStreamException, JAXBException, FileNotFoundException {

		Class<?> entryClass = KnowtatorProject.class;
		JAXBContext ctx = JAXBContext.newInstance(entryClass);
		Unmarshaller um = ctx.createUnmarshaller();
		XMLInputFactory xmlif = XMLInputFactory.newInstance();
		XMLEventReader xmler = xmlif.createXMLEventReader(knowtator2Stream);
		EventFilter filter = new EventFilter() {
			public boolean accept(XMLEvent event) {
				return event.isStartElement();
			}
		};
		XMLEventReader xmlfer = xmlif.createFilteredReader(xmler, filter);
		// StartElement e = (StartElement) xmlfer.nextEvent();

		List<TextAnnotation> annotationsToReturn = new ArrayList<TextAnnotation>();

		while (xmlfer.peek() != null) {
			JAXBElement<?> unmarshalledElement = um.unmarshal(xmler, entryClass);
			Object o = unmarshalledElement.getValue();

			if (KnowtatorProject.class.isInstance(o)) {
				KnowtatorProject project = (KnowtatorProject) o;
				Document d = project.getDocument();
				String documentId = d.getId();
				TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults(documentId.toString());
				Map<String, TextAnnotation> idToAnnotMap = new HashMap<String, TextAnnotation>();

				List<Annotation> annotations = d.getAnnotation();
				for (Annotation annot : annotations) {
					edu.ucdenver.ccp.knowtator2.Class clazz = annot.getClazz();
					String annotType = clazz.getId();
					@SuppressWarnings("unused")
					String annotTypeLabel = clazz.getLabel();
					String id = annot.getId();
					List<Span> spans = annot.getSpan();

					String coveredText = spans.get(0).getContent();
					for (int i = 1; i < spans.size(); i++) {
						coveredText += (" " + spans.get(i).getContent());
					}
					TextAnnotation ta = factory.createAnnotation(spans.get(0).getStart().intValue(),
							spans.get(0).getEnd().intValue(), coveredText, new DefaultClassMention(annotType));
					for (int i = 1; i < spans.size(); i++) {
						ta.addSpan(new edu.ucdenver.ccp.nlp.core.annotation.Span(spans.get(i).getStart().intValue(),
								spans.get(i).getEnd().intValue()));
					}
					idToAnnotMap.put(id, ta);
				}

				Map<String, String> vertexToAnnotIdMap = new HashMap<String, String>();
				GraphSpace graphSpace = d.getGraphSpace();
				if (graphSpace != null) {
					for (Vertex vertex : graphSpace.getVertex()) {
						String vertexId = vertex.getId();
						String annotationId = vertex.getAnnotation();
						vertexToAnnotIdMap.put(vertexId, annotationId);
					}

					for (Triple triple : graphSpace.getTriple()) {
						String subjectVertexId = triple.getSubject();
						String property = triple.getProperty();
						String objectVertexId = triple.getObject();
						String value = triple.getValue();
						String quantifier = triple.getQuantifier();

						if (!quantifier.isEmpty() || !value.isEmpty()) {
							throw new IllegalStateException(
									"This parser not yet able to handle quantifiers and/or values. Further development needed.");
						}

						TextAnnotation subjTa = idToAnnotMap.get(vertexToAnnotIdMap.get(subjectVertexId));
						String relationType = property;
						TextAnnotation objTa = idToAnnotMap.get(vertexToAnnotIdMap.get(objectVertexId));

						DocumentReader.createAnnotationRelation(subjTa, objTa, relationType);
					}
					annotationsToReturn.addAll(idToAnnotMap.values());
				}
			}
		}

		return annotationsToReturn;
	}

}
