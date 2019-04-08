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

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.file.conversion.DocumentWriter;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.knowtator2.Annotation;
import edu.ucdenver.ccp.knowtator2.Document;
import edu.ucdenver.ccp.knowtator2.GraphSpace;
import edu.ucdenver.ccp.knowtator2.KnowtatorProject;
import edu.ucdenver.ccp.knowtator2.Triple;
import edu.ucdenver.ccp.knowtator2.Vertex;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.mention.ClassMention;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;

public class Knowtator2DocumentWriter extends DocumentWriter {

	@Override
	public void serialize(TextDocument td, OutputStream outputStream, CharacterEncoding encoding) throws IOException {

		KnowtatorProject project = new KnowtatorProject();
		String documentId = td.getSourceid();
		Document d = new Document();
		d.setId(documentId);
		d.setTextFile(documentId + ".txt");

		project.setDocument(d);

		Map<TextAnnotation, String> annotToIdMap = new HashMap<TextAnnotation, String>();

		int annotationCount = 0;
		int spanCount = 0;
		boolean hasRelations = false;
		List<TextAnnotation> sortedAnnotationList = td.getAnnotations();
		Collections.sort(sortedAnnotationList, TextAnnotation.BY_SPAN());
		for (TextAnnotation annot : sortedAnnotationList) {

			Collection<ComplexSlotMention> csms = annot.getClassMention().getComplexSlotMentions();
			if (csms != null && !csms.isEmpty()) {
				hasRelations = true;
			}

			String annotationId = annot.getAnnotationID();
			/*
			 * use the predefined annotation id if there is one, otherwise
			 * create a unique identifier
			 */
			annotationId = (annotationId != null && !annotationId.trim().isEmpty()) ? annotationId
					: Integer.toString(annotationCount++);

			annotToIdMap.put(annot, annotationId);

			Annotation annotation = new Annotation();
			annotation.setAnnotator(annot.getAnnotator().getName());
			annotation.setId(annotationId);
			annotation.setType("identity");

			edu.ucdenver.ccp.knowtator2.Class clazz = new edu.ucdenver.ccp.knowtator2.Class();
			clazz.setId(annot.getClassMention().getMentionName());
			clazz.setLabel(annot.getClassMention().getMentionName());
			annotation.setClazz(clazz);

			for (Span span : annot.getSpans()) {
				edu.ucdenver.ccp.knowtator2.Span knowtatorSpan = new edu.ucdenver.ccp.knowtator2.Span();
				knowtatorSpan.setContent(td.getText().substring(span.getSpanStart(), span.getSpanEnd()));
				knowtatorSpan.setEnd(new BigInteger(Integer.toString(span.getSpanEnd())));
				knowtatorSpan.setStart(new BigInteger(Integer.toString(span.getSpanStart())));
				knowtatorSpan.setId(documentId + "-" + spanCount++);

				annotation.getSpan().add(knowtatorSpan);
			}

			d.getAnnotation().add(annotation);
		}

		GraphSpace gs = new GraphSpace();
		gs.setId("Old Knowtator Relations");

		if (hasRelations) {
			/* Add Vertices */
			Map<String, String> annotIdToVertexIdMap = new HashMap<String, String>();
			int nodeCount = 0;

			for (TextAnnotation annot : sortedAnnotationList) {
				String annotId = annotToIdMap.get(annot);
				String vertexId = "node_" + nodeCount++;
				annotIdToVertexIdMap.put(annotId, vertexId);
				Vertex v = new Vertex();
				v.setAnnotation(annotId);
				v.setId(vertexId);
				gs.getVertex().add(v);
			}

			/* Add Triples */
			int tripleCount = 0;
			for (TextAnnotation annot : sortedAnnotationList) {
				String sourceAnnotId = annotToIdMap.get(annot);

				Collection<ComplexSlotMention> csms = annot.getClassMention().getComplexSlotMentions();
				if (csms != null && !csms.isEmpty()) {
					for (ComplexSlotMention csm : csms) {
						String relationType = csm.getMentionName();
						for (ClassMention cm : csm.getClassMentions()) {
							TextAnnotation targetAnnot = cm.getTextAnnotation();
							String targetAnnotId = annotToIdMap.get(targetAnnot);
							Triple t = new Triple();
							t.setAnnotator(annot.getAnnotator().getName());
							t.setId("edge_" + tripleCount++);
							t.setSubject(annotIdToVertexIdMap.get(sourceAnnotId));
							t.setProperty(relationType);
							t.setObject(annotIdToVertexIdMap.get(targetAnnotId));
							t.setQuantifier("");
							t.setValue("");
							gs.getTriple().add(t);
						}
					}
				}
			}
		}

		// System.out.println("TRIPLE COUNT: " + gs.getTriple().size());

		d.setGraphSpace(gs);

		/* write the Document as XML */
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(KnowtatorProject.class);
			Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
			jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			jaxbMarshaller.marshal(project, outputStream);
		} catch (JAXBException e) {
			throw new IOException(e);
		}

	}

}
