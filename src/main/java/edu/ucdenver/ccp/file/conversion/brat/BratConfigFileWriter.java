package edu.ucdenver.ccp.file.conversion.brat;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.collections.CollectionsUtil.SortOrder;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileUtil;
import edu.ucdenver.ccp.common.file.FileWriterUtil;
import edu.ucdenver.ccp.common.string.StringUtil;
import edu.ucdenver.ccp.datasource.fileparsers.obo.OntologyUtil;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.file.conversion.bionlp.BioNLPDocumentWriter;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;

/**
 * This class creates the annotation.conf and visual.conf files that accompany
 * the BRAT .ann and .txt files to inform the BRAT tool how to visualize the
 * annotations.
 *
 */
public class BratConfigFileWriter {

	private static final String OBO_PURL = "http://purl.obolibrary.org/obo/";

	private static final String DEFAULT_SPACE_PLACEHOLDER = BioNLPDocumentWriter.SPACE_PLACEHOLDER;

	public static void createConfFiles(File annotationFileDirectory, List<File> ontologyFiles,
			CharacterEncoding encoding, Map<String, String> conceptTypeToColorMap)
			throws IOException, OWLOntologyCreationException {
		createConfFiles(annotationFileDirectory, ontologyFiles, encoding, conceptTypeToColorMap,
				DEFAULT_SPACE_PLACEHOLDER);
	}

	public static void createConfFiles(File annotationFileDirectory, List<File> ontologyFiles,
			CharacterEncoding encoding, Map<String, String> conceptTypeToColorMap, String spacePlaceholder)
			throws IOException, OWLOntologyCreationException {
		File annotationConfFile = new File(annotationFileDirectory, "annotation.conf");
		File visualConfFile = new File(annotationFileDirectory, "visual.conf");

		Set<String> annotTypes = new HashSet<String>();
		Set<String> relationTypes = new HashSet<String>();

		for (Iterator<File> fileIterator = FileUtil.getFileIterator(annotationFileDirectory, false,
				".ann"); fileIterator.hasNext();) {
			File inputFile = fileIterator.next();
			File documentTextFile = new File(inputFile.getAbsolutePath().replace(".ann", ".txt"));
			TextDocument td = new BratDocumentReader().readDocument("sourceid", "sourcedb", inputFile, documentTextFile,
					encoding);
			for (TextAnnotation annot : td.getAnnotations()) {

				/*
				 * this code is complicated b/c CRAFT doesn't use fully
				 * qualifies URLs for the concept annotation types and instead
				 * just uses the identifier without the namespace, e.g.
				 * CL_0000000. The code below tries to predict if the annotation
				 * type is an ontology concept or not and treats the two cases
				 * separately.
				 */
				String annotType = annot.getClassMention().getMentionName();
				if (annotType.matches("\\w+:\\d+")) {
					annotType = OBO_PURL + annotType.replace(":", "_");
					annotTypes.add(annotType);
				} else {
					/*
					 * replace spaces in the annotation types with the
					 * spacePlaceholder b/c the brat format does not handle
					 * spaces in annotation and relation types
					 */
					annotType = annotType.replaceAll(" ", spacePlaceholder);
					annotTypes.add(annotType);
				}
				Collection<ComplexSlotMention> csms = annot.getClassMention().getComplexSlotMentions();
				if (csms != null && !csms.isEmpty()) {
					for (ComplexSlotMention csm : csms) {
						String relationType = csm.getMentionName();
						/*
						 * replace spaces in the annotation types with the
						 * spacePlaceholder b/c the brat format does not handle
						 * spaces in annotation and relation types
						 */
						relationType = relationType.replaceAll(" ", spacePlaceholder);
						relationTypes.add(relationType);
					}
				}
			}
		}

		Map<String, String> typeToLabelMap = new HashMap<String, String>();
		for (File ontFile : ontologyFiles) {
			OntologyUtil ontUtil = null;
			if (ontFile.getName().endsWith(".zip")) {
				ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(ontFile));
				zipInputStream.getNextEntry();
				ontUtil = new OntologyUtil(zipInputStream);
			} else if (ontFile.getName().endsWith(".gz")) {
				ontUtil = new OntologyUtil(new GZIPInputStream(new FileInputStream(ontFile)));
			} else {
				ontUtil = new OntologyUtil(ontFile);
			}
			for (Iterator<OWLClass> classIterator = ontUtil.getClassIterator(); classIterator.hasNext();) {
				OWLClass cls = classIterator.next();
				String id = cls.getIRI().toString();
				String label = ontUtil.getLabel(cls);
				if (annotTypes.contains(id)) {
					typeToLabelMap.put(id, label);
				}
			}
		}

		/*
		 * add non-ontology types to the typeToLabelMap with their label simply
		 * being the same as the type
		 */
		for (String annotType : annotTypes) {
			if (!annotType.startsWith(OBO_PURL)) {
				typeToLabelMap.put(annotType, annotType);
			}
		}

		createAnnotationConfFile(annotationConfFile, annotTypes, relationTypes, encoding);
		createVisualConfFile(visualConfFile, typeToLabelMap, conceptTypeToColorMap, encoding);

	}

	private static void createVisualConfFile(File visualConfFile, Map<String, String> typeToLabelMap,
			Map<String, String> conceptTypeToColorMap, CharacterEncoding encoding) throws IOException {
		StringBuffer sb = new StringBuffer();
		sb.append("[drawing]\n");
		Map<String, String> sortedMap = CollectionsUtil.sortMapByKeys(typeToLabelMap, SortOrder.ASCENDING);
		for (Entry<String, String> entry : sortedMap.entrySet()) {
			String id = entry.getKey();
			String color = "bgColor:#47e542";
			if (id.startsWith(OBO_PURL)) {
				id = StringUtil.removePrefix(entry.getKey(), OBO_PURL).replace("_", ":");
				String idPrefix = id.substring(0, id.indexOf(":"));
				if (conceptTypeToColorMap.containsKey(idPrefix)) {
					color = conceptTypeToColorMap.get(idPrefix);
				}
			}
			sb.append(id + "\t" + color + "\n");
		}

		sb.append("[labels]\n");
		for (Entry<String, String> entry : sortedMap.entrySet()) {
			String id = entry.getKey();
			if (id.startsWith(OBO_PURL)) {
				id = StringUtil.removePrefix(entry.getKey(), OBO_PURL).replace("_", ":");
			}
			sb.append(id + " | " + entry.getValue() + "\n");
		}

		FileWriterUtil.printLines(CollectionsUtil.createList(sb.toString()), visualConfFile, encoding);
	}

	private static void createAnnotationConfFile(File annotationConfFile, Set<String> annotTypes,
			Set<String> relationTypes, CharacterEncoding encoding) throws IOException {
		StringBuffer sb = new StringBuffer();
		sb.append("[entities]\n");
		List<String> sortedAnnotTypes = new ArrayList<String>(annotTypes);
		Collections.sort(sortedAnnotTypes);
		for (String type : sortedAnnotTypes) {
			if (type.startsWith(OBO_PURL)) {
				type = StringUtil.removePrefix(type, OBO_PURL).replace("_", ":");
			}
			sb.append(type + "\n");
		}
		sb.append("[attributes]\n");
		sb.append("[relations]\n");

		List<String> sortedRelationTypes = new ArrayList<String>(relationTypes);
		Collections.sort(sortedRelationTypes);
		for (String type : sortedRelationTypes) {
			sb.append(type + "\n");
			sb.append("[events]\n");
		}

		FileWriterUtil.printLines(CollectionsUtil.createList(sb.toString()), annotationConfFile, encoding);

	}

}
